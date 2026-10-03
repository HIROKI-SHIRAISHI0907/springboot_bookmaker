package dev.web.api.bm_a099;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.repository.bm.AnalyzeErrorMatchWebRepository;
import lombok.RequiredArgsConstructor;

/**
 * AnalyzeErrorService用サービス（統計処理で登録できなかった試合の一覧・対応状況の更新）
 *
 * <ul>
 *   <li>一覧は OFFSET ページング（1ページ最大 {@link #MAX_LIMIT} 件）。</li>
 *   <li>エラー種別の表示名はバッチ側の AnalyzeErrorType と同じ文言（web とバッチでモジュールが分かれているため、ここに持つ）。
 *       種別を足したら両方直すこと。</li>
 *   <li>対応済みにしても、同じエラーがバッチで再発すると未対応に戻る（バッチ側の仕様）。</li>
 *   <li>明細ダウンロードは一覧と同じ条件の CSV（UTF-8・BOM 付きで Excel でそのまま開ける）。最大 {@link #MAX_EXPORT} 件。</li>
 * </ul>
 * @author shiraishitoshio
 *
 */
@Service
@RequiredArgsConstructor
public class AnalyzeErrorService {

	/** 1ページの件数（既定） */
	public static final int DEFAULT_LIMIT = 20;

	/** 1ページの件数（上限） */
	public static final int MAX_LIMIT = 100;

	/** まとめて更新できる件数の上限 */
	public static final int MAX_BATCH = 200;

	/** ダウンロードできる件数の上限 */
	public static final int MAX_EXPORT = 10000;

	/** 画面・CSV の時刻は JST */
	private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

	private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

	/** CSV の見出し（{@link #toCsvRow} の並びと合わせること） */
	private static final List<String> CSV_HEADER = List.of(
			"seq", "状態", "BM", "エラー種別", "エラー種別（表示名）", "国", "リーグ", "キー",
			"ホーム", "アウェー", "原因の項目", "原因の値", "マッチID", "シーズン",
			"エラー内容", "例外クラス", "補足", "発生回数", "初回発生（JST）", "最終発生（JST）",
			"対応日時（JST）", "対応者", "メモ", "スタックトレース");

	/** 対応者の既定値 */
	private static final String DEFAULT_RESOLVED_BY = "ADMIN";

	/** エラー種別の表示名（バッチの AnalyzeErrorType と同じ） */
	private static final Map<String, String> ERROR_TYPE_LABELS = Map.of(
			"SEASON_RESOLVER_MISSING", "シーズン取得処理がありません",
			"SEASON_NOT_FOUND", "シーズンが見つかりません",
			"SEASON_RESOLVE_FAILED", "シーズン取得でエラー",
			"INVALID_CATEGORY", "国・リーグを取得できません",
			"MISSING_VALUE", "必須項目が空です",
			"INVALID_VALUE", "値を読み取れません",
			"UNEXPECTED", "予期しないエラー");

	private final AnalyzeErrorMatchWebRepository repo;

	/**
	 * 一覧
	 */
	@Transactional(readOnly = true)
	public AnalyzeErrorListResponse search(AnalyzeErrorSearchCondition cond, Integer offset, Integer limit) {
		int o = (offset == null || offset < 0) ? 0 : offset;
		int l = (limit == null || limit <= 0) ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);

