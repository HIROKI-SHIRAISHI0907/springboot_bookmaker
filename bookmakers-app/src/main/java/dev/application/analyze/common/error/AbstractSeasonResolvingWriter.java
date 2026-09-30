package dev.application.analyze.common.error;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import dev.application.analyze.interf.SeasonResolverIF;
import dev.common.constant.MessageCdConst;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.CountryLeagueParser;

/**
 * シーズンを決めてから保存する Writer の共通親クラス（BM_M003 〜 BM_M034 の各 Writer が継承する）。
 *
 * <h2>何をするクラスか</h2>
 * <ul>
 *   <li>国・リーグのシーズンを {@link SeasonResolverIF} から取得する（各 Writer にあった同じ処理をまとめた）。</li>
 *   <li>1回の集計処理の間は、国,リーグごとの結果（取得できなかった場合も）をスレッド単位でキャッシュする。
 *       呼び出し側（Stat）は集計の開始時と終了時（finally）に {@link #clearSeasonCache()} を呼ぶこと。</li>
 *   <li><b>取得できなかった場合は、その試合を analyze_error_match に記録してから</b>
 *       {@link SeasonNotResolvedException} を投げる。DB 書き込みの前に判定するので、その試合は何も保存されない。
 *       呼び出し側（Stat）はこの例外を捕まえてその試合だけスキップし、次の試合へ進む。</li>
 *   <li><b>解決したら自動で対応済みにする</b>: シーズンが取得できた試合に未対応のエラーがあれば、
 *       その Writer のトランザクションが<b>コミットされた後</b>に、エラーを対応済み（resolved_by = 'AUTO'）にする。
 *       保存が失敗（ロールバック）した場合は対応済みにしない。行は消さない（履歴として残す）。</li>
 * </ul>
 * <p>
 * 自動解決の対象は「同じ BM・国・リーグ・ホーム・アウェー」の未対応エラー（エラー種別は問わない）。
 * 毎回 DB を見に行かないよう、1回の集計処理の最初に BM ごとの未対応キーを読み込み、該当する試合のときだけ更新する。
 * </p>
 *
 * <h2>使い方</h2>
 * <pre>
 * public class XxxWriter extends AbstractSeasonResolvingWriter {
 *     protected String getBmNumber() { return "BM_Mxxx"; }
 *
 *     public void saveMatch(String country, String league, ...) {
 *         String season = resolveSeason(country, league,
 *                 AnalyzeErrorInfo.match(category, home, away).matchId(matchId));
 *         ...
 *     }
 * }
 * </pre>
 * <p>
 * 各 Writer の {@code XxxWriter.SeasonNotResolvedException} は、この親クラスの {@link SeasonNotResolvedException} を指す
 * （入れ子クラスは継承されるため、Stat の catch 句は書き換えなくてよい）。
 * </p>
 *
 * <h2>懸念点</h2>
 * <ul>
 *   <li><b>キャッシュは全 Writer で共有</b>（同じスレッドなら、どの Writer で引いた結果も使う）。
 *       どの Writer の {@link #clearSeasonCache()} を呼んでも全体が消える。Stat は1つずつ順番に動く前提。</li>
 *   <li><b>エラーの記録は1試合ごと</b>: 同じリーグの試合が 100 試合あれば 100 行（同じ試合が再送されたら発生回数 +1）。</li>
 *   <li><b>自動解決のキー</b>: 国・リーグ・ホーム・アウェーが記録時と完全に同じ表記のときだけ解決する
 *       （チーム名の表記が変わった場合は、画面で手動で対応済みにする）。キーが国・リーグしかない INVALID_CATEGORY 等は、
 *       その国・リーグ・空のチーム名で登録される処理（BM_M006）が成功したときだけ解決する。</li>
 *   <li><b>集計処理の途中で画面から未対応に戻した行</b>は、次の集計処理から自動解決の対象になる（未対応キーは処理の最初に読むため）。</li>
 *   <li><b>シーズンは処理日基準</b>（SeasonResolverIF の実装による）。</li>
 * </ul>
 */
public abstract class AbstractSeasonResolvingWriter {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = AbstractSeasonResolvingWriter.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = AbstractSeasonResolvingWriter.class.getName();

