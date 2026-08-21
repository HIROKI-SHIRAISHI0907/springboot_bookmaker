package dev.batch.bm_b097;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.batch.repository.bm.BookDataRepository;
import dev.batch.repository.master.FutureMasterRepository;
import dev.common.constant.MessageCdConst;
import dev.common.entity.FutureEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.DateOffsetDecisionUtil;

/**
 * AutoAssignmentDataCategoryServiceロジック
 * @author shiraishitoshio
 *
 */
@Component
public class AutoAssignmentDataCategoryService {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = AutoAssignmentDataCategoryService.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();
	/** クラス名 */
	private static final String CLASS_NAME = AutoAssignmentDataCategoryService.class.getName();

	/** 実行モード */
	private static final String EXEC_MODE = "AUTO_ASSIGNMENT_DATA_CATEGORY";

	private static final String ROUND = "ラウンド";

	@Autowired
	private BookDataRepository bookDataRepository;

	@Autowired
	private FutureMasterRepository futureMasterRepository;

	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * 形式的なデータカテゴリを割り当てる
	 */
	public void execute() throws Exception {
		final String METHOD_NAME = "execute";
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// 1週間のうちに登録されたリアルタイムデータ、未来データのうち、ラウンドが入っていないデータを取得
		String[] previousDayRange = DateOffsetDecisionUtil.previousWeekDaysRangeAsUtcIsoStrings();
		String todayStart = previousDayRange[0];
		String todayEnd = previousDayRange[1];

		this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME
			    ,MessageCdConst.MCD00099I_LOG, "システム時間検索期間: " + todayStart + "~" + todayEnd +
			    "(日本時間換算: " + DateOffsetDecisionUtil.toIsoJstRangeString(previousDayRange) + ")");

		List<FutureEntity> weekFutureList = futureMasterRepository.findWeeksData(todayStart, todayEnd);

		this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME
			    ,MessageCdConst.MCD00099I_LOG, "weekFutureList: " + weekFutureList.size() + "件");

		// データカテゴリのセットを取得（同一カードの重複は除去）
		List<TeamPairWithDataCategory> pairs = weekFutureList.stream()
		        .map(f -> new TeamPairWithDataCategory(f.getGameTeamCategory(),
		        f.getHomeTeamName(), f.getAwayTeamName()))
		        .collect(Collectors.toList());

		this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME
			    ,MessageCdConst.MCD00099I_LOG, "pairs: " + pairs + "");

		Set<String> processedPairs = new HashSet<>();

		// update
		for (TeamPairWithDataCategory pairWithDataCategory : pairs) {
		    String home = pairWithDataCategory.getHomeTeamName();
		    String away = pairWithDataCategory.getAwayTeamName();
		    String dataCategory = pairWithDataCategory.getDataCategory();

		    this.manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME
				    ,MessageCdConst.MCD00099I_LOG, "home: " + home + ", away: " + away + ", dataCategory: "
				    + dataCategory);

		    // 同一カードはこのバッチ内で1回だけ処理する
		    if (!processedPairs.add(home + "|" + away)) {
		        continue;
		    }

		    // 「ラウンド」が存在する場合は、何もしない
		    if (roundChk(dataCategory)) {
		        continue;
		    }

		    // ラウンドがない場合、同じチームから別のデータカテゴリを取得する
		    String newDataCategory = getDataCategoryWithNull(home);
		    if (newDataCategory == null) {
		        newDataCategory = getDataCategoryWithNull(away);
		    }

		    // どこにも代替カテゴリが見つからない場合は更新しない
		    // （nullで更新すると、既にラウンド無しの正しい値が入っている行までNULLで上書きしてしまうため）
		    if (newDataCategory == null) {
		        continue;
		    }

		    // XX: YYY リーグ - ラウンド Z 形式であれば、リーグ名部分だけを取り出す
		    newDataCategory = extractLeagueOnly(newDataCategory);
		    if (newDataCategory.isEmpty()) {
		        continue;
		    }

		    // 更新
		    this.bookDataRepository.updateByDataCategoryWithNotRound(newDataCategory, home, away);
		    this.futureMasterRepository.updateByGameTeamCategoryWithNotRound(newDataCategory, home, away);
		}

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}

	/**
	 * 「ラウンド」が存在するか
	 * @param dataCategory
	 * @return
	 */
	private boolean roundChk(String dataCategory) {
		return (dataCategory == null || dataCategory.isEmpty()
				|| !dataCategory.contains(ROUND)) ? false : true;
	}

	/**
	 * 引数から渡ってきたチームと別の対戦データがある場合、dataCategoryを取得する
	 * ただしdataCategoryがnullの場合のみ
	 * @param dataCategory
	 * @return
	 */
	private String getDataCategoryWithNull(String team) {
		String futureGameTeamCategory = futureMasterRepository.findGameTeamCategoryByTeams(team);
		if (futureGameTeamCategory != null) return futureGameTeamCategory;

		String dataDataCategory = bookDataRepository.findDataCategoryByTeams(team);
		if (dataDataCategory != null) return dataDataCategory;

		return null;
	}

	/**
	 * 「ラウンド」があれば除去
	 * @param dataCategory
	 * @return
	 */
	private String extractLeagueOnly(String dataCategory) {
	    if (dataCategory == null) {
	        return null;
	    }
	    return dataCategory.replaceAll("\\s*-\\s*ラウンド.*$", "").trim();
	}

}
