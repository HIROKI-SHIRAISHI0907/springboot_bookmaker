package dev.batch.bm_b017;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.batch.repository.master.CountryLeagueMasterBatchRepository;
import dev.common.config.PathConfig;
import dev.common.entity.CountryLeagueMasterEntity;
import dev.common.mail.SmtpMailSender;
import dev.common.s3.S3Operator;
import dev.common.util.DateOffsetDecisionUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * 取るべきリーグ候補の通知サービス（B017） 2026-11-21 追加
 *
 * <ol>
 *   <li>aws-s3-no-ecs-task-time-csv/league_candidates/pending/*.json を読む
 *       （no-game スクレイパーが出力する「対象外リーグごとの出場チーム一覧」）</li>
 *   <li>country_league_master の有効チームと突き合わせ、対象チームが1つ以上出ている
 *       対象外リーグを候補にする</li>
 *   <li>まだ通知していない候補があれば、管理者へメールする
 *       （通知済みは league_candidates/_notified.json に記録し、同じリーグは2回送らない）</li>
 *   <li>処理した JSON は league_candidates/done/ へ移す</li>
 * </ol>
 * メール送信に失敗した場合は例外にして JSON を pending に残す（次回再送）。
 */
@Slf4j
@Service
public class LeagueCandidateNoticeService {

	static final String PENDING_PREFIX = "league_candidates/pending/";
	static final String DONE_PREFIX = "league_candidates/done/";
	static final String NOTIFIED_KEY = "league_candidates/_notified.json";

	/** 1通に載せる候補の上限（多すぎると読めないため。残りは件数だけ書く） */
	private static final int MAX_LIST = 50;

	/** 1候補あたりに表示する対象チーム数の上限 */
	private static final int MAX_TEAMS_PER_LEAGUE = 10;

	@Autowired
	private S3Operator s3Operator;

	@Autowired
	private PathConfig config;

	@Autowired
	private CountryLeagueMasterBatchRepository countryLeagueMasterBatchRepository;

	@Autowired
	private SmtpMailSender smtpMailSender;

	/** 通知先（未設定なら送信元 system アカウント宛） */
	@Value("${app.notification.admin-email:${mail.accounts.system.username:}}")
	private String adminEmail;

	private final ObjectMapper objectMapper = new ObjectMapper();

	/** 1リーグ分の候補 */
	static final class Candidate {
		String country;
		String league;
		String category;
		int matchCount;
		final Set<String> dates = new TreeSet<>();
		/** 一致した対象チーム → その所属（国: リーグ） */
		final Map<String, String> matchedTeams = new LinkedHashMap<>();
	}

	public void execute() throws Exception {
		String bucket = config.getS3NoEcs();
		List<String> keys = new ArrayList<>();
		for (String k : s3Operator.listKeys(bucket, PENDING_PREFIX)) {
			if (k.endsWith(".json")) {
				keys.add(k);
			}
		}
		if (keys.isEmpty()) {
			log.info("[B017] pending の候補JSONがありません。bucket={}", bucket);
			return;
		}

		Map<String, String> teamIndex = buildTeamIndex();
		Map<String, String> notified = loadNotified(bucket);
		Map<String, Candidate> candidates = new LinkedHashMap<>();

		for (String key : keys) {
			JsonNode root;
			try {
				root = objectMapper.readTree(s3Operator.downloadTextUtf8(bucket, key));
			} catch (Exception e) {
				log.warn("[B017] JSONが読めないため done/ に移します。key={}, e={}", key, e.toString());
				continue;
			}
			String date = root.path("target_date").asText("");
			for (JsonNode lg : root.path("leagues")) {
				String category = lg.path("category").asText("").trim();
				if (category.isEmpty() || notified.containsKey(norm(category))) {
					continue;
				}
				Map<String, String> matched = new LinkedHashMap<>();
				for (JsonNode t : lg.path("teams")) {
					String team = t.asText("").trim();
					String belong = teamIndex.get(norm(team));
					if (belong != null && !belong.equalsIgnoreCase(category)) {
						matched.put(team, belong);
					}
				}
				if (matched.isEmpty()) {
					continue;
				}
				Candidate c = candidates.computeIfAbsent(norm(category), k -> new Candidate());
				c.country = lg.path("country").asText("");
				c.league = lg.path("league").asText("");
				c.category = category;
				c.matchCount += lg.path("match_count").asInt(0);
				if (!date.isEmpty()) {
					c.dates.add(date);
				}
				c.matchedTeams.putAll(matched);
			}
		}

		if (!candidates.isEmpty()) {
			List<Candidate> list = new ArrayList<>(candidates.values());
			list.sort(Comparator.comparingInt((Candidate c) -> c.matchedTeams.size()).reversed()
					.thenComparing(c -> c.category));
			String today = LocalDate.now(DateOffsetDecisionUtil.getZoneId()).toString();
			String subject = "【bm-stats-real】取るべきリーグの候補 " + list.size() + "件（" + today + "）";
			String body = buildBody(list);
			String to = (adminEmail == null || adminEmail.isBlank()) ? smtpMailSender.fromAddress() : adminEmail;
			// 失敗したら例外 → pending に残して次回再送
			smtpMailSender.send(to, subject, body);
			for (Candidate c : list) {
				notified.put(norm(c.category), today);
			}
			s3Operator.putJson(bucket, NOTIFIED_KEY, objectMapper.writerWithDefaultPrettyPrinter()
					.writeValueAsString(notified));
			log.info("[B017] 候補 {} 件を通知しました。to={}", list.size(), to);
		} else {
			log.info("[B017] 新しい候補はありませんでした。files={}", keys.size());
		}

		for (String key : keys) {
			String dst = DONE_PREFIX + key.substring(PENDING_PREFIX.length());
			s3Operator.copy(bucket, key, bucket, dst);
			s3Operator.delete(bucket, key);
		}
	}

