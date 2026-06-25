package dev.batch.bm_b002;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.batch.repository.master.TeamMemberMasterBatchRepository;
import dev.common.config.PathConfig;
import dev.common.entity.TeamMemberMasterEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;
import dev.common.util.FileDeleteUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamMemberMasterStat {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = TeamMemberMasterStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = TeamMemberMasterStat.class.getName();

	private static final ZoneId JST = ZoneId.of("Asia/Tokyo");
	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	/**
	 * 何回連続で未検出なら引退扱いにするか
	 */
	private static final int RETIRE_MISSING_THRESHOLD = 3;

	private final TeamMemberMasterBatchRepository repository;

	/** Config */
	@Autowired
	private PathConfig config;

	/** S3Operator */
	@Autowired
	private S3Operator s3Operator;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * メインエントリ
	 *
	 * @param scrapedRows  今回スクレイピングした選手データ一覧
	 * @param fullSnapshot true: 対象全件を取り切れている → 引退判定を行う
	 *                     false: 部分取得 → 引退判定しない
	 */
	@Transactional
	public void execute(List<TeamMemberMasterEntity> scrapedRows, boolean fullSnapshot) {
		final String METHOD_NAME = "execute";

		List<String> insertPath = new ArrayList<String>();

		String runDate = LocalDate.now(JST).format(DATE_FMT);

		// 既存データ全件取得
		List<TeamMemberMasterEntity> existingList = repository.selectAll();
		BmB002TeamMemberMasterBean bean = new BmB002TeamMemberMasterBean(existingList);

		// 今回 seen した既存 id の集合
		Set<Integer> seenIds = new HashSet<>();

		// 新規データの前処理（同一入力内の重複マージ）
		List<TeamMemberMasterEntity> incomingList = dedupIncoming(scrapedRows, runDate);

		for (TeamMemberMasterEntity incoming : incomingList) {
			// 削除対象ファイル設定
			insertPath.add(incoming.getFile());

			normalizeEntity(incoming, runDate);

			// ─── Step1: 同一チーム・同一人物の完全一致 ───────────────────
			TeamMemberMasterEntity exact = bean.findExactCurrent(incoming);
			if (exact != null) {
				TeamMemberMasterEntity updated = mergeSameTeam(copyOf(exact), incoming, runDate);
				repository.updateById(updated);
				bean.putWorking(updated);
				seenIds.add(updated.getId());
				continue;
			}

			// ─── Step2: 同一人物候補（team を無視して人物を特定） ─────────
			TeamMemberMasterEntity samePerson = bean.resolveSamePerson(incoming);
			if (samePerson != null) {
				TeamMemberMasterEntity updated;
				if (!eq(samePerson.getTeam(), incoming.getTeam())) {
					// 移籍
					updated = applyTransfer(copyOf(samePerson), incoming, runDate);
					log.info("[移籍] member={} / {} → {}",
							nvl(incoming.getMember()),
							nvl(samePerson.getTeam()),
							nvl(incoming.getTeam()));
				} else {
					// 同一人物・同一チームだが exact key に乗らなかった（jersey変更など）
					updated = mergeSameTeam(copyOf(samePerson), incoming, runDate);
				}
				repository.updateById(updated);
				bean.putWorking(updated);
				seenIds.add(updated.getId());
				continue;
			}

			// ─── Step3: 新規登録 ─────────────────────────────────────────
			TeamMemberMasterEntity newEntity = buildNewEntity(incoming, runDate);
			repository.insert(newEntity);
			bean.putWorking(newEntity);
			if (!isBlank(newEntity.getId())) {
				seenIds.add(newEntity.getId());
			}
			log.info("[新規] member={} / team={}", nvl(newEntity.getMember()), nvl(newEntity.getTeam()));
		}

		// ─── Step4: 引退 / missing 判定 ──────────────────────────────────
		if (fullSnapshot) {
			for (TeamMemberMasterEntity existing : bean.getAllWorking()) {
				if (isBlank(existing.getId())) {
					continue;
				}
				if (seenIds.contains(existing.getId())) {
					continue;
				}
				TeamMemberMasterEntity missingUpdated = markMissingOrRetired(copyOf(existing));
				repository.updateById(missingUpdated);
				bean.putWorking(missingUpdated);

				if ("1".equals(missingUpdated.getRetireFlg())) {
					log.info("[引退確定] member={} / team={} / missingCount={}",
							nvl(missingUpdated.getMember()),
							nvl(missingUpdated.getTeam()),
							nvl(missingUpdated.getMissingCount()));
				} else {
					log.info("[未検出] member={} / team={} / missingCount={}",
							nvl(missingUpdated.getMember()),
							nvl(missingUpdated.getTeam()),
							nvl(missingUpdated.getMissingCount()));
				}
			}
		} else {
			log.info("fullSnapshot=false のため引退判定はスキップします");
		}

		// 途中で例外が起きなければ全てのファイルを削除する
		String bucket = config.getS3BucketsTeamMemberData(); // バケット名取得
		FileDeleteUtil.deleteS3Files(
				insertPath,
				bucket,
				s3Operator,
				manageLoggerComponent,
				PROJECT_NAME,
				CLASS_NAME,
				METHOD_NAME,
				"TEAM_MEMBER");
	}

	// =========================================================================
	// 入力データの前処理
	// =========================================================================

	/**
	 * 新規入力の中で currentKey が重複する行を1件にまとめる
	 */
	private List<TeamMemberMasterEntity> dedupIncoming(List<TeamMemberMasterEntity> scrapedRows,
			String runDate) {
		if (scrapedRows == null || scrapedRows.isEmpty()) {
			return new ArrayList<>();
		}
		Map<String, TeamMemberMasterEntity> map = new LinkedHashMap<>();
		for (TeamMemberMasterEntity row : scrapedRows) {
			normalizeEntity(row, runDate);
			String key = BmB002TeamMemberMasterBean.currentKey(row);
			if (!map.containsKey(key)) {
				map.put(key, row);
			} else {
				map.put(key, mergeIncomingDuplicate(map.get(key), row, runDate));
			}
		}
		return new ArrayList<>(map.values());
	}

	/**
	 * 入力内の重複行同士のマージ（新しい値を優先）
	 */
	private TeamMemberMasterEntity mergeIncomingDuplicate(TeamMemberMasterEntity a,
			TeamMemberMasterEntity b,
			String runDate) {
		TeamMemberMasterEntity out = copyOf(a);

		out.setCountry(firstNonBlank(b.getCountry(), a.getCountry()));
		out.setLeague(firstNonBlank(b.getLeague(), a.getLeague()));
		out.setTeam(firstNonBlank(b.getTeam(), a.getTeam()));
		out.setScore(mergeScore(a.getScore(), b.getScore()));
		out.setLoanBelong(mergeCsvDistinct(a.getLoanBelong(), b.getLoanBelong()));
		out.setJersey(firstNonBlank(b.getJersey(), a.getJersey()));
		out.setMember(firstNonBlank(b.getMember(), a.getMember()));
		out.setFacePicPath(preferFace(b.getFacePicPath(), a.getFacePicPath()));
		out.setBelongList(mergeCsvDistinct(a.getBelongList(), b.getBelongList()));
		out.setHeight(firstNonBlank(b.getHeight(), a.getHeight()));
		out.setWeight(firstNonBlank(b.getWeight(), a.getWeight()));
		out.setPosition(firstNonBlank(b.getPosition(), a.getPosition()));
		out.setBirth(firstNonBlank(b.getBirth(), a.getBirth()));
		out.setAge(firstNonBlank(b.getAge(), a.getAge()));
		out.setMarketValue(firstNonBlank(b.getMarketValue(), a.getMarketValue()));
		out.setInjury(firstNonBlank(b.getInjury(), a.getInjury()));
		out.setVersusTeamScoreData(firstNonBlank(b.getVersusTeamScoreData(), a.getVersusTeamScoreData()));
		out.setRetireFlg("0");
		out.setDeadline(firstNonBlank(b.getDeadline(), a.getDeadline()));
		out.setDeadlineContractDate(firstNonBlank(b.getDeadlineContractDate(), a.getDeadlineContractDate()));
		out.setLatestInfoDate(runDate);
		out.setMissingCount("0");

		return out;
	}

	// =========================================================================
	// 新規 / 更新 / 移籍 / missing 各処理
	// =========================================================================

	private TeamMemberMasterEntity buildNewEntity(TeamMemberMasterEntity incoming, String runDate) {
		TeamMemberMasterEntity e = copyOf(incoming);
		e.setBelongList(mergeBelongList(e.getBelongList(), e.getTeam()));
		e.setRetireFlg("0");
		e.setLatestInfoDate(runDate);
		e.setMissingCount("0");
		e.setDelFlg("0");
		return e;
	}

	private TeamMemberMasterEntity mergeSameTeam(TeamMemberMasterEntity existing,
			TeamMemberMasterEntity incoming,
			String runDate) {
		existing.setCountry(firstNonBlank(incoming.getCountry(), existing.getCountry()));
		existing.setLeague(firstNonBlank(incoming.getLeague(), existing.getLeague()));
		existing.setTeam(firstNonBlank(incoming.getTeam(), existing.getTeam()));
		existing.setScore(mergeScore(existing.getScore(), incoming.getScore()));
		existing.setLoanBelong(mergeCsvDistinct(existing.getLoanBelong(), incoming.getLoanBelong()));
		existing.setJersey(firstNonBlank(incoming.getJersey(), existing.getJersey()));
		existing.setMember(firstNonBlank(incoming.getMember(), existing.getMember()));
		existing.setFacePicPath(preferFace(incoming.getFacePicPath(), existing.getFacePicPath()));
		existing.setBelongList(mergeBelongList(existing.getBelongList(), existing.getTeam()));
		existing.setHeight(firstNonBlank(incoming.getHeight(), existing.getHeight()));
		existing.setWeight(firstNonBlank(incoming.getWeight(), existing.getWeight()));
		existing.setPosition(firstNonBlank(incoming.getPosition(), existing.getPosition()));
		existing.setBirth(firstNonBlank(incoming.getBirth(), existing.getBirth()));
		existing.setAge(firstNonBlank(incoming.getAge(), existing.getAge()));
		existing.setMarketValue(firstNonBlank(incoming.getMarketValue(), existing.getMarketValue()));
		existing.setInjury(mergeInjury(existing.getInjury(), incoming.getInjury()));
		existing.setVersusTeamScoreData(
				firstNonBlank(incoming.getVersusTeamScoreData(), existing.getVersusTeamScoreData()));
		existing.setDeadline(firstNonBlank(incoming.getDeadline(), existing.getDeadline()));
		existing.setDeadlineContractDate(
				firstNonBlank(incoming.getDeadlineContractDate(), existing.getDeadlineContractDate()));
		existing.setRetireFlg("0");
		existing.setLatestInfoDate(runDate);
		existing.setMissingCount("0");
		return existing;
	}

	private TeamMemberMasterEntity applyTransfer(TeamMemberMasterEntity existing,
			TeamMemberMasterEntity incoming,
			String runDate) {
		// 旧チームを belong_list に追加してから新チームへ更新
		existing.setBelongList(
				mergeBelongList(existing.getBelongList(), existing.getTeam(), incoming.getTeam()));

		existing.setCountry(firstNonBlank(incoming.getCountry(), existing.getCountry()));
		existing.setLeague(firstNonBlank(incoming.getLeague(), existing.getLeague()));
		existing.setTeam(firstNonBlank(incoming.getTeam(), existing.getTeam()));
		existing.setScore(mergeScore(existing.getScore(), incoming.getScore()));
		existing.setLoanBelong(mergeCsvDistinct(existing.getLoanBelong(), incoming.getLoanBelong()));
		existing.setJersey(firstNonBlank(incoming.getJersey(), existing.getJersey()));
		existing.setMember(firstNonBlank(incoming.getMember(), existing.getMember()));
		existing.setFacePicPath(preferFace(incoming.getFacePicPath(), existing.getFacePicPath()));
		existing.setHeight(firstNonBlank(incoming.getHeight(), existing.getHeight()));
		existing.setWeight(firstNonBlank(incoming.getWeight(), existing.getWeight()));
		existing.setPosition(firstNonBlank(incoming.getPosition(), existing.getPosition()));
		existing.setBirth(firstNonBlank(incoming.getBirth(), existing.getBirth()));
		existing.setAge(firstNonBlank(incoming.getAge(), existing.getAge()));
		existing.setMarketValue(firstNonBlank(incoming.getMarketValue(), existing.getMarketValue()));
		existing.setInjury(mergeInjury(existing.getInjury(), incoming.getInjury()));
		existing.setVersusTeamScoreData(
				firstNonBlank(incoming.getVersusTeamScoreData(), existing.getVersusTeamScoreData()));
		existing.setDeadline(firstNonBlank(incoming.getDeadline(), existing.getDeadline()));
		existing.setDeadlineContractDate(
				firstNonBlank(incoming.getDeadlineContractDate(), existing.getDeadlineContractDate()));
		existing.setRetireFlg("0");
		existing.setLatestInfoDate(runDate);
		existing.setMissingCount("0");
		return existing;
	}

	private TeamMemberMasterEntity markMissingOrRetired(TeamMemberMasterEntity existing) {
		int current = parseIntSafe(existing.getMissingCount());
		int next = current + 1;
		existing.setMissingCount(String.valueOf(next));
		existing.setRetireFlg(next >= RETIRE_MISSING_THRESHOLD ? "1" : "0");
		return existing;
	}

	// =========================================================================
	// マージユーティリティ
	// =========================================================================

	private String mergeBelongList(String... values) {
		LinkedHashSet<String> set = new LinkedHashSet<>();
		for (String value : values) {
			if (isBlank(value)) {
				continue;
			}
			for (String token : value.split(",")) {
				String cleaned = clean(token);
				if (!isBlank(cleaned)) {
					set.add(cleaned);
				}
			}
		}
		return set.isEmpty() ? null : String.join(",", set);
	}

	private String mergeCsvDistinct(String a, String b) {
		return mergeBelongList(a, b);
	}

	private String mergeScore(String oldScore, String newScore) {
		if (isBlank(oldScore))
			return clean(newScore);
		if (isBlank(newScore))
			return clean(oldScore);
		if (eq(oldScore, newScore))
			return clean(oldScore);
		// 独自マージが必要な場合はここを差し替えてください
		return clean(newScore);
	}

	private String preferFace(String newFace, String oldFace) {
		return isBlank(newFace) ? clean(oldFace) : clean(newFace);
	}

	private String mergeInjury(String oldVal, String newVal) {
		// 新データが空・N/A 相当のときは既存を保持
		if (isBlank(newVal) || "N/A".equalsIgnoreCase(newVal.trim())) {
			return clean(oldVal);
		}
		return clean(newVal);
	}

	// =========================================================================
	// Entity コピー
	// =========================================================================

	private TeamMemberMasterEntity copyOf(TeamMemberMasterEntity src) {
		TeamMemberMasterEntity e = new TeamMemberMasterEntity();
		e.setId(src.getId());
		e.setFile(src.getFile());
		e.setCountry(src.getCountry());
		e.setLeague(src.getLeague());
		e.setTeam(src.getTeam());
		e.setScore(src.getScore());
		e.setLoanBelong(src.getLoanBelong());
		e.setJersey(src.getJersey());
		e.setMember(src.getMember());
		e.setFacePicPath(src.getFacePicPath());
		e.setBelongList(src.getBelongList());
		e.setHeight(src.getHeight());
		e.setWeight(src.getWeight());
		e.setPosition(src.getPosition());
		e.setBirth(src.getBirth());
		e.setAge(src.getAge());
		e.setMarketValue(src.getMarketValue());
		e.setInjury(src.getInjury());
		e.setVersusTeamScoreData(src.getVersusTeamScoreData());
		e.setRetireFlg(src.getRetireFlg());
		e.setDeadline(src.getDeadline());
		e.setDeadlineContractDate(src.getDeadlineContractDate());
		e.setLatestInfoDate(src.getLatestInfoDate());
		e.setUpdStamp(src.getUpdStamp());
		e.setDelFlg(src.getDelFlg());
		e.setMissingCount(src.getMissingCount());
		return e;
	}

	// =========================================================================
	// 文字列ユーティリティ
	// =========================================================================

	private void normalizeEntity(TeamMemberMasterEntity e, String runDate) {
		if (e == null)
			return;
		e.setCountry(clean(e.getCountry()));
		e.setLeague(clean(e.getLeague()));
		e.setTeam(clean(e.getTeam()));
		e.setScore(clean(e.getScore()));
		e.setLoanBelong(cleanCsv(e.getLoanBelong()));
		e.setJersey(clean(e.getJersey()));
		e.setMember(clean(e.getMember()));
		e.setFacePicPath(clean(e.getFacePicPath()));
		e.setBelongList(cleanCsv(e.getBelongList()));
		e.setHeight(clean(e.getHeight()));
		e.setWeight(clean(e.getWeight()));
		e.setPosition(clean(e.getPosition()));
		e.setBirth(clean(e.getBirth()));
		e.setAge(clean(e.getAge()));
		e.setMarketValue(clean(e.getMarketValue()));
		e.setInjury(clean(e.getInjury()));
		e.setVersusTeamScoreData(clean(e.getVersusTeamScoreData()));
		e.setDeadline(clean(e.getDeadline()));
		e.setDeadlineContractDate(clean(e.getDeadlineContractDate()));
		if (isBlank(e.getRetireFlg()))
			e.setRetireFlg("0");
		if (isBlank(e.getMissingCount()))
			e.setMissingCount("0");
		if (isBlank(e.getLatestInfoDate()))
			e.setLatestInfoDate(runDate);
		if (isBlank(e.getDelFlg()))
			e.setDelFlg("0");
	}

	private String clean(String s) {
		if (s == null)
			return null;
		return s.replace('\u3000', ' ').trim().replaceAll("\\s+", " ");
	}

	private String cleanCsv(String s) {
		if (isBlank(s))
			return null;
		return Arrays.stream(s.split(","))
				.map(this::clean)
				.filter(x -> !isBlank(x))
				.distinct()
				.collect(Collectors.joining(","));
	}

	private boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

	private boolean isBlank(Integer s) {
		return s == null;
	}

	private boolean eq(String a, String b) {
		return Objects.equals(clean(a), clean(b));
	}

	private String firstNonBlank(String... values) {
		for (String v : values) {
			if (!isBlank(v))
				return clean(v);
		}
		return null;
	}

	private String nvl(String s) {
		return s == null ? "" : s;
	}

	private int parseIntSafe(String s) {
		if (isBlank(s))
			return 0;
		try {
			return Integer.parseInt(s.trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
