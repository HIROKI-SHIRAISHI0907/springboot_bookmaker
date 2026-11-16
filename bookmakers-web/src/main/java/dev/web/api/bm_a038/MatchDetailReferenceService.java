package dev.web.api.bm_a038;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.stereotype.Service;

import dev.web.repository.bm.MatchDetailReferenceRepository;
import lombok.RequiredArgsConstructor;

/**
 * 統計詳細データ参照（BM_A038。2026-11 追加）
 * <ul>
 *   <li>{@link #search}: match_detail_match を キーワード（試合ID・カテゴリ・チーム名）・試合日の範囲・取得エラーありで検索する。</li>
 * </ul>
 * 試合ごとの中身は既存の GET /v1/api/match-detail/extra?matchId= で取る（画面のモーダル）。
 * @author shiraishitoshio
 */
@Service
@RequiredArgsConstructor
public class MatchDetailReferenceService {

	/** 1 ページの件数の上限 */
	public static final int MAX_LIMIT = 500;

	/** 1 ページの件数の既定 */
	public static final int DEFAULT_LIMIT = 100;

	private final MatchDetailReferenceRepository repository;

	/**
	 * 検索
	 * @throws IllegalArgumentException 日付の形式が正しくないとき（画面に 400 で返す）
	 */
	public MatchDetailReferenceResponse search(String keyword, String dateFrom, String dateTo, Boolean errorsOnly,
			Integer offset, Integer limit) {
		String kw = trim(keyword);
		String from = date(dateFrom, "試合日（から）");
		String to = date(dateTo, "試合日（まで）");
		if (from != null && to != null && from.compareTo(to) > 0) {
			throw new IllegalArgumentException("試合日の範囲が逆です（" + from + " 〜 " + to + "）");
		}
		boolean err = Boolean.TRUE.equals(errorsOnly);
		int off = offset == null || offset < 0 ? 0 : offset;
		int lim = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);

		int total = repository.count(kw, from, to, err);
		List<MatchDetailReferenceDTO> items = total == 0 ? List.of() : repository.find(kw, from, to, err, off, lim);
		return new MatchDetailReferenceResponse(total, off, lim, items);
	}

	private static String date(String s, String label) {
		String v = trim(s);
		if (v == null) {
			return null;
		}
		try {
			return LocalDate.parse(v).toString();
		} catch (DateTimeParseException e) {
			throw new IllegalArgumentException(label + "は yyyy-MM-dd で入力してください（" + v + "）");
		}
	}

	private static String trim(String s) {
		if (s == null) {
			return null;
		}
		String v = s.trim();
		return v.isEmpty() ? null : v;
	}
}
