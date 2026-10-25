package dev.web.api.bm_a025;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.web.repository.master.CountryLeagueSeasonMasterWebRepository.SeasonPeriodRow;

/**
 * static_data の記録日時（record_time）が、country_league_season_master のシーズン期間に入っているかを判定する。
 *
 * <p>判定の考え方（2026-10-24 追加）</p>
 * <ul>
 *   <li>record_time は日本時間の壁時計（timestamp without time zone。RecordTimeConvertUtil 参照）なので、
 *       その日付をそのまま日本時間の日付として使う。シーズンの開始・終了（timestamptz）も日本時間の日付にして比べる。</li>
 *   <li>日付で比べる。シーズン終了は「2026-12-06 00:00:00+09」のように 0 時で入っているので、
 *       日時のまま比べると最終日の試合が外れてしまうため。</li>
 *   <li>前後に余裕を持たせる: 開始の {@value #START_GRACE_DAYS} 日前 〜 終了の {@value #END_GRACE_DAYS} 日後 まで期間内。
 *       最終節が夜遅くに終わって日付をまたいだ・取得が遅れた・延期試合、のデータも入るようにする。</li>
 *   <li>1 グループ（dataCategory × ホーム × アウェー）の記録日の範囲 [最初, 最後] が、期間と 1 日でも重なれば期間内。</li>
 *   <li>手動取得（add_manual_flg = '1'）の行は、終了済が取れなかった試合を別日に取り直したもので、record_time は
 *       取り直した日（試合の日より後）。そのため試合中に記録した行があれば、そちらだけで判定する。
 *       手動取得の行しか無いときは「試合の日 ≦ 取り直した日」として判定する:
 *       取り直した日が期間内なら期間内 / 開始より前なら（試合はもっと前なので）期間外 /
 *       終了の余裕より後なら試合の日が分からないので期間不明（フォーマットからは外さない）。</li>
 *   <li>終了が未設定（null）なら、開始以降はずっと期間内。開始が未設定・シーズンが未登録・国リーグが分からないときは
 *       判定できないので「期間不明」（フォーマットからは外さない）。</li>
 * </ul>
 * @author shiraishitoshio
 */
public class SeasonPeriodChecker {

	public static final String IN = "期間内";
	public static final String OUT = "期間外";
	public static final String UNKNOWN = "期間不明";

	/** シーズン開始の何日前から期間内にするか */
	public static final int START_GRACE_DAYS = 1;

	/** シーズン終了の何日後まで期間内にするか */
	public static final int END_GRACE_DAYS = 7;

	/** squash("国: リーグ") → シーズン（同じ国リーグに複数行あればどれかに入れば期間内） */
	private final Map<String, List<SeasonPeriodRow>> seasons = new HashMap<>();

	public SeasonPeriodChecker(Collection<SeasonPeriodRow> rows) {
		for (SeasonPeriodRow r : rows) {
			if (r.getCountry() == null || r.getLeague() == null) {
				continue;
			}
			String key = LeagueCandidateResolver.squash(r.getCountry().trim() + ":" + r.getLeague().trim());
			seasons.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
		}
	}

	/** 判定結果 */
	public static class Result {
		public final String check;
		/** 画面・CSV の参考用（例: 期間内（2026-02-14〜2026-12-06）、期間不明（シーズン未登録）） */
		public final String label;

		Result(String check, String label) {
			this.check = check;
			this.label = label;
		}
	}

	/** 手動取得の行を考えない判定（試合中に記録した行だけ） */
	public Result check(Collection<String> countryLeagues, LocalDate firstRecord, LocalDate lastRecord) {
		return check(countryLeagues, firstRecord, lastRecord, null, null);
	}

	/**
	 * @param countryLeagues 対象の「国: リーグ」（候補が複数ならどれかのシーズンに入れば期間内）
	 * @param firstRecord    試合中に記録した行の最初の記録日（日本時間）。無ければ null
	 * @param lastRecord     同じく最後の記録日
	 * @param firstManual    手動取得（add_manual_flg = '1'）の行の最初の記録日（取り直した日）。無ければ null
	 * @param lastManual     同じく最後の記録日
	 */
	public Result check(Collection<String> countryLeagues, LocalDate firstRecord, LocalDate lastRecord,
			LocalDate firstManual, LocalDate lastManual) {
		if (countryLeagues == null || countryLeagues.isEmpty()) {
			return new Result(UNKNOWN, UNKNOWN + "（国リーグが分からない）");
		}
		boolean live = firstRecord != null && lastRecord != null;
		boolean manual = firstManual != null && lastManual != null;
		if (!live && !manual) {
			return new Result(UNKNOWN, UNKNOWN + "（記録日時なし）");
		}
		boolean anySeason = false;
		boolean anyJudgeable = false;
		boolean manualAfterEnd = false;
		String outLabel = null;
		String afterLabel = null;
		for (String cl : countryLeagues) {
			List<SeasonPeriodRow> list = seasons.get(LeagueCandidateResolver.squash(cl));
			if (list == null) {
				continue;
			}
			anySeason = true;
			for (SeasonPeriodRow s : list) {
				if (s.getStartDate() == null) {
					continue; // 開始が無いと判定できない
				}
				anyJudgeable = true;
				LocalDate from = s.getStartDate().minusDays(START_GRACE_DAYS);
				LocalDate to = s.getEndDate() == null ? null : s.getEndDate().plusDays(END_GRACE_DAYS);
				String period = s.getStartDate() + "〜" + (s.getEndDate() == null ? "" : s.getEndDate().toString());
				if (live) {
					// 試合中に記録した行だけで判定（手動取得の行は取り直した日なので使わない）
					if (overlaps(firstRecord, lastRecord, from, to)) {
						return new Result(IN, IN + "（" + period + "）");
					}
				} else {
					// 手動取得の行だけ: 試合の日 ≦ 取り直した日
					if (overlaps(firstManual, lastManual, from, to)) {
						return new Result(IN, IN + "（" + period + "・手動取得の取得日で判定）");
					}
					if (to != null && firstManual.isAfter(to)) {
						// 取り直したのがシーズン終了の後: 試合がシーズン中だったかは分からない
						manualAfterEnd = true;
						if (afterLabel == null) {
							afterLabel = UNKNOWN + "（手動取得のみ・取得日がシーズン終了後。" + period + "）";
						}
						continue;
					}
				}
				if (outLabel == null) {
					outLabel = OUT + "（" + period + "）";
				}
			}
		}
		if (!anySeason) {
			return new Result(UNKNOWN, UNKNOWN + "（シーズン未登録）");
		}
		if (!anyJudgeable) {
			return new Result(UNKNOWN, UNKNOWN + "（開始日が未設定）");
		}
		if (manualAfterEnd) {
			return new Result(UNKNOWN, afterLabel);
		}
		return new Result(OUT, outLabel);
	}

	/** [first, last] と [from, to]（to が null なら上限なし）が 1 日でも重なるか */
	private static boolean overlaps(LocalDate first, LocalDate last, LocalDate from, LocalDate to) {
		return !last.isBefore(from) && (to == null || !first.isAfter(to));
	}
}
