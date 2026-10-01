package dev.application.analyze.bm_m021;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.common.error.AnalyzeErrorInfo;
import dev.application.analyze.common.service.AbstractSeasonResolvingWriter;
import dev.application.analyze.common.service.SeqNumberingService;
import dev.application.domain.repository.bm.TeamMatchFinalStatsRepository;
import dev.common.constant.MessageCdConst;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M021 登録処理（team_match_final_stats）。
 *
 * <h2>何をするクラスか</h2>
 * <p>
 * {@link TeamMatchFinalStat} が作った1試合分の2行（ホーム視点・アウェー視点）に、シーズン・国・リーグ・seq を設定して UPSERT する。
 * 一意キー（シーズン・国・リーグ・チーム・対戦チーム・H/A）が既にあれば上書きするため、同じ試合を再処理しても行は増えない。
 * 以前はただの INSERT で、試合終了後のデータが流れてくるたびに同じ試合の行が増えていた。
 * </p>
 *
 * <h2>シーズン・seq</h2>
 * <ul>
 *   <li>シーズンは他の Writer と同じく SeasonResolverIF から取得する。1回の集計処理の間は国,リーグごとに
 *       スレッド単位でキャッシュする。呼び出し側は集計の開始時と終了時（finally）に clearSeasonCache() を呼ぶこと。</li>
 *   <li>seq は「&lt;シーズン&gt;-&lt;6桁枝番&gt;」を {@link SeqNumberingService}（seq_counter）で採番する。
 *       既に行があるときはその seq を使い、採番しない。</li>
 * </ul>
 *
 * <h2>トランザクション</h2>
 * <p>
 * 1試合（2行）＝1トランザクション（REQUIRES_NEW）。片方だけ保存された状態は残らない。
 * </p>
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
 *   <li><b>シーズンは処理日基準</b>・<b>同じ組み合わせの試合がシーズン内に2試合ある場合は後の試合で上書き</b>。</li>
 * </ul>
 */
@Service
public class TeamMatchFinalWriter extends AbstractSeasonResolvingWriter {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = TeamMatchFinalWriter.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = TeamMatchFinalWriter.class.getName();

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M021";

	/** 採番単位のテーブル名 */
	private static final String TABLE_NAME = "team_match_final_stats";

	@Autowired
	private TeamMatchFinalStatsRepository teamMatchFinalStatsRepository;

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
	 * @param roundNo ラウンド番号（キーの「ラウンド N」）
	 * @param rows 1試合分（チーム名・対戦チーム名・H/A を設定済みであること）
	 * @throws SeasonNotResolvedException シーズンが取得できない場合（何も保存しない）
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void saveMatch(String country, String league, Integer roundNo, List<TeamMatchFinalStatsEntity> rows) {
		final String METHOD_NAME = "saveMatch";
		if (rows == null || rows.isEmpty()) {
			return;
		}
		if (roundNo == null) {
			throw new IllegalArgumentException(BM_NUMBER + " roundNo is null.");
		}
		for (TeamMatchFinalStatsEntity row : rows) {
			if (row == null || isBlank(row.getTeamName()) || isBlank(row.getVersusTeamName()) || isBlank(row.getHa())) {
				throw new IllegalArgumentException(BM_NUMBER + " キー項目が空の行があります");
			}
		}

		// DB 書き込みの前にシーズンを決める（取得できなければ何も保存せずに例外）
		TeamMatchFinalStatsEntity firstRow = rows.get(0);
		boolean home = "H".equals(firstRow.getHa());
		String season = resolveSeason(country, league, AnalyzeErrorInfo.match(null,
				home ? firstRow.getTeamName() : firstRow.getVersusTeamName(),
				home ? firstRow.getVersusTeamName() : firstRow.getTeamName())
				.matchId(firstRow.getMatchId()));

		int numbered = 0;
		for (TeamMatchFinalStatsEntity row : rows) {
			row.setSeason(season);
			row.setCountry(country);
			row.setLeague(league);
			row.setRoundNo(roundNo);

			String seq = this.teamMatchFinalStatsRepository.findSeq(
					season, country, league, roundNo, row.getTeamName(), row.getVersusTeamName(), row.getHa());
			if (seq == null) {
				seq = this.seqNumberingService.nextSeq(TABLE_NAME, season);
				numbered++;
			}
			row.setSeq(seq);

			int result = this.teamMatchFinalStatsRepository.upsert(row);
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

	private static String setLoggerFillChar(TeamMatchFinalStatsEntity e) {
		return "シーズン: " + e.getSeason() + ", 国: " + e.getCountry() + ", リーグ: " + e.getLeague()
				+ ", チーム: " + e.getTeamName() + ", 対戦: " + e.getVersusTeamName() + ", H/A: " + e.getHa()
				+ ", スコア: " + e.getScore();
	}

}
