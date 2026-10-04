package dev.application.analyze.bm_m029;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.common.service.SeqNumberingService;
import dev.application.analyze.interf.SeasonResolverIF;
import dev.application.domain.repository.bm.RealDataProcessRepository;
import dev.common.constant.MessageCdConst;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M029 登録処理（real_data_process）。
 *
 * <h2>何をするクラスか</h2>
 * <p>
 * {@link RealDataProcessStat} が作った1試合分の差分に、シーズン・国・リーグ・seq を設定し、match_id をキーに UPSERT する
 * （1試合1行。新しいデータが来るたびに最新の差分で上書き）。
 * </p>
 *
 * <h2>統計処理の Writer との違い（AbstractSeasonResolvingWriter を継承しない）</h2>
 * <p>
 * BM_M029 はリアルタイムデータの差分を保存するだけで、統計データではない。そのため
 * 統計処理の共通親クラス AbstractSeasonResolvingWriter は継承せず、<b>analyze_error_match には記録しない</b>。
 * シーズンが取得できない試合はログ（debug）だけ出してスキップする。
 * </p>
 *
 * <h2>シーズン・seq</h2>
 * <ul>
 *   <li>シーズンは {@link SeasonResolverIF} から直接取得する。1回の処理の間は国,リーグごとにスレッド単位でキャッシュする
 *       （このクラス専用。統計処理の Writer のキャッシュとは別）。
 *       呼び出し側は処理の開始時と終了時（finally）に {@link #clearSeasonCache()} を呼ぶこと。</li>
 *   <li>seq は「&lt;シーズン&gt;-&lt;6桁枝番&gt;」を {@link SeqNumberingService}（seq_counter）で採番する。
 *       同じ match_id の行が既にあればその seq を使い、採番しない（上書きのたびに番号を消費しない）。</li>
 * </ul>
 *
 * <h2>トランザクション</h2>
 * <p>1試合＝1トランザクション（REQUIRES_NEW）。失敗すると採番も含めてロールバックされる。</p>
 *
 * <h2>懸念点・エラーが起こりそうな箇所</h2>
 * <ul>
 *   <li><b>シーズンが取得できない国・リーグは保存されない</b>（seq にシーズンが必要なため）。画面のエラー一覧には出ないので、
 *       気づくにはログ（「シーズン取得不可」）を見る。</li>
 *   <li><b>採番の待ち</b>: 同じシーズンの採番は seq_counter の同じ行を更新するため、新規の試合の保存は1試合ずつ順番になる
 *       （上書きは採番しないので待たない）。</li>
 *   <li><b>同時実行で同じ試合を2つの処理が新規として採番した場合</b>: 後の処理は更新になり、後の番号は欠番になる（行の重複は起きない）。</li>
 *   <li><b>シーズンは処理日基準</b>（SeasonResolverIF の実装による）。</li>
 * </ul>
 */
@Service
public class RealDataProcessWriter {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = RealDataProcessWriter.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = RealDataProcessWriter.class.getName();

	/** BM番号 */
	private static final String BM_NUMBER = "BM_M029";

	/** 採番単位のテーブル名 */
	private static final String TABLE_NAME = "real_data_process";

	/** 取得できなかったことを表すキャッシュの値（null はキャッシュに無いことを表すため） */
	private static final String NOT_FOUND = "\u0000";

	/** 1回の処理中のシーズンキャッシュ（国 + リーグ → シーズン。取得できなければ NOT_FOUND） */
	private static final ThreadLocal<Map<String, String>> SEASON_CACHE = ThreadLocal.withInitial(HashMap::new);

	/** シーズン取得（実装: CountryLeagueSeasonResolver）。実装が無くても起動できるよう required = false */
	@Autowired(required = false)
	private SeasonResolverIF seasonResolver;

	@Autowired
	private RealDataProcessRepository realDataProcessRepository;

	/** seq 採番（seq_counter） */
	@Autowired
	private SeqNumberingService seqNumberingService;

	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * シーズンのキャッシュを破棄する。処理の開始時と終了時（finally）に呼ぶこと。
	 */
	public void clearSeasonCache() {
		SEASON_CACHE.remove();
	}

	/**
	 * 1試合分の差分を match_id をキーに UPSERT する。
	 *
	 * @param country 国
	 * @param league リーグ
	 * @param entity 差分（matchId・チーム名・dataCategory 設定済みであること）
	 * @throws SeasonNotResolvedException シーズンが取得できない場合（何も保存しない。analyze_error_match にも記録しない）
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void save(String country, String league, RealDataProcessEntity entity) {
		final String METHOD_NAME = "save";
		if (entity == null || isBlank(entity.getMatchId()) || isBlank(entity.getHomeTeamName())
				|| isBlank(entity.getAwayTeamName()) || isBlank(entity.getDataCategory())) {
			throw new IllegalArgumentException(BM_NUMBER + " キー項目（matchId・チーム名・dataCategory）が空です");
		}

		// DB 書き込みの前にシーズンを決める（取得できなければ何も保存せずに例外）
		String season = resolveSeason(country, league);
		if (season == null) {
			throw new SeasonNotResolvedException("country=" + country + ", league=" + league);
		}
		entity.setSeason(season);
		entity.setCountry(country);
		entity.setLeague(league);

		String seq = this.realDataProcessRepository.findSeqByMatchId(entity.getMatchId());
		boolean numbered = false;
		if (seq == null) {
			seq = this.seqNumberingService.nextSeq(TABLE_NAME, season);
			numbered = true;
		}
		entity.setSeq(seq);

		int result = this.realDataProcessRepository.upsertByMatchId(entity);
		if (result != 1) {
			this.rootCauseWrapper.throwUnexpectedRowCount(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00007E_INSERT_FAILED,
					1, result,
					"seq=" + seq + ", " + setLoggerFillChar(entity));
		}

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
				BM_NUMBER + (numbered ? " 登録" : " 更新") + ": seq=" + seq + " (" + setLoggerFillChar(entity) + ")");
	}

	/**
	 * シーズンを取得する（キャッシュあり）。取得できなければ null（理由はログに出す）。
	 */
	private String resolveSeason(String country, String league) {
		final String METHOD_NAME = "resolveSeason";
		if (isBlank(country) || isBlank(league)) {
			return null;
		}
		String cacheKey = country + "\u0000" + league;
		Map<String, String> cache = SEASON_CACHE.get();
		String cached = cache.get(cacheKey);
		if (cached != null) {
			return NOT_FOUND.equals(cached) ? null : cached;
		}

		String season = null;
		if (this.seasonResolver == null) {
			this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
					BM_NUMBER + " SeasonResolverIF の実装がありません");
		} else {
			try {
				String s = this.seasonResolver.resolveSeason(country, league);
				season = isBlank(s) ? null : s.trim();
			} catch (RuntimeException e) {
				// マスタに無い（IllegalStateException）なども含めて、取得できない扱い
				this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG,
						BM_NUMBER + " シーズン取得不可 country=" + country + ", league=" + league + " (" + e.getMessage() + ")");
			}
		}
		cache.put(cacheKey, season == null ? NOT_FOUND : season);
		return season;
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}

	private static String setLoggerFillChar(RealDataProcessEntity e) {
		return "シーズン: " + e.getSeason() + ", 国: " + e.getCountry() + ", リーグ: " + e.getLeague()
				+ ", ホーム: " + e.getHomeTeamName() + ", アウェー: " + e.getAwayTeamName()
				+ ", 区間: " + e.getPrevTimes() + "〜" + e.getTimes() + ", matchId: " + e.getMatchId();
	}

	/**
	 * シーズンが取得できないことを表す例外（BM_M029 専用。analyze_error_match には記録しない）。
	 * DB 書き込みの前に投げるため、この例外で終わった試合は何も保存されていない。
	 * Stat はこの例外を捕まえてその試合だけスキップする（Stat の catch 句は書き換え不要）。
	 */
	public static class SeasonNotResolvedException extends RuntimeException {

		private static final long serialVersionUID = 1L;

		public SeasonNotResolvedException(String message) {
			super(message);
		}
	}
}
