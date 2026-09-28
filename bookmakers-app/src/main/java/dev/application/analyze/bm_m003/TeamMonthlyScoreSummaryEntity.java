package dev.application.analyze.bm_m003;

import dev.common.entity.MetaEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * team_monthly_score_summary テーブルに対応するエンティティ。
 *
 * <h2>何を表すクラスか</h2>
 * <p>
 * 1行 = 「国・リーグ・チーム・ホーム/アウェー・年」の組み合わせ。
 * そのチームがその年の各月に（ホーム戦／アウェー戦で）挙げた得点数の累計を、1月〜12月の12列で持つ。
 * </p>
 *
 * <h2>月別件数の並び順</h2>
 * <p>
 * 12か月分の読み書きは {@link #toMonthArray()} / {@link #applyMonths(int[])} に集約している
 * （添字 0 = 1月 … 11 = 12月）。
 * </p>
 *
 * <h2>懸念点</h2>
 * <ul>
 *   <li><b>件数が String 型</b>: DB に数値以外が入っていると 0 として扱われ、累計が失われる。</li>
 *   <li><b>行の一意性</b>: (country, league, team_name, ha, year) に一意制約がないと、
 *       同時実行で同じキーの行が2件できる可能性がある（{@link TeamMonthlyScoreSummaryWriter} 参照）。</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class TeamMonthlyScoreSummaryEntity extends MetaEntity {

	/** 月数 */
	public static final int MONTH_COUNT = 12;

	/**
	 * 通番（主キー）。
	 * 【変更】「&lt;シーズン&gt;-&lt;6桁枝番&gt;」形式（例: 2025-2026-000001）。
	 * シーズンは country_league_season_master.season、枝番はテーブル×シーズンで 1 から振る。
	 * シーズン終了時のデータ削除後も、次シーズンは 000001 から始まる。
	 */
	private String seq;

	/** 国 */
	private String country;

	/** リーグ */
	private String league;

	/** チーム名 */
	private String teamName;

	/** ホームorアウェー（"H" / "A"） */
	private String ha;

	/** 年（yyyy） */
	private String year;

	/** 1月スコア数 */
	private String januaryScoreSumCount;

	/** 2月スコア数 */
	private String februaryScoreSumCount;

	/** 3月スコア数 */
	private String marchScoreSumCount;

	/** 4月スコア数 */
	private String aprilScoreSumCount;

	/** 5月スコア数 */
	private String mayScoreSumCount;

	/** 6月スコア数 */
	private String juneScoreSumCount;

	/** 7月スコア数 */
	private String julyScoreSumCount;

	/** 8月スコア数 */
	private String augustScoreSumCount;

	/** 9月スコア数 */
	private String septemberScoreSumCount;

	/** 10月スコア数 */
	private String octoberScoreSumCount;

	/** 11月スコア数 */
	private String novemberScoreSumCount;

	/** 12月スコア数 */
	private String decemberScoreSumCount;

	/**
	 * 【追加】12か月分の得点数を数値配列で返す（null・数値以外は 0）。
	 * @return 長さ12の配列（添字 0 = 1月）
	 */
	public int[] toMonthArray() {
		String[] raw = {
				januaryScoreSumCount, februaryScoreSumCount, marchScoreSumCount,
				aprilScoreSumCount, mayScoreSumCount, juneScoreSumCount,
				julyScoreSumCount, augustScoreSumCount, septemberScoreSumCount,
				octoberScoreSumCount, novemberScoreSumCount, decemberScoreSumCount
		};
		int[] result = new int[MONTH_COUNT];
		for (int i = 0; i < MONTH_COUNT; i++) {
			result[i] = parseOrZero(raw[i]);
		}
		return result;
	}

	/**
	 * 【追加】12か月分の得点数を各列に設定する（{@link #toMonthArray()} と同じ並び順）。
	 * @param months 長さ12の配列（添字 0 = 1月）
	 * @throws IllegalArgumentException 配列が null または長さが12でない場合
	 */
	public void applyMonths(int[] months) {
		if (months == null || months.length != MONTH_COUNT) {
			throw new IllegalArgumentException("月別配列の長さが不正です: "
					+ (months == null ? "null" : months.length));
		}
		this.januaryScoreSumCount = String.valueOf(months[0]);
		this.februaryScoreSumCount = String.valueOf(months[1]);
		this.marchScoreSumCount = String.valueOf(months[2]);
		this.aprilScoreSumCount = String.valueOf(months[3]);
		this.mayScoreSumCount = String.valueOf(months[4]);
		this.juneScoreSumCount = String.valueOf(months[5]);
		this.julyScoreSumCount = String.valueOf(months[6]);
		this.augustScoreSumCount = String.valueOf(months[7]);
		this.septemberScoreSumCount = String.valueOf(months[8]);
		this.octoberScoreSumCount = String.valueOf(months[9]);
		this.novemberScoreSumCount = String.valueOf(months[10]);
		this.decemberScoreSumCount = String.valueOf(months[11]);
	}

	private static int parseOrZero(String value) {
		if (value == null || value.isBlank()) {
			return 0;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

}