		int total = repo.count(cond);
		List<AnalyzeErrorDTO> items = (o < total) ? repo.search(cond, o, l) : new ArrayList<>();
		items.forEach(i -> i.setErrorTypeLabel(labelOf(i.getErrorType())));
		return new AnalyzeErrorListResponse(o, l, total, items);
	}

	/**
	 * 詳細（スタックトレース付き。無ければ null）
	 */
	@Transactional(readOnly = true)
	public AnalyzeErrorDTO findBySeq(String seq) {
		AnalyzeErrorDTO dto = repo.findBySeq(seq);
		if (dto != null) {
			dto.setErrorTypeLabel(labelOf(dto.getErrorType()));
		}
		return dto;
	}

	/**
	 * 集計（BM × 種別 × 国・リーグ × 項目）
	 */
	@Transactional(readOnly = true)
	public List<AnalyzeErrorSummaryDTO> findSummary() {
		List<AnalyzeErrorSummaryDTO> list = repo.findSummary();
		list.forEach(s -> s.setErrorTypeLabel(labelOf(s.getErrorType())));
		return list;
	}

	/**
	 * 対応状況・メモの更新（1件）
	 */
	@Transactional
	public AnalyzeErrorResponse updateResolved(String seq, Boolean resolvedFlg, String resolvedBy, String note) {
		if (seq == null || seq.isBlank()) {
			return new AnalyzeErrorResponse("400", "seq を指定してください", null);
		}
		if (resolvedFlg == null && note == null) {
			return new AnalyzeErrorResponse("400", "resolvedFlg か note を指定してください", null);
		}
		String by = (resolvedBy == null || resolvedBy.isBlank()) ? DEFAULT_RESOLVED_BY : resolvedBy.trim();
		int result = repo.updateResolved(seq.trim(), resolvedFlg, by, note);
		if (result == 0) {
			return new AnalyzeErrorResponse("404", "対象のエラーがありません: seq=" + seq, null);
		}
		return new AnalyzeErrorResponse("200", "OK", findBySeq(seq.trim()));
	}

	/**
	 * 対応状況・メモの更新（まとめて）。1件ごとに更新し、失敗があっても残りは続ける。
	 */
	@Transactional
	public AnalyzeErrorBatchResponse updateResolvedBatch(AnalyzeErrorResolveRequest req) {
		Set<String> seqs = new LinkedHashSet<>();
		if (req != null && req.getSeqs() != null) {
			for (String s : req.getSeqs()) {
				if (s != null && !s.isBlank()) {
					seqs.add(s.trim());
				}
			}
		}
		if (seqs.isEmpty() || seqs.size() > MAX_BATCH) {
			return new AnalyzeErrorBatchResponse("400", seqs.size(), 0, seqs.size(), List.of(
					new AnalyzeErrorBatchResponse.ItemResult(null, "400",
							seqs.isEmpty() ? "seqs を指定してください" : "一度に更新できるのは " + MAX_BATCH + " 件までです")));
		}

		int success = 0;
		List<AnalyzeErrorBatchResponse.ItemResult> results = new ArrayList<>();
		for (String seq : seqs) {
			AnalyzeErrorResponse r = updateResolved(seq, req.getResolvedFlg(), req.getResolvedBy(), req.getNote());
			if ("200".equals(r.getResponseCode())) {
				success++;
			}
			results.add(new AnalyzeErrorBatchResponse.ItemResult(seq, r.getResponseCode(), r.getMessage()));
		}
		int failed = seqs.size() - success;
		return new AnalyzeErrorBatchResponse(failed == 0 ? "200" : "207", seqs.size(), success, failed, results);
	}

	/**
	 * 明細ダウンロード（CSV）。一覧と同じ条件（seqs を指定すればその行だけ）で、スタックトレースも含める。
	 */
	@Transactional(readOnly = true)
	public AnalyzeErrorExportResult exportCsv(AnalyzeErrorSearchCondition cond) {
		int total = repo.count(cond);
		List<AnalyzeErrorDTO> items = (total == 0) ? new ArrayList<>() : repo.searchForExport(cond, MAX_EXPORT);

		StringBuilder sb = new StringBuilder();
		appendRow(sb, CSV_HEADER);
		for (AnalyzeErrorDTO dto : items) {
			appendRow(sb, toCsvRow(dto));
		}

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		// BOM（Excel が UTF-8 と判断できるように）
		out.write(0xEF);
		out.write(0xBB);
		out.write(0xBF);
		byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
		out.write(bytes, 0, bytes.length);

		return new AnalyzeErrorExportResult(fileNameOf(cond), out.toByteArray(), total, items.size());
	}

	/** ファイル名: 1件指定なら analyze_error_<seq>.csv、それ以外は analyze_error_<JST日時>.csv */
	private static String fileNameOf(AnalyzeErrorSearchCondition cond) {
		if (cond != null && cond.getSeqs() != null && cond.getSeqs().size() == 1) {
			return "analyze_error_" + cond.getSeqs().get(0).replaceAll("[^0-9A-Za-z_-]", "_") + ".csv";
		}
		return "analyze_error_" + ZonedDateTime.now(JST).format(FILE_TIME) + ".csv";
	}

	private static List<String> toCsvRow(AnalyzeErrorDTO d) {
		List<String> row = new ArrayList<>();
		row.add(d.getSeq());
		row.add(statusLabelOf(d.getResolvedFlg(), d.getResolvedBy()));
		row.add(d.getBmNumber());
		row.add(d.getErrorType());
		row.add(labelOf(d.getErrorType()));
		row.add(d.getCountry());
		row.add(d.getLeague());
		row.add(d.getDataCategory());
		row.add(d.getHomeTeamName());
		row.add(d.getAwayTeamName());
		row.add(d.getErrorField());
		row.add(d.getErrorValue());
		row.add(d.getMatchId());
		row.add(d.getSeason());
		row.add(d.getErrorMessage());
		row.add(d.getExceptionClass());
		row.add(d.getDetail());
		row.add(d.getOccurredCount() == null ? null : String.valueOf(d.getOccurredCount()));
		row.add(toJst(d.getFirstOccurredAt()));
		row.add(toJst(d.getLastOccurredAt()));
		row.add(toJst(d.getResolvedAt()));
		row.add(d.getResolvedBy());
		row.add(d.getNote());
		row.add(d.getStackTrace());
		return row;
	}

	/** 状態の表示名（画面の getResolvedStatus と同じ） */
	private static String statusLabelOf(Boolean resolvedFlg, String resolvedBy) {
		if (resolvedFlg == null || !resolvedFlg) {
			return "未対応";
		}
		return "AUTO".equals(resolvedBy) ? "自動で解決" : "対応済み";
	}

	/** ISO-8601（UTC）→ JST の「yyyy-MM-dd HH:mm:ss」。読めなければそのまま */
	private static String toJst(String iso) {
		if (iso == null || iso.isBlank()) {
			return null;
		}
		try {
			return Instant.parse(iso).atZone(JST).format(CSV_TIME);
		} catch (DateTimeParseException e) {
			return iso;
		}
	}

	private static void appendRow(StringBuilder sb, List<String> cols) {
		for (int i = 0; i < cols.size(); i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(csvCell(cols.get(i)));
		}
		sb.append("\r\n");
	}

	/**
	 * CSV の1セル。カンマ・ダブルクォート・改行を含むときは "" で囲む。
	 * = + @ で始まる値は Excel で数式として実行されないように先頭に ' を付ける（- は負の数があるので対象外）。
	 */
	private static String csvCell(String v) {
		if (v == null) {
			return "";
		}
		String s = v;
		if (!s.isEmpty() && "=+@\t\r".indexOf(s.charAt(0)) >= 0) {
			s = "'" + s;
		}
		if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
			return "\"" + s.replace("\"", "\"\"") + "\"";
		}
		return s;
	}

	/** エラー種別の表示名（知らない種別はそのまま） */
	public static String labelOf(String errorType) {
		if (errorType == null) {
			return null;
		}
		return ERROR_TYPE_LABELS.getOrDefault(errorType, errorType);
	}
}
