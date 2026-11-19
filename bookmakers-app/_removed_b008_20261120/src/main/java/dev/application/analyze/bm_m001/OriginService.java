package dev.application.analyze.bm_m001;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.analyze.bm_m029.RealDataProcessStat;
import dev.application.analyze.interf.OriginIF;
import dev.common.constant.MessageCdConst;
import dev.common.entity.DataEntity;
import dev.common.getinfo.GetOriginInfo;
import dev.common.logger.ManageLoggerComponent;

/**
 * BM_M001統計分析ロジック
 *
 * <p>2026-10 追加: リアルタイムデータの試合時間から「中断」「延期」「中止」「再開」を検知して
 * delay_postpone_＜日付＞.json を出力する（{@link SuspensionStatusPublisher}）。
 * 延期の出力を同じ実行の start_flg の戻し（{@link FutureStartFlgService}）に反映するため、未来データフラグ更新の前に行う。</p>
 * @author shiraishitoshio
 *
 */
@Component
public class OriginService implements OriginIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = OriginService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = OriginService.class.getName();

	/**
	 * 起源データ取得管理クラス
	 */
	@Autowired
	private GetOriginInfo getOriginInfo;

	/**
	 * BM_M001起源データ登録ロジック
	 */
	@Autowired
	private OriginStat originStat;

	/**
	 * BM_M029起源データ過程登録ロジック
	 */
	@Autowired
	private RealDataProcessStat realDataProcessStat;

	/**
	 * 中断・延期などの検知（delay_postpone 出力）
	 */
	@Autowired
	private SuspensionStatusPublisher suspensionStatusPublisher;

	/**
	 * 未来データフラグサービス
	 */
	@Autowired
	private FutureStartFlgService futureStartFlgService;

	/**
	 * ログ管理クラス
	 */
	@Autowired
	private ManageLoggerComponent loggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int execute() throws Exception {
		final String METHOD_NAME = "execute";

		// ログ出力
		this.loggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// 直近のCSVデータ情報を取得
		Map<String, List<DataEntity>> getStatMap = this.getOriginInfo.getData(null);
		// BM_M001登録(Transactional)
		try {
			this.originStat.originStat(getStatMap);
		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
			this.loggerComponent.createSystemException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					messageCd,
					null,
					e);
		}

		// BM_M029登録・更新(Transactional)
		try {
			this.realDataProcessStat.calcStat(getStatMap);
		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
			this.loggerComponent.createSystemException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					messageCd,
					null,
					e);
		}

		// 中断・延期・中止・再開の検知（delay_postpone 出力。失敗しても例外は投げない）
		try {
			this.suspensionStatusPublisher.publish(getStatMap);
		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
			this.loggerComponent.createSystemException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					messageCd,
					null,
					e);
		}

		// 未来データフラグ更新
		try {
			this.futureStartFlgService.execute(getStatMap);
		} catch (Exception e) {
			String messageCd = MessageCdConst.MCD00099E_UNEXPECTED_EXCEPTION;
			this.loggerComponent.createSystemException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					messageCd,
					null,
					e);
		}

		// endLog
		this.loggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		return 0;
	}

}
