package dev.application.analyze.bm_m017;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.common.service.SeqNumberingService;
import dev.application.analyze.interf.SeasonResolverIF;
import dev.application.domain.repository.bm.LeagueScoreGoalEventRepository;
import dev.common.constant.MessageCdConst;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M017 登録処理（league_score_goal_event）。
 *
 * <h2>何をするクラスか</h2>
 * <p>
 * {@link LeagueScoreTimeBandStat} が作った1試合分のゴール（0件もあり）を、シーズンと seq を設定して保存する。
 * </p>
 * <ol>
 *   <li>シーズンを決める（取得できなければ何も保存せずに {@link SeasonNotResolvedException}）。</li>
 *   <li>試合の既存行の seq を引き、同じ「何点目」の行はその seq を使い回す。新しいゴールの行だけ採番する。</li>
 *   <li>ゴールを UPSERT する。</li>
 *   <li>今回のゴール数より後ろ（goal_no が大きい）の既存行を削除する（ゴール取り消しで点数が減った場合）。</li>
 * </ol>
 * <p>
 * 試合単位で「最新の計算結果に置き換える」ため、同じ試合を何度処理しても件数は増えない（冪等）。
 * 以前の「件数に +1 して UPDATE」方式は、同じ試合が流れてくるたびに二重に数えていた。
 * </p>
 *
 * <h2>シーズン・seq</h2>
 * <ul>
 *   <li>シーズンは他の Writer と同じく {@link SeasonResolverIF}（country_league_season_master.season_year）から取得する。
 *       1回の集計処理の間は国,リーグごとにスレッド単位でキャッシュする（取得不可も含む）。
 *       呼び出し側は集計の開始時と終了時（finally）に {@link #clearSeasonCache()} を呼ぶこと。</li>
 *   <li>seq は「&lt;シーズン&gt;-&lt;6桁枝番&gt;」を {@link SeqNumberingService}（seq_counter）で採番する。</li>
 * </ul>
 *
 * <h2>トランザクション</h2>
 * <p>
 * 1試合＝1トランザクション（REQUIRES_NEW）。途中で失敗するとその試合の変更と採番はすべてロールバックされる。
 * 1試合のゴールは数行なのでトランザクションは短い。
 * </p>
 *
 * <h2>懸念点・エラーが起こりそうな箇所</h2>
 * <ul>
 *   <li><b>採番の待ち</b>: 同じシーズンの採番は seq_counter の同じ行を更新するため、新規行を含む試合の保存は1試合ずつ順番になる。</li>
 *   <li><b>同時実行で同じ試合を処理した場合</b>: 後の処理の番号が欠番になる（行の重複は起きない）。</li>
 *   <li><b>シーズンは処理日基準</b>: シーズン切替直後に前シーズンの試合を処理すると、新シーズンとして保存される。</li>
 *   <li><b>同じ組み合わせ（ホーム・アウェー）の試合がシーズン内に2試合ある場合</b>: 後の試合で上書きされる。</li>
 * </ul>
 */
@Service
public class LeagueScoreTimeBandWriter {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = LeagueScoreTimeBandWriter.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = LeagueScoreTimeBandWriter.class.getName();

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M017_BM_M018";

	/** 採番単位のテーブル名 */
	private static final String TABLE_NAME = "league_score_goal_event";

	/** シーズン取得不可を表すキャッシュ値 */
	private static final String NOT_RESOLVED = "";

	/** 1回の集計処理中のシーズンキャッシュ（国 + リーグ → シーズン。取得不可は NOT_RESOLVED） */
	private static final ThreadLocal<Map<String, String>> SEASON_CACHE = ThreadLocal.withInitial(HashMap::new);

	@Autowired
	private LeagueScoreGoalEventRepository leagueScoreGoalEventRepository;

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
	 * 1試合分のゴールを保存する（既存行は置き換え、余った行は削除）。
	 *
	 * @param match 試合（国・リーグ・ホーム・アウェー）
	 * @param goals ゴール（goalNo 順。0件なら既存行を全削除）
	 * @throws SeasonNotResolvedException シーズンが取得できない場合（何も保存しない）
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void saveMatch(MatchKey match, List<LeagueScoreGoalEventEntity> goals) {
		final String METHOD_NAME = "saveMatch";
		if (match == null) {
			throw new IllegalArgumentException(BM_NUMBER + " match is null.");
		}

		String season = resolveSeason(match);

		// 既存行の seq（何点目 → seq）
		Map<Integer, String> existingSeq = new HashMap<>();
		List<LeagueScoreGoalEventEntity> rows = this.leagueScoreGoalEventRepository.findSeqByMatchKey(
				season, match.getCountry(), match.getLeague(), match.getHomeTeamName(), match.getAwayTeamName());
		if (rows != null) {
			for (LeagueScoreGoalEventEntity r : rows) {
				if (r != null && r.getGoalNo() != null && r.getSeq() != null) {
					existingSeq.put(r.getGoalNo(), r.getSeq());
				}
			}
		}

		int count = 0;
		int numbered = 0;
		int maxGoalNo = 0;
		if (goals != null) {
			for (LeagueScoreGoalEventEntity goal : goals) {
				if (goal == null) {
					continue;
				}
				goal.setSeason(season);
				goal.setCountry(match.getCountry());
				goal.setLeague(match.getLeague());
				goal.setHomeTeamName(match.getHomeTeamName());
				goal.setAwayTeamName(match.getAwayTeamName());

				String seq = existingSeq.get(goal.getGoalNo());
				if (seq == null) {
					seq = this.seqNumberingService.nextSeq(TABLE_NAME, season);
					numbered++;
				}
				goal.setSeq(seq);

				int result = this.leagueScoreGoalEventRepository.upsert(goal);
				if (result != 1) {
					this.rootCauseWrapper.throwUnexpectedRowCount(
							PROJECT_NAME, CLASS_NAME, METHOD_NAME,
							MessageCdConst.MCD00007E_INSERT_FAILED,
							1, result,
							"seq=" + seq + ", season=" + season + ", " + match + ", goalNo=" + goal.getGoalNo());
				}
				count++;
				maxGoalNo = Math.max(maxGoalNo, goal.getGoalNo());
			}
		}

		// 取り消し等で減ったゴールの行を削除（既存行がある場合だけ）
		int deleted = 0;
		if (!existingSeq.isEmpty()) {
			deleted = this.leagueScoreGoalEventRepository.deleteGoalsAfter(
					season, match.getCountry(), match.getLeague(), match.getHomeTeamName(), match.getAwayTeamName(),
					maxGoalNo);
		}

		if (count > 0 || deleted > 0) {
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					MessageCdConst.MCD00005I_INSERT_SUCCESS,
					BM_NUMBER + " 登録/更新: " + count + "件（うち新規採番: " + numbered + "件）, 削除: " + deleted
							+ "件 (シーズン: " + season + ", " + match + ")");
		}
	}

	/**
	 * 国,リーグのシーズンを取得する（1回の集計処理の中ではキャッシュを使う）。
	 *
	 * @throws SeasonNotResolvedException 取得できない場合
	 */
	private String resolveSeason(MatchKey match) {
		final String METHOD_NAME = "resolveSeason";
		if (this.seasonResolver == null) {
			throw new SeasonNotResolvedException(
					"SeasonResolverIF の実装がありません（CountryLeagueSeasonResolver が Bean 登録されていない）: " + match);
		}

		String cacheKey = match.getCountry() + "\u0000" + match.getLeague();
		Map<String, String> cache = SEASON_CACHE.get();
		String season = cache.get(cacheKey);
		if (season == null) {
			season = NOT_RESOLVED;
			try {
				String s = this.seasonResolver.resolveSeason(match.getCountry(), match.getLeague());
				if (s != null && !s.isBlank()) {
					season = s.trim();
				}
			} catch (RuntimeException e) {
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG, e,
						"country=" + match.getCountry() + ", league=" + match.getLeague());
			}
			cache.put(cacheKey, season);
		}

		if (NOT_RESOLVED.equals(season)) {
			throw new SeasonNotResolvedException(
					"シーズンを取得できません: country=" + match.getCountry() + ", league=" + match.getLeague());
		}
		return season;
	}

	/**
	 * 試合キー（国・リーグ・ホーム・アウェー）。record は使わない。
	 */
	public static final class MatchKey {

		private final String country;
		private final String league;
		private final String homeTeamName;
		private final String awayTeamName;

		public MatchKey(String country, String league, String homeTeamName, String awayTeamName) {
			if (isBlank(country) || isBlank(league) || isBlank(homeTeamName) || isBlank(awayTeamName)) {
				throw new IllegalArgumentException("country/league/team is blank: " + country + ", " + league
						+ ", " + homeTeamName + ", " + awayTeamName);
			}
			this.country = country;
			this.league = league;
			this.homeTeamName = homeTeamName;
			this.awayTeamName = awayTeamName;
		}

		public String getCountry() {
			return this.country;
		}

		public String getLeague() {
			return this.league;
		}

		public String getHomeTeamName() {
			return this.homeTeamName;
		}

		public String getAwayTeamName() {
			return this.awayTeamName;
		}

		private static boolean isBlank(String s) {
			return s == null || s.isBlank();
		}

		@Override
		public String toString() {
			return "国: " + this.country + ", リーグ: " + this.league
					+ ", ホーム: " + this.homeTeamName + ", アウェー: " + this.awayTeamName;
		}
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