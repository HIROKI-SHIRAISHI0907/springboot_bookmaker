package dev.batch.bm_b002;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.batch.interf.TeamMemberEntityIF;
import dev.common.config.PathConfig;
import dev.common.constant.MessageCdConst;
import dev.common.entity.TeamMemberMasterEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;
import dev.common.util.FileDeleteUtil;

/**
 * team_member_masterロジック
 * @author shiraishitoshio
 *
 */
@Component
@Transactional
public class TeamMemberMasterStat implements TeamMemberEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = TeamMemberMasterStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = TeamMemberMasterStat.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "TEAM_MEMBER";

	/** 監督 */
	private static final String MANAGER = "監督";

	/** beanクラス */
	@Autowired
	private BmB002TeamMemberMasterBean bean;

	/** TeamMemberDBService部品 */
	@Autowired
	private TeamMemberDBService teamMemberDBService;

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
	 * {@inheritDoc}
	 * @throws Exception
	 */
	@Override
	public void teamMemberStat(Map<String, List<TeamMemberMasterEntity>> entities) throws Exception {
		final String METHOD_NAME = "teamMemberStat";
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		this.bean.init();

		// チームマップ
		Map<String, List<String>> teamMap = this.bean.getTeamMap();
		if (teamMap.isEmpty()) {
			String messageCd = MessageCdConst.MCD00014I_NO_MAP_DATA;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, "country_league_masterにデータがありません。");
			this.manageLoggerComponent.debugEndInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME);
			this.manageLoggerComponent.clear();
			return;
		}

		// DB既存 memberMap（キー = member）
		Map<String, TeamMemberMasterEntity> memberMap = this.bean.getMemberMap();

		// このバッチ実行中に逐次反映する作業用Map
		Map<String, TeamMemberMasterEntity> workingMemberMap = new HashMap<>(memberMap);

		// 既にDBに存在している member キー
		Set<String> persistedMemberKeys = new HashSet<>(memberMap.keySet());

		List<String> insertPath = new ArrayList<String>();

		for (Map.Entry<String, List<TeamMemberMasterEntity>> map : entities.entrySet()) {
			String filePath = map.getKey();
			String fillChar = "ファイル名: " + filePath;

			// 同一ファイル内重複を吸収するため Map で持つ
			Map<String, TeamMemberMasterEntity> insertMap = new LinkedHashMap<>();
			Map<String, TeamMemberMasterEntity> updateMap = new LinkedHashMap<>();

			try {
				List<TeamMemberMasterEntity> editedList = editData(map.getValue());

				for (TeamMemberMasterEntity entity : editedList) {
					// 監督はskip
					if (MANAGER.equals(entity.getPosition())) {
						continue;
					}

					// member が無いものは同一人物追跡できないのでスキップ
					String keyMember = BmB002TeamMemberMasterBean.memberKey(entity);
					if (isBlank(keyMember)) {
						continue;
					}

					TeamMemberMasterEntity oldEntity = workingMemberMap.get(keyMember);

					// 既存 / 同一バッチ内にすでに処理済みなら merge
					if (oldEntity != null) {
						TeamMemberMasterEntity merged = updateData(entity, oldEntity);
						if (merged != null) {
							if (persistedMemberKeys.contains(keyMember)) {
								// 既にDBにいる → update対象
								updateMap.put(keyMember, merged);
							} else {
								// まだこのバッチでinsert予定 → insertMap上書き
								merged.setId(null);
								insertMap.put(keyMember, merged);
							}
							workingMemberMap.put(keyMember, merged);
						}
						continue;
					}

					// ---- ここから下は「新規登録」候補のみ ----
					String country = entity.getCountry();
					String league = entity.getLeague();
					String team = entity.getTeam();
					String keyCL = nz(country) + "-" + nz(league);
					List<String> teams = teamMap.get(keyCL);

					// 新規は従来通り、所属チームがマスタにいるものだけ
					if (teams == null || !teams.contains(team)) {
						continue;
					}

					insertMap.put(keyMember, entity);
					workingMemberMap.put(keyMember, entity);
				}

				List<TeamMemberMasterEntity> insertEntities = new ArrayList<>(insertMap.values());
				List<TeamMemberMasterEntity> updateEntities = new ArrayList<>(updateMap.values());

				int result = this.teamMemberDBService.insertInBatch(insertEntities);
				if (result == 9) {
					String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
					throw new Exception(messageCd);
				}

				result = this.teamMemberDBService.updateInBatch(updateEntities, fillChar);
				if (result == 9) {
					String messageCd = MessageCdConst.MCD00008E_UPDATE_FAILED;
					throw new Exception(messageCd);
				}

				// このファイルのinsert/update成功後は「DBに存在する扱い」にする
				persistedMemberKeys.addAll(insertMap.keySet());
				persistedMemberKeys.addAll(updateMap.keySet());

				insertPath.add(filePath);

			} catch (Exception e) {
				String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
				throw new Exception(messageCd, e);
			}
		}

		// 途中で例外が起きなければ全てのファイルを削除する
		String bucket = config.getS3BucketsTeamMemberData();
		FileDeleteUtil.deleteS3Files(
				insertPath,
				bucket,
				s3Operator,
				manageLoggerComponent,
				PROJECT_NAME,
				CLASS_NAME,
				METHOD_NAME,
				"TEAM_MEMBER_MASTER");

		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}

	/**
	 * 編集データメソッド
	 *  - team(暫定)        → belongList へ詰め替え（重複は除外、カンマ連結）
	 *  - score(暫定) + versusTeam(暫定) → versusTeamScoreData へ「対戦相手-得点」をカンマ連結
	 *  - loanBelong が空でなければ deadline = "1"、なければ "0"
	 *  - retireFlg は既定で "0"
	 */
	private List<TeamMemberMasterEntity> editData(List<TeamMemberMasterEntity> entities) {
		List<TeamMemberMasterEntity> newDtoList = new ArrayList<>();
		for (TeamMemberMasterEntity e : entities) {

			String team = nz(e.getTeam());
			String belongList = nz(e.getBelongList());
			if (!team.isEmpty()) {
				Set<String> set = new LinkedHashSet<>();
				if (!belongList.isEmpty()) {
					for (String s : belongList.split(",")) {
						if (!s.isBlank()) {
							set.add(s.trim());
						}
					}
				}
				set.add(team.trim());
				belongList = String.join(",", set);
				e.setBelongList(belongList);
			} else {
				e.setBelongList(belongList);
			}

			e.setMarketValue(naToEmpty(e.getMarketValue()));
			e.setVersusTeamScoreData(nz(e.getVersusTeamScoreData()));
			e.setInjury(naToEmpty(e.getInjury()));

			String loanBelong = nz(e.getLoanBelong());
			e.setDeadline(loanBelong.isEmpty() ? "0" : "1");

			e.setRetireFlg("0");

			newDtoList.add(e);
		}
		return newDtoList;
	}

	/** ===== ユーティリティ ===== */
	private static String nz(String s) {
		return s == null ? "" : s.trim();
	}

	/**
	 * 更新データメソッド（member が同一のものを更新対象とする）
	 */
	private TeamMemberMasterEntity updateData(TeamMemberMasterEntity exDto, TeamMemberMasterEntity oldDto) {
		if (oldDto == null || exDto == null || !Objects.equals(oldDto.getMember(), exDto.getMember())) {
			return null;
		}

		final String prevTeam = oldDto.getTeam();

		TeamMemberMasterEntity newDto = new TeamMemberMasterEntity();
		newDto.setId(oldDto.getId());

		// 国・リーグ・チームは新情報優先
		newDto.setCountry(!isBlank(exDto.getCountry()) ? exDto.getCountry() : oldDto.getCountry());
		newDto.setLeague(!isBlank(exDto.getLeague()) ? exDto.getLeague() : oldDto.getLeague());
		newDto.setTeam(!isBlank(exDto.getTeam()) ? exDto.getTeam() : oldDto.getTeam());

		newDto.setMember(oldDto.getMember());

		// score は team4=score 形式で履歴管理
		newDto.setScore(mergeScoreByTeam4(oldDto, exDto, newDto.getTeam()));

		// ローン所属履歴
		newDto.setLoanBelong(mergeCsvHistory(oldDto.getLoanBelong(), exDto.getLoanBelong()));

		// 所属履歴
		String belongBase = !isBlank(oldDto.getBelongList())
				? oldDto.getBelongList()
				: mergeCsvHistory(oldDto.getTeam(), null);
		newDto.setBelongList(mergeCsvHistory(belongBase, exDto.getTeam()));

		// 背番号履歴
		String jerseyNow;
		String oldJ = trimToEmpty(oldDto.getJersey());
		String newJ = trimToEmpty(exDto.getJersey());
		if (isBlank(oldJ)) {
			jerseyNow = newJ;
		} else if (oldJ.equals(newJ) || isBlank(newJ)) {
			jerseyNow = oldJ;
		} else {
			String annotatedOld = oldJ;
			if (!isBlank(prevTeam) && newDto.getBelongList().contains(",")) {
				String head4 = prevTeam.length() >= 4 ? prevTeam.substring(0, 4) : prevTeam;
				annotatedOld = oldJ + "(" + head4 + ")";
			}
			jerseyNow = mergeCsvHistory(annotatedOld, newJ);
		}
		newDto.setJersey(jerseyNow);

		// 顔写真は新があれば新優先
		newDto.setFacePicPath(!isBlank(exDto.getFacePicPath()) ? exDto.getFacePicPath() : oldDto.getFacePicPath());

		// 生年月日・年齢は新優先
		newDto.setBirth(!isBlank(exDto.getBirth()) ? exDto.getBirth() : oldDto.getBirth());
		newDto.setAge(!isBlank(exDto.getAge()) ? exDto.getAge() : oldDto.getAge());

		// 故障情報は N/A/空なら更新しない
		newDto.setInjury(!isBlank(naToEmpty(exDto.getInjury())) ? naToEmpty(exDto.getInjury()) : oldDto.getInjury());

		newDto.setDeadline(!isBlank(exDto.getLoanBelong()) ? "1" : "0");

		String keepRetire = "1".equals(trimToEmpty(oldDto.getRetireFlg()))
				? "1"
				: (isBlank(exDto.getRetireFlg()) ? "0" : exDto.getRetireFlg());
		newDto.setRetireFlg(keepRetire);

		newDto.setDeadlineContractDate(mergeHistory(oldDto.getDeadlineContractDate(), exDto.getDeadlineContractDate()));
		newDto.setHeight(mergeHistory(oldDto.getHeight(), exDto.getHeight()));
		newDto.setWeight(mergeHistory(oldDto.getWeight(), exDto.getWeight()));
		newDto.setPosition(mergeHistory(oldDto.getPosition(), exDto.getPosition()));
		newDto.setMarketValue(
				mergeHistory(
						naToEmpty(oldDto.getMarketValue()),
						naToEmpty(exDto.getMarketValue())));

		newDto.setLatestInfoDate(!isBlank(exDto.getLatestInfoDate()) ? exDto.getLatestInfoDate() : oldDto.getLatestInfoDate());
		newDto.setUpdStamp(incrementUpdStamp(oldDto.getUpdStamp()));

		// そのまま維持
		newDto.setVersusTeamScoreData(!isBlank(exDto.getVersusTeamScoreData()) ? exDto.getVersusTeamScoreData() : oldDto.getVersusTeamScoreData());

		return newDto;
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}

	private static String trimToEmpty(String s) {
		return s == null ? "" : s.trim();
	}

	private static String incrementUpdStamp(String old) {
		String v = trimToEmpty(old);
		if (v.isEmpty()) {
			return "更新済み1";
		}
		if (v.startsWith("更新済み")) {
			String nStr = v.substring("更新済み".length()).trim();
			try {
				int n = Integer.parseInt(nStr);
				return "更新済み" + (n + 1);
			} catch (NumberFormatException ignore) {
				return "更新済み1";
			}
		}
		return "更新済み1";
	}

	private static String mergeHistory(String oldValue, String newValue) {
		return mergeHistory(oldValue, newValue, "→");
	}

	private static String mergeHistory(String oldValue, String newValue, String sep) {
		sep = (sep == null || sep.isEmpty()) ? "→" : sep;

		String oldV = oldValue == null ? "" : oldValue.trim();
		String newV = newValue == null ? "" : newValue.trim();

		if (newV.isEmpty()) {
			return oldV;
		}
		if (oldV.isEmpty()) {
			return newV;
		}

		String last = oldV;
		int idx = oldV.lastIndexOf(sep);
		if (idx >= 0) {
			last = oldV.substring(idx + sep.length()).trim();
		}

		if (last.equals(newV)) {
			return oldV;
		}

		return oldV + sep + newV;
	}

	private static String mergeCsvHistory(String oldValue, String newValue) {
		String oldNorm = normalizeCsv(oldValue);
		String newNorm = normalizeCsv(newValue);

		if (oldNorm.isEmpty() && newNorm.isEmpty()) {
			return "";
		}
		if (oldNorm.isEmpty()) {
			return newNorm;
		}
		if (newNorm.isEmpty()) {
			return oldNorm;
		}

		LinkedHashSet<String> merged = new LinkedHashSet<>();

		for (String t : oldNorm.split(",")) {
			String s = t.trim();
			if (!s.isEmpty()) {
				merged.add(s);
			}
		}
		for (String t : newNorm.split(",")) {
			String s = t.trim();
			if (!s.isEmpty()) {
				merged.add(s);
			}
		}
		return String.join(",", merged);
	}

	private static String mergeScoreByTeam4(TeamMemberMasterEntity oldDto,
			TeamMemberMasterEntity exDto,
			String currentTeam) {

		LinkedHashMap<String, Integer> map = parseTeam4ScoreHistory(oldDto);

		String team = trimToEmpty(currentTeam);
		if (team.isEmpty()) {
			team = trimToEmpty(exDto.getTeam());
		}
		if (team.isEmpty()) {
			team = trimToEmpty(oldDto.getTeam());
		}
		String team4 = head4(team);

		Integer add = parseIntOrNull(trimToEmpty(exDto.getScore()));
		if (team4.isEmpty() || add == null) {
			return toTeam4ScoreString(map);
		}

		map.put(team4, map.getOrDefault(team4, 0) + add);

		return toTeam4ScoreString(map);
	}

	private static LinkedHashMap<String, Integer> parseTeam4ScoreHistory(TeamMemberMasterEntity oldDto) {
		LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
		if (oldDto == null) {
			return map;
		}

		String s = trimToEmpty(oldDto.getScore());
		String team4FromOldTeam = head4(trimToEmpty(oldDto.getTeam()));

		Integer plain = parseIntOrNull(s);
		if (plain != null) {
			if (!team4FromOldTeam.isEmpty()) {
				map.put(team4FromOldTeam, plain);
			}
			return map;
		}

		if (!s.isEmpty()) {
			for (String token : s.split(",")) {
				String t = token.trim();
				if (t.isEmpty()) {
					continue;
				}
				int eq = t.lastIndexOf('=');
				if (eq < 0) {
					continue;
				}
				String k = t.substring(0, eq).trim();
				String v = t.substring(eq + 1).trim();
				Integer n = parseIntOrNull(v);
				if (!k.isEmpty() && n != null) {
					map.put(k, n);
				}
			}
		}
		return map;
	}

	private static String toTeam4ScoreString(LinkedHashMap<String, Integer> map) {
		if (map.isEmpty()) {
			return "";
		}
		List<String> out = new ArrayList<>();
		for (var e : map.entrySet()) {
			out.add(e.getKey() + "=" + e.getValue());
		}
		return String.join(",", out);
	}

	private static String head4(String team) {
		if (team == null) {
			return "";
		}
		String t = team.trim();
		if (t.isEmpty()) {
			return "";
		}
		return t.length() <= 4 ? t : t.substring(0, 4);
	}

	private static Integer parseIntOrNull(String s) {
		try {
			if (s == null || s.isBlank()) {
				return null;
			}
			return Integer.valueOf(s.trim());
		} catch (Exception ex) {
			return null;
		}
	}

	private static String normalizeCsv(String s) {
		if (s == null) {
			return "";
		}
		String x = s.trim();
		if (x.isEmpty()) {
			return "";
		}
		x = x.replace('，', ',').replace('、', ',');
		x = x.replaceAll("\\s*,\\s*", ",").replaceAll(",{2,}", ",");
		if (x.startsWith(",")) {
			x = x.substring(1);
		}
		if (x.endsWith(",")) {
			x = x.substring(0, x.length() - 1);
		}
		return x.trim();
	}

	private static String naToEmpty(String s) {
		if (s == null) {
			return "";
		}
		String t = s.trim();
		if (t.isEmpty()) {
			return "";
		}
		if ("N/A".equalsIgnoreCase(t)) {
			return "";
		}
		if ("-".equals(t)) {
			return "";
		}
		return t;
	}
}
