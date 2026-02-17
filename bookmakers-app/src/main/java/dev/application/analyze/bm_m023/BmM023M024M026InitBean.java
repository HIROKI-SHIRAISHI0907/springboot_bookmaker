package dev.application.analyze.bm_m023;

import java.lang.reflect.Field;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.analyze.bm_m024.CalcCorrelationEntity;
import dev.application.analyze.bm_m026.EachTeamScoreBasedFeatureEntity;
import dev.common.constant.MessageCdConst;
import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;
import jakarta.annotation.PostConstruct;

/**
 * score_based_feature, correlation関係のbeanロジック
 * @author shiraishitoshio
 *
 */
@Component
public class BmM023M024M026InitBean {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = BmM023M024M026InitBean.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = BmM023M024M026InitBean.class.getName();

	/** 最小値 */
	private String[] minList = new String[AverageStatisticsSituationConst.COUNTER];

	/** 最大値 */
	private String[] maxList = new String[AverageStatisticsSituationConst.COUNTER];

	/** 平均値 */
	private String[] avgList = new String[AverageStatisticsSituationConst.COUNTER];

	/** 標準偏差 */
	private String[] sigmaList = new String[AverageStatisticsSituationConst.COUNTER];

	/** 歪度(正規分布の釣鐘型がどの左右に歪んでいるか(+なら左寄りに歪んでいる)) */
	private String[] skewnessList = new String[AverageStatisticsSituationConst.COUNTER];

	/** 尖度(正規分布の釣鐘型がどの程度尖っているか(+なら尖っている)) */
	private String[] kurtosisList = new String[AverageStatisticsSituationConst.COUNTER];

	/** 件数 */
	private Integer[] skewnessCntList = new Integer[AverageStatisticsSituationConst.SPLIT_COUNTER];

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

	/** 歪度(正規分布の釣鐘型がどの左右に歪んでいるか(+なら左寄りに歪んでいる)) */
	private String[] timeSkewnessList = new String[AverageStatisticsSituationConst.COUNTER];

	/** 尖度(正規分布の釣鐘型がどの程度尖っているか(+なら尖っている)) */
	private String[] timeKurtosisList = new String[AverageStatisticsSituationConst.COUNTER];

	/** 件数 */
	private Integer[] timeCntList = new Integer[AverageStatisticsSituationConst.COUNTER];

	/** 最小値 */
	private String[] minSplitList = new String[AverageStatisticsSituationConst.SPLIT_COUNTER];

	/** 最大値 */
	private String[] maxSplitList = new String[AverageStatisticsSituationConst.SPLIT_COUNTER];

	/** 平均値 */
	private String[] avgSplitList = new String[AverageStatisticsSituationConst.SPLIT_COUNTER];

	/** 標準偏差 */
	private String[] sigmaSplitList = new String[AverageStatisticsSituationConst.SPLIT_COUNTER];

	/** 件数 */
	private Integer[] cntSplitList = new Integer[AverageStatisticsSituationConst.SPLIT_COUNTER];

	/** 最小値 */
	private String[] timeMinSplitList = new String[AverageStatisticsSituationConst.SPLIT_COUNTER];

	/** 最大値 */
	private String[] timeMaxSplitList = new String[AverageStatisticsSituationConst.SPLIT_COUNTER];

	/** 平均値 */
	private String[] timeAvgSplitList = new String[AverageStatisticsSituationConst.SPLIT_COUNTER];

	/** 標準偏差 */
	private String[] timeSigmaSplitList = new String[AverageStatisticsSituationConst.SPLIT_COUNTER];

	/** 歪度(正規分布の釣鐘型がどの左右に歪んでいるか(+なら左寄りに歪んでいる)) */
	private String[] timeSkewnessSplitList = new String[AverageStatisticsSituationConst.SPLIT_COUNTER];

	/** 尖度(正規分布の釣鐘型がどの程度尖っているか(+なら尖っている)) */
	private String[] timeKurtosisSplitList = new String[AverageStatisticsSituationConst.SPLIT_COUNTER];

	/** 件数 */
	private Integer[] timeCntSplitList = new Integer[AverageStatisticsSituationConst.SPLIT_COUNTER];

	/** 相関係数 */
	private String[] corrList = new String[AverageStatisticsSituationConst.SPLIT_COUNTER];

	/** 開始 */
	private int startIdx;

	/** 終了 */
	private int endIdx;

	/** 開始 */
	private int startInsertIdx;

	/** 終了 */
	private int endInsertIdx;

	/** 開始 */
	private int startScoreInsertIdx;

	/** 終了 */
	private int endScoreInsertIdx;

	/** 開始 */
	private int startCalcInsertIdx;

