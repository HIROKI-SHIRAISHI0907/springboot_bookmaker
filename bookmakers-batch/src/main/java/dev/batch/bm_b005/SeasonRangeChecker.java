package dev.batch.bm_b005;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import dev.batch.repository.master.SeasonRangeBatchRepository;
import dev.common.entity.FutureEntity;

/**
 * 試合予定がシーズン期間の外かを判定する（B005 の future_master 登録。2026-11-21 追加）
 *
 * <ul>
 *   <li>カテゴリ「国: リーグ …」の 国 と、リーグ（完全一致、または「リーグ - …」。長いもの優先）で
 *       country_league_season_master（del_flg = '0'）の行を探す。</li>
 *   <li>行が無い（カップ戦など）・開始日か終了日が無い・試合開始日時が読めない → 期間外としない（登録する）。</li>
 *   <li>開始日〜終了日に入る行が1つでもあれば期間内。</li>
 * </ul>
 * 2026-11-21: シーズンが終わっても終了日は消さず season_end_flg を立てるようにしたので、終了日が無い＝判断できない扱いにした。
 * 例: コロンビア プリメーラ A（クラウスーラの終了日の後）の「クラウスーラ: クアルドラングラル」は登録しない。
 * 画面（web FuturesRepository の FUTURE_DATE_WHERE）と同じ考え方。
 */
@Component
public class SeasonRangeChecker {

	@Value("${b005.skip-out-of-season:true}")
	private boolean enabled;

	@Autowired
	private SeasonRangeBatchRepository repository;

	/** 1回の登録処理の間だけ使う判定（リーグの一覧をキャッシュする） */
	public Checker newChecker() {
		return new Checker();
	}

	public final class Checker {
		private final Map<String, List<String>> leaguesByCountry = new HashMap<>();

		/** シーズン期間の外なら、その理由（国: リーグ）。期間内・判断できないときは null */
		public String outOfSeason(FutureEntity e) {
			if (!enabled || e == null || isBlank(e.getGameTeamCategory()) || isBlank(e.getFutureTime())) {
				return null;
			}
			String cat = e.getGameTeamCategory().trim();
			int i = cat.indexOf(':');
			if (i <= 0) {
				return null;
			}
			String country = cat.substring(0, i).trim();
			String rest = cat.substring(i + 1).trim();
			List<String> leagues = leaguesByCountry.computeIfAbsent(country, c -> {
				List<String> l = new ArrayList<>(repository.findLeagues(c));
				l.sort(Comparator.comparingInt(String::length).reversed());
				return l;
			});
			for (String league : leagues) {
				if (league == null || league.isBlank() || !rest.startsWith(league)) {
					continue;
				}
				String tail = rest.substring(league.length());
				if (!tail.isEmpty() && !tail.startsWith(" -")) {
					continue;
				}
				String ft = e.getFutureTime().trim();
				if (!ft.matches("^\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}(:\\d{2})?(\\.\\d+)?$")) {
					return null;   // 日時の形が想定外なら判定しない（登録側のエラーチェックに任せる）
				}
				List<String> rows;
				try {
					rows = repository.checkRange(country, league, ft.replace('T', ' '));
				} catch (RuntimeException ex) {
					return null;   // 判定できなければ登録する
				}
				if (rows.isEmpty() || rows.contains("IN") || !rows.contains("OUT")) {
					return null;
				}
				return country + ": " + league;
			}
			return null;
		}
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}
}
