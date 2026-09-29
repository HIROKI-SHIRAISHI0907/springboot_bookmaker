package dev.application.analyze.bm_m031;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.domain.repository.master.CountryLeagueSeasonMasterRepository;
import dev.common.constant.MessageCdConst;
import dev.common.entity.CountryLeagueSeasonMasterEntity;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M031 総ラウンド数（country_league_season_master.round）の保持。
 *
 * <h2>何をするクラスか</h2>
 * <p>
 * 国・リーグ・シーズンごとの総ラウンド数を読み込み、序盤/中盤/終盤（{@link Phase}）の判定に使う。
 * 呼び出し側（SurfaceOverviewStat）は集計の開始時に {@link #init()} を呼ぶこと。
 * </p>
 *
 * <h2>修正履歴</h2>
 * <ul>
 *   <li>league の null チェックより前に正規化（league.contains）していて NPE になっていたのを修正。</li>
 *   <li>valid_flg = '0' で絞るのをやめた（主要リーグが valid_flg = '1' のため、序盤/中盤/終盤が全部スキップされていた）。</li>
 *   <li>シーズンごとに持つようにした（シーズンによって総ラウンド数が違う場合・過去シーズンの試合を後から入れる場合に対応）。</li>
 *   <li>1回の読み込みが終わってから差し替える（読み込み途中の空の Map を他のスレッドが見ない）。</li>
 * </ul>
 *
 * <h2>懸念点</h2>
 * <ul>
 *   <li><b>リーグ名の表記</b>: マスタと試合データの表記が違うリーグは {@link #normalizeLeague} で合わせている
 *       （セリエA・プレミアリーグ・" - " 以降の除去）。他にも違うリーグがあると総ラウンド数が取れず、phase が null になる。</li>
 *   <li><b>シーズンが無い場合</b>: そのシーズンの行が無ければ、同じ国・リーグの最新シーズン（season_year の最大値）の値を使う。</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@Component
public class BmM031SurfaceOverviewBean {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = BmM031SurfaceOverviewBean.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = BmM031SurfaceOverviewBean.class.getName();

	/** イタリア／セリエA */
	private static final String ITALY_SERIEA = "イタリア／セリエ A";

	/** イングランド／プレミアリーグ */
	private static final String ENGLAND_PREMIER = "イングランド／プレミアリーグ";

	/** CountryLeagueSeasonMasterRepositoryレポジトリクラス */
	@Autowired
	private CountryLeagueSeasonMasterRepository countryLeagueSeasonMasterRepository;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent loggerComponent;

	/** 国|リーグ|シーズン → 総ラウンド数 */
	private volatile Map<String, Integer> roundBySeason = new HashMap<>();

	/** 国|リーグ → 最新シーズンの総ラウンド数 */
	private volatile Map<String, Integer> latestRound = new HashMap<>();

	/**
	 * マスタを読み込む。
	 */
	public synchronized void init() {
		final String METHOD_NAME = "init";
		Map<String, Integer> bySeason = new HashMap<>();
		Map<String, Integer> latest = new HashMap<>();
		Map<String, String> latestSeason = new HashMap<>();
		try {
			List<CountryLeagueSeasonMasterEntity> rows = this.countryLeagueSeasonMasterRepository.findAllRounds();
			if (rows != null) {
				for (CountryLeagueSeasonMasterEntity row : rows) {
					if (row == null) {
						continue;
					}
					String country = trimToNull(row.getCountry());
					String league = normalizeLeague(trimToNull(row.getLeague()));
					Integer round = parseRound(row.getRound());
					if (country == null || league == null || round == null) {
						continue;
					}
					String key = key(country, league);
					String season = trimToNull(row.getSeasonYear());
					if (season != null) {
						bySeason.put(key + "|" + season, round);
					}
					String cur = latestSeason.get(key);
					if (cur == null || (season != null && season.compareTo(cur) > 0)) {
						latestSeason.put(key, season == null ? "" : season);
						latest.put(key, round);
					}
				}
			}
		} catch (RuntimeException e) {
			String messageCd = MessageCdConst.MCD00013E_INITILIZATION_ERROR;
			this.loggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, e,
					"country_league_season_master の読み込みに失敗");
			throw e;
		}
		this.roundBySeason = bySeason;
		this.latestRound = latest;
	}

	/**
	 * 総ラウンド数（無ければ null）。
	 */
	public Integer getTotalRounds(String country, String league, String season) {
		String key = key(trimToNull(country), normalizeLeague(trimToNull(league)));
		Integer r = season == null ? null : this.roundBySeason.get(key + "|" + season.trim());
		return r != null ? r : this.latestRound.get(key);
	}

	/**
	 * 序盤/中盤/終盤（総ラウンド数の 1/3 ずつ。どちらかが無ければ null）。
	 */
	public static Phase toPhase(Integer roundNo, Integer totalRounds) {
		if (roundNo == null || totalRounds == null || totalRounds <= 0) {
			return null;
		}
		int firstEnd = (int) Math.ceil(totalRounds / 3.0);
		int secondEnd = (int) Math.ceil(totalRounds * 2.0 / 3.0);
		if (roundNo <= firstEnd) {
			return Phase.FIRST;
		}
		return roundNo <= secondEnd ? Phase.MID : Phase.LAST;
	}

	/**
	 * マスタのリーグ名を試合データの表記に合わせる（null はそのまま null）。
	 */
	static String normalizeLeague(String league) {
		if (league == null) {
			return null;
		}
		if (ITALY_SERIEA.equals(league)) {
			return "セリエA";
		}
		if (ENGLAND_PREMIER.equals(league)) {
			return "プレミアリーグ";
		}
		int idx = league.indexOf(" - ");
		if (idx < 0) {
			idx = league.indexOf('-');
		}
		return idx > 0 ? league.substring(0, idx).trim() : league;
	}

	private static String key(String country, String league) {
		return country + "|" + league;
	}

	private static Integer parseRound(String s) {
		String t = trimToNull(s);
		if (t == null) {
			return null;
		}
		t = java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFKC);
		try {
			int v = Integer.parseInt(t);
			return v > 0 ? v : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String trimToNull(String s) {
		return (s == null || s.isBlank()) ? null : s.trim();
	}
}