	/** 終了 */
	private int endCalcInsertIdx;

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
			String messageCd = MessageCdConst.MCD00013E_INITILIZATION_ERROR;
			String fillChar = "対象フィールド範囲なし";
			this.loggerComponent.debugErrorLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null, fillChar);
			this.loggerComponent.createBusinessException(
					PROJECT_NAME,
					CLASS_NAME,
					METHOD_NAME,
					messageCd,
					null, null);
		}
		// 初期化
		for (int cnt = 0; cnt < AverageStatisticsSituationConst.COUNTER; cnt++) {
			this.minList[cnt] = "10000.0";
			this.maxList[cnt] = "0.0";
			this.avgList[cnt] = "0.0";
			this.sigmaList[cnt] = "0.0";
			this.cntList[cnt] = 0;
			this.timeMinList[cnt] = "10000'";
			this.timeMaxList[cnt] = "0'";
			this.timeAvgList[cnt] = "0'";
			this.timeSigmaList[cnt] = "0'";
			this.timeCntList[cnt] = 0;
		}
		// 開始情報
		this.startIdx = startIdx;
		// 終了情報
		this.endIdx = endIdx;

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

		// 全フィールド取得（※順序は保証されない可能性あり）
		Field[] insertScoreFields = EachTeamScoreBasedFeatureEntity.class.getDeclaredFields();
		// 分析対象のフィールド範囲（homeExp 〜 awayInterceptCount）
		int startScoreInsertIdx = -1;
		int endScoreInsertIdx = -1;
		for (int i = 0; i < insertScoreFields.length; i++) {
			String name = insertScoreFields[i].getName();
			if (name.equals("homeExpStat"))
				startScoreInsertIdx = i;
			if (name.equals("awayInterceptCountStat"))
				endScoreInsertIdx = i;
		}
		// 開始情報
		this.startScoreInsertIdx = startScoreInsertIdx;
		// 終了情報
		this.endScoreInsertIdx = endScoreInsertIdx;

		Field[] insertSubFields = CalcCorrelationEntity.class.getDeclaredFields();
		// 分析対象のフィールド範囲（homeExpInfo 〜 awayInterceptCountInfo）
		int startCalcInsertIdx = -1;
		int endCalcInsertIdx = -1;
		for (int i = 0; i < insertSubFields.length; i++) {
			String name = insertSubFields[i].getName();
			if (name.equals("homeExpInfo"))
				startCalcInsertIdx = i;
			if (name.equals("awayInterceptCountInfo"))
				endCalcInsertIdx = i;
		}
		// 開始情報
		this.startCalcInsertIdx = startCalcInsertIdx;
		// 終了情報
		this.endCalcInsertIdx = endCalcInsertIdx;

		for (int cnt = 0; cnt < AverageStatisticsSituationConst.COUNTER; cnt++) {
			this.skewnessCntList[cnt] = 0;
		}
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
	 * 時間の歪度リストを返却
	 * @return timeSkewnessList
	 */
	public String[] getTimeSkewnessList() {
		return timeSkewnessList;
	}

	/**
	 * 時間の尖度リストを返却
	 * @return timeKurtosisList
	 */
	public String[] getTimeKurtosisList() {
		return timeKurtosisList;
	}

	/**
	 * 時間の件数リストを返却
	 * @return timeCntList
	 */
	public Integer[] getTimeCntList() {
		return timeCntList;
	}

	/**
	 * 最小値リストを返却
	 * @return minSplitList
	 */
	public String[] getMinSplitList() {
		return minSplitList;
	}

	/**
	 * 最大値リストを返却
	 * @return maxSplitList
	 */
	public String[] getMaxSplitList() {
		return maxSplitList;
	}

	/**
	 * 平均値リストを返却
	 * @return avgSplitList
	 */
	public String[] getAvgSplitList() {
		return avgSplitList;
	}

	/**
	 * 標準偏差リストを返却
	 * @return sigmaSplitList
	 */
	public String[] getSigmaSplitList() {
		return sigmaSplitList;
	}

	/**
	 * 歪度リストを返却
	 * @return skewnessList
	 */
	public String[] getSkewnessList() {
		return skewnessList;
	}

	/**
	 * 尖度リストを返却
	 * @return kurtosisList
	 */
	public String[] getKurtosisList() {
		return kurtosisList;
	}

	/**
	 * 件数リストを返却
	 * @return cntSplitList
	 */
	public Integer[] getCntSplitList() {
		return cntSplitList;
	}

	/**
	 * 時間の最小値リストを返却
	 * @return timeMinSplitList
	 */
	public String[] getTimeMinSplitList() {
		return timeMinSplitList;
	}

	/**
	 * 時間の最大値リストを返却
	 * @return timeMaxSplitList
	 */
	public String[] getTimeMaxSplitList() {
		return timeMaxSplitList;
	}

	/**
	 * 時間の平均値リストを返却
	 * @return timeAvgSplitList
	 */
	public String[] getTimeAvgSplitList() {
		return timeAvgSplitList;
	}

	/**
	 * 時間の標準偏差リストを返却
	 * @return timeSigmaSplitList
	 */
	public String[] getTimeSigmaSplitList() {
		return timeSigmaSplitList;
	}

	/**
	 * 時間の歪度リストを返却
	 * @return timeSkewnessSplitList
	 */
	public String[] getTimeSkewnessSplitList() {
		return timeSkewnessSplitList;
	}

	/**
	 * 時間の尖度リストを返却
	 * @return timeKurtosisSplitList
	 */
	public String[] getTimeKurtosisSplitList() {
		return timeKurtosisSplitList;
	}

	/**
	 * 時間の件数リストを返却
	 * @return timeCntSplitList
	 */
	public Integer[] getTimeCntSplitList() {
		return timeCntSplitList;
	}

	/**
	 * 相関係数リストを返却
	 * @return corrList
	 */
	public String[] getCorrList() {
		return corrList;
	}

	/**
	 * 件数リストを返却
	 * @return skewnessCntList
	 */
	public Integer[] getSkewnessCntList() {
		return skewnessCntList;
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

	/**
	 * 開始情報を返却
	 * @return startInsertIdx
	 */
	public int getStartScoreInsertIdx() {
		return startScoreInsertIdx;
	}

	/**
	 * 終了情報を返却
	 * @return endScoreInsertIdx
	 */
	public int getEndScoreInsertIdx() {
		return endScoreInsertIdx;
	}

	/**
	 * 開始情報を返却
	 * @return startCalcInsertIdx
	 */
	public int getStartCalcInsertIdx() {
		return startCalcInsertIdx;
	}

	/**
	 * 終了情報を返却
	 * @return endCalcInsertIdx
	 */
	public int getEndCalcInsertIdx() {
		return endCalcInsertIdx;
	}

}
