package dev.application.analyze.bm_m028;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.domain.repository.bm.PastRankingRepository;
import dev.common.constant.MessageCdConst;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;

/**
 * # BM_M028 統計分析ロジック（過去順位管理 集計）
 *
 * <p>入力された全試合データ（CSV由来）を走査し、月次・チーム単位の
 * 過去順位を集計します。</p>
 *
 * <h3>主な仕様</h3>
 * <ul>
 *   <li>集計キーは {@code country|league|gameYear|gameMonth|team}。</li>
 *   <li>同一キーが同一バッチ中に複数回現れた場合は、
 *       DBではなく {@code resultMap} にある途中値を再利用し、逐次加算。</li>
 *   <li>順位、勝ち、負け、引き分けなどを登録。</li>
 * </ul>
 *
 * @author shiraishitoshio
 */
@Component
public class PastRankingStat {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = PastRankingStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = PastRankingStat.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M028_PAST_RANKING";

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M028";

	/** PastRankingRepository */
	@Autowired
	private PastRankingRepository pastRankingRepository;

	/** ログ管理ラッパー*/
	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	/** ロガー */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * surface_overviewから預かったEntityを更新
	 * @param param surface_overviewから預かったEntityParam
	 * @throws Exception
	 */
	public void executeStat(PastRankingQueryParam param) throws Exception {
		final String METHOD_NAME = "executeStat";
		manageLoggerComponent.init(EXEC_MODE, null);
		manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		PastRankingEntity entity = PastRankingMapper.toEntity(param);
		insert(entity);

		manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		manageLoggerComponent.clear();
	}

	/**
	 * insertメソッド
	 * @param entity
	 */
	private void insert(PastRankingEntity entity) {
		final String METHOD_NAME = "insert";
		String fillChar = setLoggerFillChar(
				entity.getCountry(),
				entity.getLeague(),
				entity.getSeasonYear(),
				entity.getTeam(),
				String.valueOf(entity.getMatch()));
		int result = this.pastRankingRepository.insert(entity);
		if (result == 0) {
			String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
        	this.rootCauseWrapper.throwUnexpectedRowCount(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                    messageCd, 1, result, fillChar);
		}

		String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, BM_NUMBER + " 登録件数: " + result + "件 (" + fillChar + ")");
	}

	/**
	 * 埋め字設定
	 * @param country 国
	 * @param league リーグ
	 * @param seasonYear シーズン
	 * @param team チーム
	 * @param match 節
	 * @return
	 */
	private String setLoggerFillChar(String country, String league, String seasonYear, String team, String match) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("国: " + country + ", ");
		stringBuilder.append("リーグ: " + league + ", ");
		stringBuilder.append("シーズン: " + seasonYear + ", ");
		stringBuilder.append("チーム: " + team);
		stringBuilder.append("節: " + match);
		return stringBuilder.toString();
	}

}