	/** 1回の集計処理中のシーズンキャッシュ（国 + リーグ → 結果） */
	private static final ThreadLocal<Map<String, SeasonResult>> SEASON_CACHE = ThreadLocal.withInitial(HashMap::new);

	/** 1回の集計処理中の未対応エラーの試合キー（BM → キー）。自動解決の判定用 */
	private static final ThreadLocal<Map<String, Set<String>>> UNRESOLVED = ThreadLocal.withInitial(HashMap::new);

	/**
	 * シーズン取得（実装: CountryLeagueSeasonResolver）。
	 * 実装が無い場合もアプリが起動できるよう required = false。保存時に実装が無ければエラーとして記録する。
	 */
	@Autowired(required = false)
	private SeasonResolverIF seasonResolver;

	/** 登録できなかった試合の記録 */
	@Autowired
	private AnalyzeErrorRecorder analyzeErrorRecorder;

	/** ログ */
	@Autowired
	private ManageLoggerComponent seasonLogger;

	/**
	 * BM 番号（エラーの記録・ログに使う）。
	 *
	 * @return 例: "BM_M004"
	 */
	protected abstract String getBmNumber();

	/**
	 * シーズンのキャッシュを破棄する。集計の開始時と終了時（finally）に呼ぶこと。
	 */
	public void clearSeasonCache() {
		SEASON_CACHE.remove();
		UNRESOLVED.remove();
	}

	/**
	 * 国,リーグのシーズンを取得する。取得できなければ analyze_error_match に記録して例外。
	 *
	 * @param country 国
	 * @param league リーグ
	 * @param info 記録用の試合情報（国・リーグは自動で入る。null 可）
	 * @return シーズン
	 * @throws SeasonNotResolvedException 取得できない場合（記録済み）
	 */
	protected String resolveSeason(String country, String league, AnalyzeErrorInfo info) {
		AnalyzeErrorInfo i = (info == null ? AnalyzeErrorInfo.empty() : info).countryLeague(country, league);
		SeasonResult r = lookup(country, league);
		if (r.season != null) {
			resolveAfterCommit(i);
			return r.season;
		}
		this.analyzeErrorRecorder.record(getBmNumber(), r.type, r.message, i, null, r.cause);
		unresolvedKeys().add(key(i));
		throw new SeasonNotResolvedException(r.message + " (" + i + ")");
	}

	/**
	 * 「国: リーグ - ラウンドN」形式のキーからシーズンを取得する。取得できなければ記録して例外。
	 *
	 * @param dataCategory キー
	 * @param info 記録用の試合情報（キーは自動で入る。null 可）
	 * @return シーズン
	 * @throws SeasonNotResolvedException 取得できない場合（記録済み）
	 */
	protected String resolveSeasonByCategory(String dataCategory, AnalyzeErrorInfo info) {
		AnalyzeErrorInfo i = (info == null ? AnalyzeErrorInfo.empty() : info).dataCategory(dataCategory);
		String[] cl = CountryLeagueParser.parse(dataCategory);
		if (cl == null || cl.length < 2) {
			String message = "キーから国・リーグを取得できません: " + dataCategory;
			this.analyzeErrorRecorder.record(getBmNumber(), AnalyzeErrorType.INVALID_CATEGORY, message, i, null, null);
			throw new SeasonNotResolvedException(message);
		}
		return resolveSeason(cl[0].trim(), cl[1].trim(), i);
	}

	/**
	 * シーズンを取得する。取得できなければ記録して null を返す（例外にしない。まとめて保存する Writer 用）。
	 *
	 * @param country 国
	 * @param league リーグ
	 * @param info 記録用の情報（null 可）
	 * @return シーズン（取得できなければ null）
	 */
	protected String resolveSeasonOrNull(String country, String league, AnalyzeErrorInfo info) {
		try {
			return resolveSeason(country, league, info);
		} catch (SeasonNotResolvedException e) {
			return null;
		}
	}

	/**
	 * シーズン以外の理由で登録できなかった試合を記録する（例外は投げない）。
	 *
	 * @param type エラー種別
	 * @param message エラー内容
	 * @param info 試合の情報
	 * @param season シーズン（分かっていれば）
	 * @param cause 原因の例外
	 */
	protected void recordError(AnalyzeErrorType type, String message, AnalyzeErrorInfo info, String season,
			Throwable cause) {
		this.analyzeErrorRecorder.record(getBmNumber(), type, message, info, season, cause);
	}

