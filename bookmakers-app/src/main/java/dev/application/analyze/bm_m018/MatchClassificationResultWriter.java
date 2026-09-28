package dev.application.analyze.bm_m018;

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
import dev.application.domain.repository.bm.MatchClassificationResultRepository;
import dev.common.constant.MessageCdConst;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M019 / BM_M020 登録処理（classify_result_data）。
 *
 * <h2>何をするクラスか</h2>
 * <p>
 * {@link MatchClassificationResultStat} が作った1試合分の行（KICKOFF / GOAL / HT / FIN）に、
 * シーズン・国・リーグ・seq を設定して保存する。試合単位で「最新の計算結果に置き換える」。
 * </p>
 * <ol>
 *   <li>シーズンを決める（取得できなければ何も保存せずに {@link SeasonNotResolvedException}）。</li>
 *   <li>試合の既存行の seq を引き、同じ時点（snapshot_type, goal_no）の行はその seq を使い回す。新しい時点だけ採番する。</li>
 *   <li>UPSERT する。</li>
 *   <li>今回の計算に無い既存行（ゴール取り消しで無くなった時点など）を削除する。</li>
 * </ol>
 * <p>
 * BM_M020（分類モード別の試合数）はビュー classify_result_data_detail がこのテーブルから数えるため、
 * 件数の加算処理・初期行作成（旧 init）は不要になった。同じ試合を何度処理しても件数は増えない。
 * </p>
 *
 * <h2>シーズン・seq</h2>
 * <ul>
 *   <li>シーズンは他の Writer と同じく {@link SeasonResolverIF} から取得する。1回の集計処理の間は国,リーグごとに
 *       スレッド単位でキャッシュする。呼び出し側は集計の開始時と終了時（finally）に {@link #clearSeasonCache()} を呼ぶこと。</li>
 *   <li>seq は「&lt;シーズン&gt;-&lt;6桁枝番&gt;」を {@link SeqNumberingService}（seq_counter）で採番する。</li>
 * </ul>
 *
 * <h2>トランザクション</h2>
 * <p>
 * 1試合＝1トランザクション（REQUIRES_NEW）。途中で失敗するとその試合の変更と採番はすべてロールバックされる。
 * 旧実装はリーグ単位の一括 INSERT と件数加算で、同じ試合が流れてくるたびに明細が重複し件数が増えていた。
 * </p>
 *
 * <h2>懸念点・エラーが起こりそうな箇所</h2>
 * <ul>
 *   <li><b>採番の待ち</b>: 同じシーズンの採番は seq_counter の同じ行を更新するため、新規行を含む試合の保存は1試合ずつ順番になる。</li>
 *   <li><b>同時実行で同じ試合を処理した場合</b>: 後の処理の番号が欠番になる（行の重複は起きない）。</li>
 *   <li><b>シーズンは処理日基準</b>・<b>同じ組み合わせの試合がシーズン内に2試合ある場合は後の試合で上書き</b>。</li>
 * </ul>
 */
@Service
public class MatchClassificationResultWriter {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = MatchClassificationResultWriter.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = MatchClassificationResultWriter.class.getName();

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M019_BM_M020";

	/** 採番単位のテーブル名 */
	private static final String TABLE_NAME = "classify_result_data";

	/** シーズン取得不可を表すキャッシュ値 */
	private static final String NOT_RESOLVED = "";

	/** 1回の集計処理中のシーズンキャッシュ（国 + リーグ → シーズン。取得不可は NOT_RESOLVED） */
	private static final ThreadLocal<Map<String, String>> SEASON_CACHE = ThreadLocal.withInitial(HashMap::new);

	@Autowired
	private MatchClassificationResultRepository matchClassificationResultRepository;

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
	 * 1試合分の行を保存する（既存行は置き換え、今回無い時点の行は削除）。
	 *
	 * @param country 国
	 * @param league リーグ
	 * @param rows 1試合分（ホーム・アウェーのチーム名、snapshotType、goalNo、classifyMode を設定済みであること）
	 * @throws SeasonNotResolvedException シーズンが取得できない場合（何も保存しない）
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void saveMatch(String country, String league, List<MatchClassificationResultEntity> rows) {
		final String METHOD_NAME = "saveMatch";
		if (rows == null || rows.isEmpty()) {
			return;
		}
		MatchClassificationResultEntity first = requireSingleMatch(rows);
		String season = resolveSeason(country, league);

		// 既存行の seq（時点キー → seq）
		Map<String, String> existingSeq = new HashMap<>();
		List<MatchClassificationResultEntity> existing = this.matchClassificationResultRepository.findSeqByMatchKey(
				season, country, league, first.getHomeTeamName(), first.getAwayTeamName());
		if (existing != null) {
			for (MatchClassificationResultEntity r : existing) {
				if (r != null && r.getSeq() != null) {
					existingSeq.put(snapshotKey(r), r.getSeq());
				}
			}
		}

		int count = 0;
		int numbered = 0;
		Set<String> savedKeys = new HashSet<>();
		for (MatchClassificationResultEntity row : rows) {
			if (row == null) {
				continue;
			}
			row.setSeason(season);
			row.setCountry(country);
			row.setLeague(league);

			String key = snapshotKey(row);
			String seq = existingSeq.get(key);
			if (seq == null) {
				seq = this.seqNumberingService.nextSeq(TABLE_NAME, season);
				numbered++;
			}
			row.setSeq(seq);

			int result = this.matchClassificationResultRepository.upsert(row);
			if (result != 1) {
				this.rootCauseWrapper.throwUnexpectedRowCount(
						PROJECT_NAME, CLASS_NAME, METHOD_NAME,
						MessageCdConst.MCD00007E_INSERT_FAILED,
						1, result,
						"seq=" + seq + ", " + setLoggerFillChar(season, country, league, row) + ", 時点=" + key);
			}
			savedKeys.add(key);
			count++;
		}

		// 今回の計算に無い時点の既存行を削除
		int deleted = 0;
		for (Map.Entry<String, String> e : existingSeq.entrySet()) {
			if (!savedKeys.contains(e.getKey())) {
				deleted += this.matchClassificationResultRepository.deleteBySeq(e.getValue());
			}
		}

		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME,
				MessageCdConst.MCD00005I_INSERT_SUCCESS,
				BM_NUMBER + " 登録/更新: " + count + "件（うち新規採番: " + numbered + "件）, 削除: " + deleted
						+ "件, 分類モード: " + first.getClassifyMode()
						+ " (" + setLoggerFillChar(season, country, league, first) + ")");
	}

	/** 時点キー（snapshot_type + goal_no） */
	private static String snapshotKey(MatchClassificationResultEntity e) {
		return e.getSnapshotType() + "#" + e.getGoalNo();
	}

	/**
	 * 1試合分の行がすべて同じ試合で、キー項目があり、時点が重複していないことを確認し、先頭の行を返す。
	 */
	private MatchClassificationResultEntity requireSingleMatch(List<MatchClassificationResultEntity> rows) {
		MatchClassificationResultEntity first = null;
		Set<String> keys = new HashSet<>();
		for (MatchClassificationResultEntity e : rows) {
			if (e == null) {
				continue;
			}
			if (isBlank(e.getHomeTeamName()) || isBlank(e.getAwayTeamName()) || isBlank(e.getSnapshotType())
					|| e.getGoalNo() == null || e.getClassifyMode() == null) {
				throw new IllegalArgumentException(BM_NUMBER + " キー項目が空の行があります: "
						+ e.getHomeTeamName() + " vs " + e.getAwayTeamName() + ", " + snapshotKey(e));
			}
			if (!keys.add(snapshotKey(e))) {
				throw new IllegalArgumentException(BM_NUMBER + " 同じ時点の行が重複しています: " + snapshotKey(e));
			}
			if (first == null) {
				first = e;
			} else if (!first.getHomeTeamName().equals(e.getHomeTeamName())
					|| !first.getAwayTeamName().equals(e.getAwayTeamName())) {
				throw new IllegalArgumentException(BM_NUMBER + " 1試合分に別の試合の行が混ざっています");
			}
		}
		if (first == null) {
			throw new IllegalArgumentException(BM_NUMBER + " 有効な行がありません");
		}
		return first;
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

	private static String setLoggerFillChar(String season, String country, String league,
			MatchClassificationResultEntity e) {
		return "シーズン: " + season + ", 国: " + country + ", リーグ: " + league
				+ ", ホーム: " + e.getHomeTeamName() + ", アウェー: " + e.getAwayTeamName();
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