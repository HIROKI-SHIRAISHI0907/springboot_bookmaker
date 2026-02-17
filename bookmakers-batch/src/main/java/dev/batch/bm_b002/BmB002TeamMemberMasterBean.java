package dev.batch.bm_b002;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.batch.repository.master.CountryLeagueMasterBatchRepository;
import dev.common.constant.MessageCdConst;
import dev.common.entity.CountryLeagueMasterEntity;
import dev.common.entity.TeamMemberMasterEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * team_member_masterのbeanロジック
 * @author shiraishitoshio
 *
 */
@Component
public class BmB002TeamMemberMasterBean {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = BmB002TeamMemberMasterBean.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = BmB002TeamMemberMasterBean.class.getName();

	/** ZONEID */
	private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

	/** TeamMemberDBService部品 */
	@Autowired
	private TeamMemberDBService teamMemberDBService;

	/** CountryLeagueMasterRepositoryレポジトリクラス */
	@Autowired
	private CountryLeagueMasterBatchRepository countryLeagueMasterRepository;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent loggerComponent;

	/** 件数取得 */
	private Map<String, TeamMemberMasterEntity> teamMemberMap;

	/** 件数取得 */
	private Map<String, List<String>> teamMap;

	/**
	 * 初期Mapデータ生成
	 */
	public void init() {
		final String METHOD_NAME = "init";
		// hashデータを取得
		Map<String, TeamMemberMasterEntity> map = new HashMap<String, TeamMemberMasterEntity>();
		try {
			List<List<TeamMemberMasterEntity>> list = this.teamMemberDBService.selectInBatch();
			for (List<TeamMemberMasterEntity> listTmp : list) {
				for (TeamMemberMasterEntity subMap : listTmp) {
					String key = personKey(subMap);
					if (key != null) {
						map.put(key, subMap);
					}
				}
			}
		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00013E_INITILIZATION_ERROR;
			String fillChar = (e.getMessage() != null) ? e.getMessage() : null;
			this.loggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e, fillChar);
			this.loggerComponent.createBusinessException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					messageCd,
					null,
					null);
		}
		this.teamMemberMap = map;

		// hashデータを取得
		Map<String, List<String>> team = new HashMap<String, List<String>>();
		List<CountryLeagueMasterEntity> master = this.countryLeagueMasterRepository.findData();
		for (CountryLeagueMasterEntity entity : master) {
			String country = entity.getCountry();
			String league = entity.getLeague();
			String teams = entity.getTeam();
			// スキップ
			if ("ドジャース（大谷 翔平)".equals(teams) || "レイカーズ".equals(teams)
					|| "インテル・マイアミCF".equals(teams)) {
				continue;
			}
			String key = nz(country) + "-" + nz(league);
			team.computeIfAbsent(key, k -> new ArrayList<>()).add(teams);
		}
		this.teamMap = team;
	}

	/** パーソンキー（移籍して同一人物でない可能性があるため）*/
	public static String personKey(TeamMemberMasterEntity e) {
		if (e == null)
			return null;

		String member = nz(e.getMember());
		String birthS = nz(e.getBirth());
		String ageS = nz(e.getAge());

		if (member.isEmpty() || birthS.isEmpty() || ageS.isEmpty())
			return null;

		Integer ageFromCsv = parseIntOrNull(ageS);
		LocalDate birth = parseDateOrNull(birthS);
		if (ageFromCsv == null || birth == null)
			return null;

		// 基準日：latestInfoDate があればそれ、なければ今日（JST）
		LocalDate baseDate = parseDateOrNull(nz(e.getLatestInfoDate()));
		if (baseDate == null)
			baseDate = LocalDate.now(JST);

		int ageKey = calcAge(birth, baseDate); // ←誕生日を迎えた瞬間 +1 になる
		return member + "|" + birthS + "|" + ageKey;
	}

	public static String personKeyMinus1(String personKey) {
		// "member|birth|NN" の NN を NN-1 にする
		if (personKey == null)
			return null;
		int idx = personKey.lastIndexOf('|');
		if (idx < 0)
			return null;
		String head = personKey.substring(0, idx);
		String tail = personKey.substring(idx + 1);
		Integer n = parseIntOrNull(tail);
		if (n == null)
			return null;
		return head + "|" + (n - 1);
	}

	/** ===== ユーティリティ ===== */
	private static String nz(String s) {
		return s == null ? "" : s.trim();
	}

	/** ===== ユーティリティ ===== */
	private static Integer parseIntOrNull(String s) {
		try {
			return Integer.valueOf(s.trim());
		} catch (Exception ex) {
			return null;
		}
	}

	/** ===== ユーティリティ ===== */
	private static LocalDate parseDateOrNull(String s) {
		if (s == null || s.trim().isEmpty())
			return null;
		String t = s.trim();

		// ISO (yyyy-MM-dd)
		try {
			return LocalDate.parse(t);
		} catch (Exception ignore) {
		}

		// dd.MM.yyyy
		try {
			java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy");
			return LocalDate.parse(t, f);
		} catch (Exception ignore) {
		}

		// 3) "yyyy-MM-dd HH:mm:ss" → LocalDateTime で読んで日付だけ取る
		try {
			var f = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
			return java.time.LocalDateTime.parse(t, f).toLocalDate();
		} catch (Exception ignore) {
		}

		// 4) "yyyy-MM-dd'T'HH:mm:ss"（ISOっぽい日時）→ LocalDateTime
		try {
			return java.time.LocalDateTime.parse(t).toLocalDate();
		} catch (Exception ignore) {
		}

		// 5) "yyyy-MM-dd'T'HH:mm:ss.SSS..." → OffsetDateTime/Instant 系も吸収（Z付きなど）
		try {
			return java.time.OffsetDateTime.parse(t).toLocalDate();
		} catch (Exception ignore) {
		}

		return null;
	}

	/** 年齢計算 */
	private static int calcAge(LocalDate birth, LocalDate baseDate) {
		// Periodで年齢計算（誕生日を迎えたら自動的に+1）
		return java.time.Period.between(birth, baseDate).getYears();
	}

	/**
	 * メンバーリスト
	 * @return Map<String, TeamMemberMasterEntity>
	 */
	public Map<String, TeamMemberMasterEntity> getMemberMap() {
		return this.teamMemberMap;
	}

	/**
	 * マスタリスト
	 * @return Map<String, List<String>>
	 */
	public Map<String, List<String>> getTeamMap() {
		return this.teamMap;
	}
}
