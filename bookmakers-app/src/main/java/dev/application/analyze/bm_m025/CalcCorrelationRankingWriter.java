package dev.application.analyze.bm_m025;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.application.domain.repository.bm.CalcCorrelationRankingRepository;
import dev.common.constant.MessageCdConst;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M025 DB永続化専用サービス
 * 登録・更新などDB処理はこのクラスに集約し、個別トランザクションで実行する。
 *
 * @author shiraishitoshio
 */
@Service
public class CalcCorrelationRankingWriter {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = CalcCorrelationRankingWriter.class
			.getProtectionDomain().getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = CalcCorrelationRankingWriter.class.getName();

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M025";

	/** CalcCorrelationRankingRepositoryレポジトリクラス */
	@Autowired
	private CalcCorrelationRankingRepository calcCorrelationRankingRepository;

	/** ログ管理ラッパー */
	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 登録メソッド
	 * 1件ごとに独立トランザクションで実行する。
	 *
	 * @param entity エンティティ
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public void insert(CalcCorrelationRankingEntity entity) {
		final String METHOD_NAME = "insert";

		String fillChar = buildFillChar(entity);

		int result = this.calcCorrelationRankingRepository.insert(entity);

		if (result != 1) {
			String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
			this.rootCauseWrapper.throwUnexpectedRowCount(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME,
					messageCd, 1, result, fillChar);
		}

		String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd,
				BM_NUMBER + " 登録件数: " + result + "件 (" + fillChar + ")");
	}

	/**
	 * 将来 update が必要になった場合はこのクラスへ追加する。
	 *
	 * 例:
	 *
	 * @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	 * public void update(CalcCorrelationRankingEntity entity) {
	 *     final String METHOD_NAME = "update";
	 *     String fillChar = buildFillChar(entity);
	 *     int result = this.calcCorrelationRankingRepository.update(entity);
	 *     if (result != 1) {
	 *         String messageCd = MessageCdConst.MCD00008E_UPDATE_FAILED;
	 *         this.rootCauseWrapper.throwUnexpectedRowCount(
	 *                 PROJECT_NAME, CLASS_NAME, METHOD_NAME,
	 *                 messageCd, 1, result, fillChar);
	 *     }
	 * }
	 */

	/**
	 * 埋め字設定
	 *
	 * @param chkBody 調査内容
	 * @param score スコア
	 * @param country 国
	 * @param league リーグ
	 * @param home ホーム
	 * @param away アウェー
	 * @return 埋め字
	 */
	private String setLoggerFillChar(String chkBody, String score,
			String country, String league, String home, String away) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("調査内容: ").append(safe(chkBody)).append(", ");
		stringBuilder.append("スコア: ").append(safe(score)).append(", ");
		stringBuilder.append("国: ").append(safe(country)).append(", ");
		stringBuilder.append("リーグ: ").append(safe(league)).append(", ");
		stringBuilder.append("ホーム: ").append(safe(home)).append(", ");
		stringBuilder.append("アウェー: ").append(safe(away));
		return stringBuilder.toString();
	}

	/**
	 * エンティティから埋め字生成
	 *
	 * @param entity エンティティ
	 * @return 埋め字
	 */
	private String buildFillChar(CalcCorrelationRankingEntity entity) {
		if (entity == null) {
			return "entity=null";
		}
		return setLoggerFillChar(
				entity.getChkBody(),
				entity.getScore(),
				entity.getCountry(),
				entity.getLeague(),
				entity.getHome(),
				entity.getAway());
	}

	/**
	 * null safe
	 *
	 * @param value 値
	 * @return 非null文字列
	 */
	private String safe(String value) {
		return value == null ? "" : value;
	}
}
