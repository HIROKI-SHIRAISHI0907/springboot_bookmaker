package dev.application.analyze.bm_m031;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.common.service.SeqNumberingService;
import dev.application.analyze.interf.SeasonResolverIF;
import dev.application.domain.repository.bm.SurfaceOverviewMatchRepository;
import dev.common.constant.MessageCdConst;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M031 登録処理（surface_overview_match）。
 *
 * <h2>何をするクラスか</h2>
 * <p>
 * {@link SurfaceOverviewStat} が作った1試合分の2行（ホーム視点・アウェー視点）に、シーズン・国・リーグ・seq・
 * 総ラウンド数・序盤/中盤/終盤を設定して UPSERT する。
 * 一意キー（シーズン・国・リーグ・チーム・対戦相手・H/A）が既にあれば上書きするため、同じ試合を再処理しても行は増えない。
 * 欠けていた試合を後から入れた場合も、この1試合分の行が増えるだけで、連続記録・順位はビューが計算し直す。
 * </p>
 *
 * <h2>シーズン・seq</h2>
 * <ul>
 *   <li>シーズンは他の Writer と同じく {@link SeasonResolverIF} から取得する。1回の集計処理の間は国,リーグごとに
 *       スレッド単位でキャッシュする。呼び出し側は集計の開始時と終了時（finally）に {@link #clearSeasonCache()} を呼ぶこと。</li>
 *   <li>seq は「&lt;シーズン&gt;-&lt;6桁枝番&gt;」を {@link SeqNumberingService}（seq_counter）で採番する。
 *       既に行があるときはその seq を使い、採番しない。</li>
 *   <li>総ラウンド数はシーズンが決まってから {@link BmM031SurfaceOverviewBean} で引く（シーズンごとに違ってもよい）。</li>
 * </ul>
 *
 * <h2>トランザクション</h2>
 * <p>1試合（2行）＝1トランザクション（REQUIRES_NEW）。片方だけ保存された状態は残らない。</p>
 *
 * <h2>懸念点・エラーが起こりそうな箇所</h2>
 * <ul>
 *   <li><b>採番の待ち</b>: 同じシーズンの採番は seq_counter の同じ行を更新するため、新規行を含む試合の保存は1試合ずつ順番になる。</li>
 *   <li><b>同時実行で同じ試合を処理した場合</b>: 後の処理の番号が欠番になる（行の重複は起きない）。</li>
 *   <li><b>シーズンは処理日基準</b>（SeasonResolverIF の実装による）。過去シーズンの欠け試合を今のシーズン中に入れると、
 *       今のシーズンとして保存される。過去シーズンを入れ直す場合はシーズンの決め方を確認すること。</li>
 *   <li><b>同じ組み合わせ（同じ H/A）の試合がシーズン内に2試合ある場合は後の試合で上書き</b>。</li>
 * </ul>
 */
@Service
public class SurfaceOverviewWriter {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = SurfaceOverviewWriter.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = SurfaceOverviewWriter.class.getName();

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M031";

	/** 採番単位のテーブル名 */
	private static final String TABLE_NAME = "surface_overview_match";

	/** シーズン取得不可を表すキャッシュ値 */
	private static final String NOT_RESOLVED = "";

	/** 1回の集計処理中のシーズンキャッシュ（国 + リーグ → シーズン。取得不可は NOT_RESOLVED） */
	private static final ThreadLocal<Map<String, String>> SEASON_CACHE = ThreadLocal.withInitial(HashMap::new);

	@Autowired
	private SurfaceOverviewMatchRepository surfaceOverviewMatchRepository;

	/** 総ラウンド数 */
	@Autowired
	private BmM031SurfaceOverviewBean surfaceOverviewBean;

	/** seq 採番（seq_counter） */
	@Autowired
	private SeqNumberingService seqNumberingService;

	/**
	 * シーズン取得（実装: CountryLeagueSeasonResolver）。
	 * 実装が無い場合もアプリが起動できるよう required = false。保存時に実装が無ければ例外。
	 */
	@Autowired(required = false)
	private SeasonResolverIF seasonResolver;

	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * シーズンのキャッシュを破棄する。集計の開始時と終了時（finally）に呼ぶこと。
	 */
	public void clearSeasonCache() {
		SEASON_CACHE.remove();
	}

	/**
	 * 1試合分（2行）を1トランザクションで UPSERT する。
	 *
	 * @param country 国
	 * @param league リーグ
	 * @param rows 1試合分（team・opponent・ha・result 等を設定済みであること）
	 * @throws SeasonNotResolvedException シーズンが取得できない場合（何も保存しない）
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void saveMatch(String country, String league, List<SurfaceOverviewMatchEntity> rows) {
		final String METHOD_NAME = "saveMatch";
		if (rows == null || rows.isEmpty()) {
			return;
		}
		for (SurfaceOverviewMatchEntity row : rows) {
			if (row == null || isBlank(row.getTeam()) || isBlank(row.getOpponent()) || isBlank(row.getHa())
					|| isBlank(row.getResult())) {
				throw new IllegalArgumentException(BM_NUMBER + " キー項目が空の行があります");
			}
		}

		// DB 書き込みの前にシーズンを決める（取得できなければ何も保存せずに例外）
		String season = resolveSeason(country, league);
		Integer totalRounds = this.surfaceOverviewBean.getTotalRounds(country, league, season);

		int numbered = 0;
		for (SurfaceOverviewMatchEntity row : rows) {
			row.setSeason(season);
			row.setCountry(country);
			row.setLeague(league);
			row.setTotalRounds(totalRounds);
			Phase phase = BmM031SurfaceOverviewBean.toPhase(row.getRoundNo(), totalRounds);
			row.setPhase(phase == null ? null : phase.name());
			if (row.getRoundNo() != null && totalRounds != null && row.getRoundNo() > totalRounds) {
				this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						MessageCdConst.MCD00099I_LOG,
						BM_NUMBER + " ラウンド番号が総ラウンド数を超えています（終盤として扱う）: round=" + row.getRoundNo()
								+ ", totalRounds=" + totalRounds + " (" + setLoggerFillChar(row) + ")");
			}

			String seq = this.surfaceOverviewMatchRepository.findSeq(
					season, country, league, row.getTeam(), row.getOpponent(), row.getHa());
			if (seq == null) {
				seq = this.seqNumberingService.nextSeq(TABLE_NAME, season);
				numbered++;
			}
			row.setSeq(seq);

			int result = this.surfaceOverviewMatchRepository.upsert(row);
			if (result != 1) {
				this.rootCauseWrapper.throwUnexpectedRowCount(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						MessageCdConst.MCD00007E_INSERT_FAILED,
						1, result,
						"seq=" + seq + ", " + setLoggerFillChar(row));
			}
		}

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME,
				MessageCdConst.MCD00005I_INSERT_SUCCESS,
				BM_NUMBER + " 登録/更新件数: " + rows.size() + "件（うち新規採番: " + numbered + "件） ("
						+ setLoggerFillChar(rows.get(0)) + ")");
	}

	/**
	 * 国,リーグのシーズンを取得する（1回の集計処理の中ではキャッシュを使う）。
	 *
	 * @throws SeasonNotResolvedException 取得できない場合
	 */
	private String resolveSeason(String country, String league) {
		final String METHOD_NAME = "resolveSeason";
		if (this.seasonResolver == null) {
			throw new SeasonNotResolvedException(
					"SeasonResolverIF の実装がありません（CountryLeagueSeasonResolver が Bean 登録されていない）: "
							+ country + ", " + league);
		}
		String cacheKey = country + "\u0000" + league;
		Map<String, String> cache = SEASON_CACHE.get();
		String season = cache.get(cacheKey);
		if (season == null) {
			season = NOT_RESOLVED;
			try {
				String s = this.seasonResolver.resolveSeason(country, league);
				if (!isBlank(s)) {
					season = s.trim();
				}
			} catch (RuntimeException e) {
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG, e,
						"country=" + country + ", league=" + league);
			}
			cache.put(cacheKey, season);
		}
		if (NOT_RESOLVED.equals(season)) {
			throw new SeasonNotResolvedException("シーズンを取得できません: country=" + country + ", league=" + league);
		}
		return season;
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}

	private static String setLoggerFillChar(SurfaceOverviewMatchEntity e) {
		return "シーズン: " + e.getSeason() + ", 国: " + e.getCountry() + ", リーグ: " + e.getLeague()
				+ ", チーム: " + e.getTeam() + ", 対戦: " + e.getOpponent() + ", H/A: " + e.getHa()
				+ ", ラウンド: " + e.getRoundNo() + ", 結果: " + e.getResult()
				+ " " + e.getGoalsFor() + "-" + e.getGoalsAgainst();
	}

	/**
	 * シーズンが取得できないことを表す例外。
	 * DB 書き込みの前に投げるため、この例外で終わった試合は何も保存されていない。
	 */
	public static class SeasonNotResolvedException extends RuntimeException {

		private static final long serialVersionUID = 1L;

		public SeasonNotResolvedException(String message) {
			super(message);
		}
	}
}