	/**
	 * この試合に未対応のエラーがあれば、保存のトランザクションがコミットされた後に対応済みにする。
	 * トランザクションの外で呼ばれた場合（コミットされたか分からない）は何もしない。
	 */
	private void resolveAfterCommit(AnalyzeErrorInfo info) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			return;
		}
		Set<String> keys = unresolvedKeys();
		String k = key(info);
		if (!keys.contains(k)) {
			return;
		}
		final String bm = getBmNumber();
		final AnalyzeErrorRecorder recorder = this.analyzeErrorRecorder;
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				// コミット後の処理は元のトランザクションに入れないため、AnalyzeErrorWriter は REQUIRES_NEW で更新する
				recorder.resolve(bm, info);
				keys.remove(k);
			}
		});
	}

	/** この BM の未対応エラーの試合キー（1回の集計処理で最初の1回だけ DB から読む） */
	private Set<String> unresolvedKeys() {
		return UNRESOLVED.get().computeIfAbsent(getBmNumber(),
				bm -> new HashSet<>(this.analyzeErrorRecorder.findUnresolvedKeys(bm)));
	}

	private static String key(AnalyzeErrorInfo i) {
		return AnalyzeErrorWriter.key(i.getCountry(), i.getLeague(), i.getHomeTeamName(), i.getAwayTeamName());
	}

	/**
	 * キャッシュを使ってシーズンを引く。
	 */
	private SeasonResult lookup(String country, String league) {
		final String METHOD_NAME = "resolveSeason";
		String cacheKey = country + "\u0000" + league;
		Map<String, SeasonResult> cache = SEASON_CACHE.get();
		SeasonResult r = cache.get(cacheKey);
		if (r != null) {
			return r;
		}
		if (this.seasonResolver == null) {
			r = SeasonResult.error(AnalyzeErrorType.SEASON_RESOLVER_MISSING,
					"SeasonResolverIF の実装がありません（CountryLeagueSeasonResolver が Bean 登録されていない）", null);
		} else if (isBlank(country) || isBlank(league)) {
			r = SeasonResult.error(AnalyzeErrorType.INVALID_CATEGORY, "国・リーグが空です", null);
		} else {
			try {
				String s = this.seasonResolver.resolveSeason(country, league);
				r = isBlank(s)
						? SeasonResult.error(AnalyzeErrorType.SEASON_NOT_FOUND,
								"シーズンが見つかりません（country_league_season_master を確認）: country=" + country
										+ ", league=" + league,
								null)
						: SeasonResult.ok(s.trim());
			} catch (RuntimeException e) {
				this.seasonLogger.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG, e,
						getBmNumber() + " country=" + country + ", league=" + league);
				r = SeasonResult.error(AnalyzeErrorType.SEASON_RESOLVE_FAILED,
						"シーズン取得でエラー: " + e.getMessage(), e);
			}
		}
		cache.put(cacheKey, r);
		return r;
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}

	/**
	 * シーズン取得の結果（キャッシュ用）。
	 */
	private static final class SeasonResult {
		private final String season;
		private final AnalyzeErrorType type;
		private final String message;
		private final Throwable cause;

		private SeasonResult(String season, AnalyzeErrorType type, String message, Throwable cause) {
			this.season = season;
			this.type = type;
			this.message = message;
			this.cause = cause;
		}

		static SeasonResult ok(String season) {
			return new SeasonResult(season, null, null, null);
		}

		static SeasonResult error(AnalyzeErrorType type, String message, Throwable cause) {
			return new SeasonResult(null, type, message, cause);
		}
	}

	/**
	 * シーズンが取得できないことを表す例外。
	 * DB 書き込みの前に投げるため、この例外で終わった試合は何も保存されていない（analyze_error_match には記録済み）。
	 */
	public static class SeasonNotResolvedException extends RuntimeException {

		private static final long serialVersionUID = 1L;

		public SeasonNotResolvedException(String message) {
			super(message);
		}
	}
}