	/** 正規化チーム名 → 「国: リーグ」 */
	private Map<String, String> buildTeamIndex() {
		Map<String, String> index = new LinkedHashMap<>();
		List<CountryLeagueMasterEntity> rows = countryLeagueMasterBatchRepository.findAllActiveTeams();
		for (CountryLeagueMasterEntity r : rows) {
			if (r.getTeam() == null || r.getTeam().isBlank()) {
				continue;
			}
			index.putIfAbsent(norm(r.getTeam()), r.getCountry() + ": " + r.getLeague());
		}
		log.info("[B017] 登録済みチーム数={}", index.size());
		return index;
	}

	private Map<String, String> loadNotified(String bucket) {
		try {
			if (!s3Operator.existsOnS3(bucket, NOTIFIED_KEY)) {
				return new LinkedHashMap<>();
			}
			String json = s3Operator.downloadTextUtf8(bucket, NOTIFIED_KEY);
			if (json == null || json.isBlank()) {
				return new LinkedHashMap<>();
			}
			return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {
			});
		} catch (Exception e) {
			log.warn("[B017] 通知済みJSONが読めないため空として扱います。e={}", e.toString());
			return new LinkedHashMap<>();
		}
	}

	private String buildBody(List<Candidate> list) {
		StringBuilder sb = new StringBuilder();
		sb.append("管理者 様\n\n");
		sb.append("以下のリーグは現在スクレイピング対象外ですが、対象リーグのチームが出場しています。\n");
		sb.append("必要であれば管理画面「スクレイピング対象設定」で対象に追加してください。\n");
		sb.append("（同じリーグは2回通知しません）\n\n");
		int n = 0;
		for (Candidate c : list) {
			if (n >= MAX_LIST) {
				sb.append("…ほか ").append(list.size() - MAX_LIST).append(" 件\n");
				break;
			}
			n++;
			sb.append("■ ").append(c.category)
					.append("（試合日: ").append(String.join(", ", c.dates))
					.append(" / ").append(c.matchCount).append("試合）\n");
			Set<String> lines = new LinkedHashSet<>();
			for (Map.Entry<String, String> e : c.matchedTeams.entrySet()) {
				lines.add(e.getKey() + "（" + e.getValue() + "）");
			}
			int i = 0;
			for (String line : lines) {
				if (i >= MAX_TEAMS_PER_LEAGUE) {
					sb.append("    …ほか ").append(lines.size() - MAX_TEAMS_PER_LEAGUE).append(" チーム\n");
					break;
				}
				sb.append("    ・").append(line).append("\n");
				i++;
			}
			sb.append("\n");
		}
		sb.append("--\nbm-stats-real（B017 自動通知）\n");
		return sb.toString();
	}

	/** 比較用の正規化（NFKC・小文字・空白圧縮） */
	static String norm(String s) {
		if (s == null) {
			return "";
		}
		String n = Normalizer.normalize(s, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
		return n.replaceAll("\\s+", " ").trim();
	}
}
