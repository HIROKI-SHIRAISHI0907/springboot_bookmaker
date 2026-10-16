package dev.web.api.bm_a030;

import java.text.Normalizer;
import java.time.Instant;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.common.config.PathConfig;
import dev.common.s3.S3Operator;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * S3 の延期・遅延情報（delay_postpone_＜日付＞.json / delay_postpone_＜日付＞_N.json）を読む
 *
 * <ul>
 *   <li>S3 へのアクセスは共通の {@link S3Operator} を使う（S3Client は DI されたもの）。
 *       一覧は {@link S3Operator#listObjectsByPrefixSortedByLastModifiedAsc} で「delay_postpone_＜日付＞」に絞って取る。</li>
 *   <li>バケット直下だけを見る（json/ フォルダの中は対象外）。</li>
 *   <li>指定日と前日のファイルは全部読む（深夜の試合は前日のファイルにしか無いことがある）。同じ試合が複数ファイルにあれば、
 *       ファイルの generated_at（作成日時）が新しいものの値を使う（読めなければ S3 の最終更新日時）。
 *       （連番の大小と作成順は一致しない。例: _2 が 06:02、番号なしが 12:01。
 *       また試合が再開するとバッチが中断・延期の試合をファイルから削除して書き直すので、最終更新日時は作成順にならない）</li>
 *   <li>試合は mid（URL の ?mid=）で突き合わせる。mid で見つからなければ home / away（NFKC・空白除去）で突き合わせる。</li>
 *   <li>読み込みに失敗しても画面は止めない（ログを出して「延期情報なし」として扱う）。</li>
 * </ul>
 * @author shiraishitoshio
 *
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DelayPostponeS3Reader {

	/** JSON の中で試合が入っている配列（後ろのものほど優先） */
	private static final String[] MATCH_ARRAYS = {
			"scraped_flagged_matches",
			"carry_over_postponed_games",
			"matched_delay_postpone_games",
	};

	private final ObjectMapper objectMapper;

	private final PathConfig config;

	private final S3Operator s3Operator;

	/**
	 * 指定日の延期・遅延情報
	 */
	public DelayPostponeIndex load(LocalDate date) {
		DelayPostponeIndex index = new DelayPostponeIndex();
		String bucket = config.getS3BucketsOutputsDelayPostpone();

		// 前日分＋当日分を読む。スクレイピングは前日の実行で翌朝 5:00 までの試合も拾う（ecs_slots の計算範囲）ため、
		// 深夜の試合は前日のファイルにしか無いことがある。
		List<S3Object> targets = new ArrayList<>();
		for (LocalDate d : List.of(date.minusDays(1), date)) {
			String prefix = "delay_postpone_" + d;
			// バケット直下の delay_postpone_yyyy-MM-dd.json / delay_postpone_yyyy-MM-dd_N.json
			Pattern keyPattern = Pattern.compile("^" + Pattern.quote(prefix) + "(_\\d+)?\\.json$");
			try {
				targets.addAll(s3Operator.listObjectsByPrefixSortedByLastModifiedAsc(bucket, prefix, keyPattern));
			} catch (Exception e) {
				log.warn("延期・遅延情報の一覧取得に失敗しました（s3://{}/{}*）", bucket, prefix, e);
			}
		}
		// 中身を先に読んでから、新しい順の判定をする。
		// 試合が再開するとバッチがファイルから中断・延期の試合を削除して書き直す（S3 の最終更新日時が変わる）ため、
		// ファイルの中の generated_at（作成日時）の昇順で読む。generated_at が読めなければ最終更新日時を使う。
		List<LoadedFile> loaded = new ArrayList<>();
		for (S3Object o : targets) {
			try {
				JsonNode root = objectMapper.readTree(s3Operator.downloadTextUtf8(bucket, o.key()));
				loaded.add(new LoadedFile(o.key(), root, generatedAt(root, o)));
			} catch (Exception e) {
				log.warn("延期・遅延情報の読み込みに失敗しました（s3://{}/{}）", bucket, o.key(), e);
			}
		}
		loaded.sort(Comparator.comparing((LoadedFile f) -> f.generatedAt).thenComparing(f -> f.key));
		for (LoadedFile f : loaded) {
			readJson(f.root, f.key, f.generatedAt, index);
		}
		// 反映されないときの切り分け用（読んだファイルと拾えた試合の数）
		log.info("延期・遅延情報: bucket={}, date={}, files={}, matches={}",
				bucket, date, targets.stream().map(S3Object::key).toList(), index.size());
		return index;
	}

	/** 1ファイル分を index に入れる（同じ試合は後から読んだものが勝つ） */
	void readJson(String json, String sourceKey, DelayPostponeIndex index) throws Exception {
		readJson(objectMapper.readTree(json), sourceKey, null, index);
	}

	/** 1ファイル分を index に入れる（同じ試合は後から読んだものが勝つ） */
	void readJson(JsonNode root, String sourceKey, Instant fileTime, DelayPostponeIndex index) {
		for (String arrayName : MATCH_ARRAYS) {
			JsonNode arr = root.path(arrayName);
			if (!arr.isArray()) {
				continue;
			}
			for (JsonNode n : arr) {
				String statusType = text(n, "status_type");
				if (statusType == null) {
					continue;
				}
				DelayPostponeInfo info = new DelayPostponeInfo();
				info.setStatusType(statusType);
				String statusText = text(n, "status_text");
				info.setStatusText(statusText != null ? statusText : text(n, "raw_status_text"));
				info.setMid(text(n, "mid"));
				info.setHome(text(n, "home"));
				info.setAway(text(n, "away"));
				info.setSourceKey(sourceKey);
				info.setStart(toLocal(text(n, "start")));
				String detected = toLocal(text(n, "scraped_at"));
				JsonNode scraped = n.get("scraped");
				if (detected == null && scraped != null) {
					detected = toLocal(text(scraped, "scraped_at"));
				}
				if (detected == null && fileTime != null && !Instant.MAX.equals(fileTime)) {
					detected = fileTime.atZone(JST).toLocalDateTime().format(LOCAL_FMT);
				}
				info.setDetectedAt(detected);
				index.put(info);
			}
		}
	}

	/** ファイルの作成日時（generated_at）。読めなければ S3 の最終更新日時、それも無ければ最後扱い */
	static Instant generatedAt(JsonNode root, S3Object o) {
		String g = text(root, "generated_at");
		if (g != null) {
			try {
				return OffsetDateTime.parse(g).toInstant();
			} catch (DateTimeParseException e) {
				// 最終更新日時を使う
			}
		}
		return o.lastModified() == null ? Instant.MAX : o.lastModified();
	}

	/** 読み込んだ1ファイル */
	private static final class LoadedFile {
		final String key;
		final JsonNode root;
		final Instant generatedAt;

		LoadedFile(String key, JsonNode root, Instant generatedAt) {
			this.key = key;
			this.root = root;
			this.generatedAt = generatedAt;
		}
	}

	private static String text(JsonNode n, String field) {
		JsonNode v = n.get(field);
		if (v == null || v.isNull()) {
			return null;
		}
		String s = v.asText().trim();
		return s.isEmpty() ? null : s;
	}

	private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

	private static final DateTimeFormatter LOCAL_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	/** 予定の試合開始日時と延期等の情報の基準時刻がこれ以上離れていたら、その予定の情報として使わない */
	static final Duration MAX_GAP = Duration.ofDays(1);

	/** ISO（+09:00）→ "yyyy-MM-dd HH:mm:ss"（日本時間）。読めなければ null */
	static String toLocal(String iso) {
		if (iso == null) {
			return null;
		}
		try {
			return OffsetDateTime.parse(iso).atZoneSameInstant(JST).toLocalDateTime().format(LOCAL_FMT);
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	/**
	 * 延期等の情報がこの予定（試合開始日時）のものとして使えるか。
	 * 基準時刻（元の試合開始日時、無ければ検知した日時）と試合開始日時が1日を超えて離れていれば使わない
	 * （延期の試合が別の日に変わり、delay_postpone に古い延期が残っている場合に、新しい日の予定を延期と表示しないため）。
	 * どちらかが分からなければ使う。
	 */
	static boolean appliesTo(DelayPostponeInfo info, String futureTimeLocal) {
		if (info == null) {
			return false;
		}
		String base = info.getStart() != null ? info.getStart() : info.getDetectedAt();
		if (base == null || futureTimeLocal == null) {
			return true;
		}
		try {
			LocalDateTime b = LocalDateTime.parse(base, LOCAL_FMT);
			LocalDateTime f = LocalDateTime.parse(futureTimeLocal.trim(), LOCAL_FMT);
			return Duration.between(b, f).abs().compareTo(MAX_GAP) <= 0;
		} catch (DateTimeParseException e) {
			return true;
		}
	}

	static String teamKey(String home, String away) {
		return squash(home) + "\u0000" + squash(away);
	}

	static String squash(String s) {
		return s == null ? "" : Normalizer.normalize(s, Normalizer.Form.NFKC).replaceAll("\\s+", "");
	}

	/** 延期・遅延の1試合分 */
	@Data
	public static class DelayPostponeInfo {
		/** 延期 / 遅延 */
		private String statusType;
		/** 画面に出ていた文言 */
		private String statusText;
		private String mid;
		private String home;
		private String away;
		/** 読み込んだ S3 キー */
		private String sourceKey;
		/** 元の試合開始日時（"yyyy-MM-dd HH:mm:ss"・日本時間。無ければ null） */
		private String start;
		/** 検知した日時（"yyyy-MM-dd HH:mm:ss"・日本時間。無ければ null） */
		private String detectedAt;
	}

	/** mid / home・away で引ける索引 */
	public static class DelayPostponeIndex {
		private final Map<String, DelayPostponeInfo> byMid = new HashMap<>();
		private final Map<String, DelayPostponeInfo> byTeams = new HashMap<>();

		void put(DelayPostponeInfo info) {
			if (info.getMid() != null) {
				byMid.put(info.getMid(), info);
			}
			if (info.getHome() != null && info.getAway() != null) {
				byTeams.put(teamKey(info.getHome(), info.getAway()), info);
			}
		}

		/** mid を優先し、無ければ home / away で探す。見つからなければ null */
		public DelayPostponeInfo find(String mid, String home, String away) {
			if (mid != null && !mid.isBlank()) {
				DelayPostponeInfo i = byMid.get(mid.trim());
				if (i != null) {
					return i;
				}
			}
			if (home == null || away == null) {
				return null;
			}
			return byTeams.get(teamKey(home, away));
		}

		public boolean isEmpty() {
			return byMid.isEmpty() && byTeams.isEmpty();
		}

		/** 拾えた試合の数（mid・チーム名のどちらかで引けるもの） */
		public int size() {
			return Math.max(byMid.size(), byTeams.size());
		}
	}
}
