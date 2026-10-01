package dev.application.analyze.bm_m031;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.common.error.AnalyzeErrorInfo;
import dev.application.analyze.common.service.AbstractSeasonResolvingWriter;
import dev.application.analyze.common.service.SeqNumberingService;
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
 * 一意キー（シーズン・国・リーグ・ラウンド・チーム・対戦相手・H/A）が既にあれば上書きするため、同じ試合を再処理しても行は増えない。
 * 欠けていた試合を後から入れた場合も、この1試合分の行が増えるだけで、連続記録・順位はビューが計算し直す。
 * </p>
 *
 * <h2>シーズン・seq</h2>
 * <ul>
 *   <li>シーズンは他の Writer と同じく SeasonResolverIF から取得する。1回の集計処理の間は国,リーグごとに
 *       スレッド単位でキャッシュする。呼び出し側は集計の開始時と終了時（finally）に clearSeasonCache() を呼ぶこと。</li>
 *   <li>seq は「&lt;シーズン&gt;-&lt;6桁枝番&gt;」を {@link SeqNumberingService}（seq_counter）で採番する。
 *       既に行があるときはその seq を使い、採番しない。</li>
 *   <li>総ラウンド数はシーズンが決まってから {@link BmM031SurfaceOverviewBean} で引く（シーズンごとに違ってもよい）。</li>
 * </ul>
 *
 * <h2>トランザクション</h2>
 * <p>1試合（2行）＝1トランザクション（REQUIRES_NEW）。片方だけ保存された状態は残らない。</p>
 *
 * <h2>シーズンが取得できない試合</h2>
 * <p>
 * 【変更】シーズンの取得は共通の親クラス AbstractSeasonResolvingWriter で行う。取得できない試合は analyze_error_match に
 * 記録してから SeasonNotResolvedException を投げる（何も保存しない）。Stat はこの例外を捕まえてその試合だけスキップする。
 * </p>
 *
 * <h2>懸念点・エラーが起こりそうな箇所</h2>
 * <ul>
 *   <li><b>採番の待ち</b>: 同じシーズンの採番は seq_counter の同じ行を更新するため、新規行を含む試合の保存は1試合ずつ順番になる。</li>
 *   <li><b>同時実行で同じ試合を処理した場合</b>: 後の処理の番号が欠番になる（行の重複は起きない）。</li>
 *   <li><b>シーズンは処理日基準</b>（SeasonResolverIF の実装による）。過去シーズンの欠け試合を今のシーズン中に入れると、
 *       今のシーズンとして保存される。過去シーズンを入れ直す場合はシーズンの決め方を確認すること。</li>
 *   <li>【変更】一意キーにラウンド番号を追加した。同じ組み合わせ（同じ H/A）の試合がシーズン内に複数回あるリーグ
 *       （スイス・スコットランドなど）でも、ラウンドが違えば別の行になる（以前は後の試合で上書きされていた）。</li>
 * </ul>
 */
@Service
public class SurfaceOverviewWriter extends AbstractSeasonResolvingWriter {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = SurfaceOverviewWriter.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = SurfaceOverviewWriter.class.getName();

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M031";

	/** 採番単位のテーブル名 */
	private static final String TABLE_NAME = "surface_overview_match";

	@Autowired
	private SurfaceOverviewMatchRepository surfaceOverviewMatchRepository;

	/** 総ラウンド数 */
	@Autowired
	private BmM031SurfaceOverviewBean surfaceOverviewBean;

	/** seq 採番（seq_counter） */
	@Autowired
	private SeqNumberingService seqNumberingService;

	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected String getBmNumber() {
		return BM_NUMBER;
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
					|| isBlank(row.getResult()) || row.getRoundNo() == null) {
				throw new IllegalArgumentException(BM_NUMBER + " キー項目が空の行があります");
			}
		}

		// DB 書き込みの前にシーズンを決める（取得できなければ何も保存せずに例外）
		SurfaceOverviewMatchEntity firstRow = rows.get(0);
		boolean home = "H".equals(firstRow.getHa());
		String season = resolveSeason(country, league, AnalyzeErrorInfo.match(firstRow.getDataCategory(),
				home ? firstRow.getTeam() : firstRow.getOpponent(),
				home ? firstRow.getOpponent() : firstRow.getTeam())
				.matchId(firstRow.getMatchId()));
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
					season, country, league, row.getRoundNo(), row.getTeam(), row.getOpponent(), row.getHa());
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

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}

	private static String setLoggerFillChar(SurfaceOverviewMatchEntity e) {
		return "シーズン: " + e.getSeason() + ", 国: " + e.getCountry() + ", リーグ: " + e.getLeague()
				+ ", チーム: " + e.getTeam() + ", 対戦: " + e.getOpponent() + ", H/A: " + e.getHa()
				+ ", ラウンド: " + e.getRoundNo() + ", 結果: " + e.getResult()
				+ " " + e.getGoalsFor() + "-" + e.getGoalsAgainst();
	}

}
