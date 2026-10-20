package dev.web.api.headToHead.bm_w001;

import java.text.Normalizer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.web.repository.bm.HeadToHeadRepository;
import lombok.RequiredArgsConstructor;

/**
 * 対戦成績（同じ 2 チームの過去の対戦）
 *
 * <p>VIEW head_to_head_summary・head_to_head_match（元は surface_overview_match）から、team 視点で返す。
 * ホーム / アウェーを問わず全シーズン・全大会。PK 決着は PK の勝敗で数える（pkWins / pkLosses は内数）。</p>
 * @author shiraishitoshio
 */
@Service
@RequiredArgsConstructor
public class HeadToHeadService {

	/** 直近の対戦の既定件数・上限 */
	public static final int DEFAULT_LIMIT = 10;
	public static final int MAX_LIMIT = 50;

	private final HeadToHeadRepository repository;

	/**
	 * 対戦成績
	 * @throws IllegalArgumentException チーム名が空・同じチームのとき（画面に 400 で返す）
	 */
	@Transactional(readOnly = true)
	public HeadToHeadResponse get(String team, String opponent, Integer limit) {
		String t = trimToNull(team);
		String o = trimToNull(opponent);
		if (t == null || o == null) {
			throw new IllegalArgumentException("team と opponent を指定してください。");
		}
		if (normalize(t).equals(normalize(o))) {
			throw new IllegalArgumentException("team と opponent に同じチームが指定されています。（" + t + "）");
		}
		int l = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);

		HeadToHeadResponse res = new HeadToHeadResponse();
		res.setTeam(t);
		res.setOpponent(o);
		if (repository.fillSummary(t, o, res)) {
			res.setMatches(repository.findMatches(t, o, l));
		}
		return res;
	}

	private static String normalize(String s) {
		return Normalizer.normalize(s, Normalizer.Form.NFKC).trim();
	}

	private static String trimToNull(String s) {
		if (s == null) {
			return null;
		}
		String v = s.trim();
		return v.isEmpty() ? null : v;
	}
}
