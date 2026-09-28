package dev.application.analyze.bm_m005;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.common.service.SeqNumberingService;
import dev.application.analyze.interf.SeasonResolverIF;
import dev.application.domain.repository.bm.NoGoalMatchStatsRepository;
import dev.common.constant.MessageCdConst;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * BM_M005 登録処理（no_goal_match_stats）。
 *
 * <h2>何をするクラスか</h2>
 * <p>
 * {@link NoGoalMatchStat} が作った1試合分の行（START / HT / END の最大3行）に、シーズンと seq を設定して UPSERT する。
 * 一意キー（シーズン・国リーグ・ホーム・アウェー・時点）が既にあれば上書きするため、
 * 同じ試合を再処理しても行は増えない（冪等）。
 * </p>
 *
 * <h2>シーズンの取得</h2>
 * <ul>
 *   <li>他の Writer と同じく {@link SeasonResolverIF}（country_league_season_master.season_year）から取得する。</li>
 *   <li>国・リーグは dataCategory を {@link ExecuteMainUtil#splitLeagueInfo} で分割して求める。</li>
 *   <li>1回の集計処理の間は、国,リーグごとの結果をスレッド単位でキャッシュする（取得不可も含む）。
 *       呼び出し側は集計の開始時と終了時（finally）に {@link #clearSeasonCache()} を呼ぶこと。</li>
 *   <li>取得できない場合は DB 書き込みの前に {@link SeasonNotResolvedException} を投げる（その試合は何も保存されない）。</li>
 * </ul>
 *
 * <h2>seq（主キー）の採番</h2>
 * <ul>
 *   <li>「&lt;シーズン&gt;-&lt;6桁枝番&gt;」を {@link SeqNumberingService}（seq_counter）で採番する。枝番はテーブル×シーズンで 1 から。</li>
 *   <li>先に1試合分の既存行の seq を引き、既にある時点はその seq を使い回す。新しい時点の行だけ採番するため、
 *       再処理で番号を消費しない。</li>
 * </ul>
 *
 * <h2>トランザクション</h2>
 * <p>
 * 1試合（最大3行）＝1トランザクション（REQUIRES_NEW）。途中で失敗するとその試合の行はすべてロールバックされ、
 * 採番もロールバックされる（欠番にならない）。それ以前に登録済みの試合は取り消されない。
 * 以前は1行ずつのトランザクションで、途中で失敗すると START だけ保存された試合が残っていた。
 * </p>
 *
 * <h2>懸念点・エラーが起こりそうな箇所</h2>
 * <ul>
 *   <li><b>採番の待ち</b>: 同じシーズンの採番は seq_counter の同じ行を更新するため、新規行を含む試合の保存は1試合ずつ順番になる。</li>
 *   <li><b>同時実行で同じ試合を処理した場合</b>: 後の処理の番号が欠番になる（行の重複は起きない）。</li>
 *   <li><b>シーズンは処理日基準</b>: シーズン切替直後に前シーズンの試合を処理すると、新シーズンとして保存される。</li>
 *   <li><b>同じ組み合わせ（ホーム・アウェー）の試合がシーズン内に2試合ある場合</b>: 後の試合で上書きされる。</li>
 *   <li><b>1試合分に別の試合の行が混ざっている場合</b>: 不正データとして IllegalArgumentException（Stat の作り方では起きない）。</li>
 * </ul>
 */
@Service
public class NoGoalMatchWriter {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = NoGoalMatchWriter.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = NoGoalMatchWriter.class.getName();

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M005";

	/** 採番単位のテーブル名 */
	private static final String TABLE_NAME = "no_goal_match_stats";

	/** シーズン取得不可を表すキャッシュ値 */
	private static final String NOT_RESOLVED = "";

	/** 1回の集計処理中のシーズンキャッシュ（国,リーグ → シーズン。取得不可は NOT_RESOLVED） */
	private static final ThreadLocal<Map<String, String>> SEASON_CACHE = ThreadLocal.withInitial(HashMap::new);

	/** NoGoalMatchStatsRepository レポジトリクラス */
	@Autowired
	private NoGoalMatchStatsRepository noGoalMatchStatsRepository;

	/** seq 採番（seq_counter） */
	@Autowired
	private SeqNumberingService seqNumberingService;

	/**
	 * シーズン取得（実装: CountryLeagueSeasonResolver）。
	 * 実装が無い場合もアプリが起動できるよう required = false。登録時に実装が無ければ例外。
	 */
	@Autowired(required = false)
	private SeasonResolverIF seasonResolver;

	/** ログ管理ラッパー */
	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * シーズンのキャッシュを破棄する。集計の開始時と終了時（finally）に呼ぶこと。
	 */
	public void clearSeasonCache() {
		SEASON_CACHE.remove();
	}

	/**
	 * 1試合分の行にシーズンと seq を設定し、1トランザクションで UPSERT する。
	 *
	 * @param entities 1試合分（null・空の場合は何もしない。null 要素は無視）
	 * @throws SeasonNotResolvedException シーズンが取得できない場合（何も保存しない）
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void upsertMatch(List<NoGoalMatchStatisticsEntity> entities) {
		final String METHOD_NAME = "upsertMatch";
		if (entities == null || entities.isEmpty()) {
			return;
		}

		// DB 書き込みの前にシーズンを決める（取得できなければ何も保存せずに例外）
		NoGoalMatchStatisticsEntity first = requireSingleMatch(entities);
		String season = resolveSeason(first.getDataCategory());

		// 既存行の seq（時点 → seq）
		Map<String, String> existingSeq = findExistingSeq(season, first);

		int count = 0;
		int numbered = 0;
		for (NoGoalMatchStatisticsEntity entity : entities) {
			if (entity == null) {
				continue;
			}
			entity.setSeason(season);
			String seq = existingSeq.get(entity.getSnapshotType());
			if (seq == null) {
				seq = this.seqNumberingService.nextSeq(TABLE_NAME, season);
				numbered++;
			}
			entity.setSeq(seq);

			int result = this.noGoalMatchStatsRepository.upsert(entity);
			if (result != 1) {
				this.rootCauseWrapper.throwUnexpectedRowCount(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						MessageCdConst.MCD00007E_INSERT_FAILED,
						1, result,
						setLoggerFillChar(entity));
			}
			count++;
		}

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME,
				MessageCdConst.MCD00005I_INSERT_SUCCESS,
				BM_NUMBER + " 登録/更新件数: " + count + "件（うち新規採番: " + numbered + "件） ("
						+ setLoggerFillChar(first) + ")");
	}

	/**
	 * 1試合分の行がすべて同じ試合（国リーグ・ホーム・アウェー）で、時点が重複していないことを確認し、先頭の行を返す。
	 */
	private NoGoalMatchStatisticsEntity requireSingleMatch(List<NoGoalMatchStatisticsEntity> entities) {
		NoGoalMatchStatisticsEntity first = null;
		Set<String> snapshotTypes = new HashSet<>();
		for (NoGoalMatchStatisticsEntity e : entities) {
			if (e == null) {
				continue;
			}
			if (isBlank(e.getDataCategory()) || isBlank(e.getHomeTeamName()) || isBlank(e.getAwayTeamName())
					|| isBlank(e.getSnapshotType())) {
				throw new IllegalArgumentException(BM_NUMBER + " キー項目が空の行があります: " + setLoggerFillChar(e));
			}
			if (!snapshotTypes.add(e.getSnapshotType())) {
				throw new IllegalArgumentException(
						BM_NUMBER + " 同じ時点の行が重複しています: " + e.getSnapshotType() + " / " + setLoggerFillChar(e));
			}
			if (first == null) {
				first = e;
			} else if (!first.getDataCategory().equals(e.getDataCategory())
					|| !first.getHomeTeamName().equals(e.getHomeTeamName())
					|| !first.getAwayTeamName().equals(e.getAwayTeamName())) {
				throw new IllegalArgumentException(BM_NUMBER + " 1試合分に別の試合の行が混ざっています: "
						+ setLoggerFillChar(first) + " / " + setLoggerFillChar(e));
			}
		}
		if (first == null) {
			throw new IllegalArgumentException(BM_NUMBER + " 有効な行がありません");
		}
		return first;
	}

	/**
	 * 1試合分の既存行の seq を取得する（時点 → seq）。
	 */
	private Map<String, String> findExistingSeq(String season, NoGoalMatchStatisticsEntity key) {
		Map<String, String> map = new HashMap<>();
		List<NoGoalMatchStatisticsEntity> rows = this.noGoalMatchStatsRepository.findSeqByMatchKey(
				season, key.getDataCategory(), key.getHomeTeamName(), key.getAwayTeamName());
		if (rows != null) {
			for (NoGoalMatchStatisticsEntity r : rows) {
				if (r != null && r.getSnapshotType() != null && r.getSeq() != null) {
					map.put(r.getSnapshotType(), r.getSeq());
				}
			}
		}
		return map;
	}

	/**
	 * 国,リーグのシーズンを取得する（1回の集計処理の中ではキャッシュを使う）。
	 *
	 * @throws SeasonNotResolvedException 取得できない場合
	 */
	private String resolveSeason(String dataCategory) {
		final String METHOD_NAME = "resolveSeason";
		if (this.seasonResolver == null) {
			throw new SeasonNotResolvedException(
					"SeasonResolverIF の実装がありません（CountryLeagueSeasonResolver が Bean 登録されていない）: "
							+ dataCategory);
		}

		Map<String, String> cache = SEASON_CACHE.get();
		String season = cache.get(dataCategory);
		if (season == null) {
			season = NOT_RESOLVED;
			try {
				String[] split = ExecuteMainUtil.splitLeagueInfo(dataCategory);
				if (split != null && split.length >= 2) {
					String s = this.seasonResolver.resolveSeason(split[0].trim(), split[1].trim());
					if (!isBlank(s)) {
						season = s.trim();
					}
				}
			} catch (RuntimeException e) {
				this.manageLoggerComponent.debugErrorLog(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME, MessageCdConst.MCD00099I_LOG, e,
						"countryLeague=" + dataCategory);
			}
			cache.put(dataCategory, season);
		}

		if (NOT_RESOLVED.equals(season)) {
			throw new SeasonNotResolvedException("シーズンを取得できません: " + dataCategory);
		}
		return season;
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}

	/**
	 * 埋め字設定
	 */
	private String setLoggerFillChar(NoGoalMatchStatisticsEntity entity) {
		StringBuilder sb = new StringBuilder();
		sb.append("seq: ").append(entity.getSeq()).append(", ");
		sb.append("シーズン: ").append(entity.getSeason()).append(", ");
		sb.append("国,リーグ: ").append(entity.getDataCategory()).append(", ");
		sb.append("ホームチーム: ").append(entity.getHomeTeamName()).append(", ");
		sb.append("アウェーチーム: ").append(entity.getAwayTeamName()).append(", ");
		sb.append("時点: ").append(entity.getSnapshotType());
		return sb.toString();
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