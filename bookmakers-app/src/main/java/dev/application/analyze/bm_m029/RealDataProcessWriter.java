package dev.application.analyze.bm_m029;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.analyze.common.error.AnalyzeErrorInfo;
import dev.application.analyze.common.service.AbstractSeasonResolvingWriter;
import dev.application.analyze.common.service.SeqNumberingService;
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
 * <h2>シーズン・seq</h2>
 * <ul>
 *   <li>シーズンは他の Writer と同じく SeasonResolverIF から取得する。1回の処理の間は国,リーグごとに
 *       スレッド単位でキャッシュする。呼び出し側は処理の開始時と終了時（finally）に clearSeasonCache() を呼ぶこと。</li>
 *   <li>seq は「&lt;シーズン&gt;-&lt;6桁枝番&gt;」を {@link SeqNumberingService}（seq_counter）で採番する。
 *       同じ match_id の行が既にあればその seq を使い、採番しない（上書きのたびに番号を消費しない）。</li>
 * </ul>
 *
 * <h2>トランザクション</h2>
 * <p>1試合＝1トランザクション（REQUIRES_NEW）。失敗すると採番も含めてロールバックされる。</p>
 *
 * <h2>シーズンが取得できない試合</h2>
 * <p>
 * 【変更】シーズンの取得は共通の親クラス AbstractSeasonResolvingWriter で行う。取得できない試合は analyze_error_match に
 * 記録してから SeasonNotResolvedException を投げる（何も保存しない）。Stat はこの例外を捕まえてその試合だけスキップする。
 * </p>
 *
 * <h2>懸念点・エラーが起こりそうな箇所</h2>
 * <ul>
 *   <li><b>採番の待ち</b>: 同じシーズンの採番は seq_counter の同じ行を更新するため、新規の試合の保存は1試合ずつ順番になる
 *       （上書きは採番しないので待たない）。</li>
 *   <li><b>同時実行で同じ試合を2つの処理が新規として採番した場合</b>: 後の処理は更新になり、後の番号は欠番になる（行の重複は起きない）。</li>
 *   <li><b>シーズンは処理日基準</b>（SeasonResolverIF の実装による）。</li>
 * </ul>
 */
@Service
public class RealDataProcessWriter extends AbstractSeasonResolvingWriter {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = RealDataProcessWriter.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = RealDataProcessWriter.class.getName();

	/** BM番号 */
	private static final String BM_NUMBER = "BM_M029";

	/** 採番単位のテーブル名 */
	private static final String TABLE_NAME = "real_data_process";

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
	 * {@inheritDoc}
	 */
	@Override
	protected String getBmNumber() {
		return BM_NUMBER;
	}

	/**
	 * 1試合分の差分を match_id をキーに UPSERT する。
	 *
	 * @param country 国
	 * @param league リーグ
	 * @param entity 差分（matchId・チーム名・dataCategory 設定済みであること）
	 * @throws SeasonNotResolvedException シーズンが取得できない場合（何も保存しない）
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void save(String country, String league, RealDataProcessEntity entity) {
		final String METHOD_NAME = "save";
		if (entity == null || isBlank(entity.getMatchId()) || isBlank(entity.getHomeTeamName())
				|| isBlank(entity.getAwayTeamName()) || isBlank(entity.getDataCategory())) {
			throw new IllegalArgumentException(BM_NUMBER + " キー項目（matchId・チーム名・dataCategory）が空です");
		}

		// DB 書き込みの前にシーズンを決める（取得できなければ何も保存せずに例外）
		String season = resolveSeason(country, league, AnalyzeErrorInfo.match(entity.getDataCategory(),
				entity.getHomeTeamName(), entity.getAwayTeamName()).matchId(entity.getMatchId()));
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

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}

	private static String setLoggerFillChar(RealDataProcessEntity e) {
		return "シーズン: " + e.getSeason() + ", 国: " + e.getCountry() + ", リーグ: " + e.getLeague()
				+ ", ホーム: " + e.getHomeTeamName() + ", アウェー: " + e.getAwayTeamName()
				+ ", 区間: " + e.getPrevTimes() + "〜" + e.getTimes() + ", matchId: " + e.getMatchId();
	}

}
