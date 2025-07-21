package dev.application.analyze.bm_m023;

import java.lang.reflect.Field;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;
import jakarta.annotation.PostConstruct;

/**
 * score_based_featureのbeanロジック
 * @author shiraishitoshio
 *
 */
@Component
public class BmM023ScoreBasedFeatureBean {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = BmM023ScoreBasedFeatureBean.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = BmM023ScoreBasedFeatureBean.class.getSimpleName();

	/** 最小値 */
	private String[] minList = new String[AverageStatisticsSituationConst.COUNTER];

	/** 最大値 */
	private String[] maxList = new String[AverageStatisticsSituationConst.COUNTER];

	/** 平均値 */
	private String[] avgList = new String[AverageStatisticsSituationConst.COUNTER];

	/** 標準偏差 */
	private String[] sigmaList = new String[AverageStatisticsSituationConst.COUNTER];

	/** 件数 */
	private Integer[] cntList = new Integer[AverageStatisticsSituationConst.COUNTER];

	/** 最小値 */
	private String[] timeMinList = new String[AverageStatisticsSituationConst.COUNTER];

	/** 最大値 */
	private String[] timeMaxList = new String[AverageStatisticsSituationConst.COUNTER];

	/** 平均値 */
	private String[] timeAvgList = new String[AverageStatisticsSituationConst.COUNTER];

	/** 標準偏差 */
	private String[] timeSigmaList = new String[AverageStatisticsSituationConst.COUNTER];

	/** 件数 */
	private Integer[] timeCntList = new Integer[AverageStatisticsSituationConst.COUNTER];

	/** 開始 */
	private int startIdx;

	/** 終了 */
	private int endIdx;

	/** 開始 */
	private int startInsertIdx;

	/** 終了 */
	private int endInsertIdx;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent loggerComponent;

	/** 初期化 */
	@PostConstruct
	public void init() {
		final String METHOD_NAME = "init";
		// 全フィールド取得（※順序は保証されない可能性あり）
		Field[] allFields = BookDataEntity.class.getDeclaredFields();
		// 分析対象のフィールド範囲（homeExp 〜 awayInterceptCount）
		int startIdx = -1;
		int endIdx = -1;
		for (int i = 0; i < allFields.length; i++) {
			String name = allFields[i].getName();
			if (name.equals("homeExp"))
				startIdx = i;
			if (name.equals("awayInterceptCount"))
				endIdx = i;
		}

		if (startIdx == -1 || endIdx == -1 || startIdx > endIdx) {
			String fillChar = "startIdx: " + startIdx + ", endIdx: " + endIdx;
			String messageCd = "初期化エラー: 対象フィールド範囲なし";
			this.loggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null, fillChar);
			this.loggerComponent.createBusinessException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					messageCd,
					null);
		}
		// 初期化
		for (int cnt = 0; cnt < AverageStatisticsSituationConst.COUNTER; cnt++) {
			this.minList[cnt] = "10000.0";
			this.maxList[cnt] = "0.0";
			this.avgList[cnt] = "0.0";
			this.sigmaList[cnt] = "0.0";
			this.cntList[cnt] = 0;
			this.timeMinList[cnt] = "0'";
			this.timeMaxList[cnt] = "0'";
			this.timeAvgList[cnt] = "0'";
			this.timeSigmaList[cnt] = "0'";
			this.timeCntList[cnt] = 0;
		}
		// 開始情報
		this.startIdx = startIdx;
		// 終了情報
		this.endIdx = endIdx;

		// 全フィールド取得（※順序は保証されない可能性あり）
		Field[] insertFields = ScoreBasedFeatureStatsEntity.class.getDeclaredFields();
		// 分析対象のフィールド範囲（homeExp 〜 awayInterceptCount）
		int startInsertIdx = -1;
		int endInsertIdx = -1;
		for (int i = 0; i < insertFields.length; i++) {
			String name = insertFields[i].getName();
			if (name.equals("homeExpStat"))
				startInsertIdx = i;
			if (name.equals("awayInterceptCountStat"))
				endInsertIdx = i;
		}
		// 開始情報
		this.startInsertIdx = startInsertIdx;
		// 終了情報
		this.endInsertIdx = endInsertIdx;
	}

	/**
	 * 最小値リストを返却
	 * @return minList
	 */
	public String[] getMinList() {
		return minList;
	}

	/**
	 * 最大値リストを返却
	 * @return maxList
	 */
	public String[] getMaxList() {
		return maxList;
	}

	/**
	 * 平均値リストを返却
	 * @return avgList
	 */
	public String[] getAvgList() {
		return avgList;
	}

	/**
	 * 標準偏差リストを返却
	 * @return sigmaList
	 */
	public String[] getSigmaList() {
		return sigmaList;
	}

	/**
	 * 件数リストを返却
	 * @return cntList
	 */
	public Integer[] getCntList() {
		return cntList;
	}

	/**
	 * 時間の最小値リストを返却
	 * @return timeMinList
	 */
	public String[] getTimeMinList() {
		return timeMinList;
	}

	/**
	 * 時間の最大値リストを返却
	 * @return timeMaxList
	 */
	public String[] getTimeMaxList() {
		return timeMaxList;
	}

	/**
	 * 時間の平均値リストを返却
	 * @return timeAvgList
	 */
	public String[] getTimeAvgList() {
		return timeAvgList;
	}

	/**
	 * 時間の標準偏差リストを返却
	 * @return timeSigmaList
	 */
	public String[] getTimeSigmaList() {
		return timeSigmaList;
	}

	/**
	 * 時間の件数リストを返却
	 * @return timeCntList
	 */
	public Integer[] getTimeCntList() {
		return timeCntList;
	}

	/**
	 * 開始情報を返却
	 * @return startIdx
	 */
	public int getStartIdx() {
		return startIdx;
	}

	/**
	 * 終了情報を返却
	 * @return endIdx
	 */
	public int getEndIdx() {
		return endIdx;
	}

	/**
	 * 開始情報を返却
	 * @return startInsertIdx
	 */
	public int getStartInsertIdx() {
		return startInsertIdx;
	}

	/**
	 * 終了情報を返却
	 * @return endInsertIdx
	 */
	public int getEndInsertIdx() {
		return endInsertIdx;
	}

}
