package dev.application.analyze.bm_m005;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.application.domain.repository.bm.NoGoalMatchStatsRepository;
import dev.common.constant.MessageCdConst;
import dev.common.entity.BookDataEntity;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * BM_M005統計分析ロジック
 * @author shiraishitoshio
 *
 */
@Component
public class NoGoalMatchStat implements AnalyzeEntityIF {

	/** プロジェクト名 */
	private static final String PROJECT_NAME = NoGoalMatchStat.class.getProtectionDomain()
			.getCodeSource().getLocation().getPath();

	/** クラス名 */
	private static final String CLASS_NAME = NoGoalMatchStat.class.getSimpleName();

	/** 実行モード */
	private static final String EXEC_MODE = "BM_M005_NO_GOAL_MATCH_DATA";

	/** BM_STAT_NUMBER */
	private static final String BM_NUMBER = "BM_M005";

	/** BookDataToNoGoalMatchMapperマッパークラス */
	@Autowired
	private BookDataToNoGoalMatchMapper bookDataToNoGoalMatchMapper;

	/** NoGoalMatchStatisticsRepositoryレポジトリクラス */
	@Autowired
	private NoGoalMatchStatsRepository noGoalMatchStatisticsRepository;

	/** ログ管理ラッパー*/
	@Autowired
	private RootCauseWrapper rootCauseWrapper;

	/** ログ管理クラス */
	@Autowired
	private ManageLoggerComponent manageLoggerComponent;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {
		final String METHOD_NAME = "calcStat";
		// ログ出力
		this.manageLoggerComponent.init(EXEC_MODE, null);
		this.manageLoggerComponent.debugStartInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);

		// 無得点データのみ抜き出す
		Map<String, Map<String, List<BookDataEntity>>> noEntities = excerptNoScores(entities);

		// 登録情報あるか
		if (noEntities.isEmpty()) {
			List<BookDataEntity> allList =
				    entities.values().stream()
				        .flatMap(m -> m.values().stream())
				        .flatMap(List::stream)
				        .collect(Collectors.toList());
			String fillChar = (!allList.isEmpty()) ? setLoggerFillChar(allList.get(0)) : "";
			String messageCd = MessageCdConst.MCD00008I_NO_SCORE_SKIP;
			this.manageLoggerComponent.debugInfoLog(
					PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, fillChar);
		}

		// Mapを回す
		List<BookDataEntity> entityList = Collections.synchronizedList(new ArrayList<>());
		noEntities.values().parallelStream().forEach(innerMap -> {
			for (List<BookDataEntity> list : innerMap.values()) {
				entityList.add(ExecuteMainUtil.getMinSeqEntities(list));
				entityList.add(ExecuteMainUtil.getHalfEntities(list));
				entityList.add(ExecuteMainUtil.getMaxSeqEntities(list));
			}
		});

		// MapStructで変換
		List<NoGoalMatchStatisticsEntity> insertEntities = new ArrayList<NoGoalMatchStatisticsEntity>();
		for (BookDataEntity entity : entityList) {
			if (entity == null) continue;
			insertEntities.add(this.bookDataToNoGoalMatchMapper.mapStruct(entity));
		}

		// Map登録
		for (NoGoalMatchStatisticsEntity entity : insertEntities) {
			save(entity);
		}

		// endLog
		this.manageLoggerComponent.debugEndInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME);
		this.manageLoggerComponent.clear();
	}

	/**
	 * 無得点データのみ抜き出す
	 * @param entities エンティティ
	 * @return エンティティ
	 */
	private Map<String, Map<String, List<BookDataEntity>>> excerptNoScores(
			Map<String, Map<String, List<BookDataEntity>>> entities) {
		Map<String, Map<String, List<BookDataEntity>>> noScoreEntities = entities.entrySet().stream()
				.map(entry -> {
					String outerKey = entry.getKey();
					Map<String, List<BookDataEntity>> innerMap = entry.getValue();

					Map<String, List<BookDataEntity>> filteredInnerMap = innerMap.entrySet().stream()
							.map(innerEntry -> {
								String innerKey = innerEntry.getKey();
								List<BookDataEntity> filteredList = innerEntry.getValue().stream()
										.filter(e -> "0".equals(e.getHomeScore()) && "0".equals(e.getAwayScore()))
										.collect(Collectors.toList());
								return new java.util.AbstractMap.SimpleEntry<>(innerKey, filteredList);
							})
							.filter(e -> !e.getValue().isEmpty())
							.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

					return new SimpleEntry<>(outerKey, filteredInnerMap);
				})
				.filter(e -> !e.getValue().isEmpty())
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		return noScoreEntities;
	}

	/**
	 * 登録ロジック
	 * @param entity
	 */
	private synchronized void save(NoGoalMatchStatisticsEntity entity) {
		final String METHOD_NAME = "save";

		int result = this.noGoalMatchStatisticsRepository.insert(entity);
		if (result != 1) {
			String messageCd = MessageCdConst.MCD00007E_INSERT_FAILED;
        	this.rootCauseWrapper.throwUnexpectedRowCount(
                    PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                    messageCd, 1, result, null);
		}

		String fillChar = setLoggerFillChar(entity);
		String messageCd = MessageCdConst.MCD00005I_INSERT_SUCCESS;
		this.manageLoggerComponent.debugInfoLog(
				PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, BM_NUMBER + " 登録件数: " + result + "件 (" + fillChar + ")");
	}

	/**
	 * 埋め字設定
	 * @param entity
	 * @return
	 */
	private String setLoggerFillChar(NoGoalMatchStatisticsEntity entity) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("国,リーグ: " + entity.getDataCategory() + ", ");
		stringBuilder.append("ホームチーム: " + entity.getHomeTeamName() + ", ");
		stringBuilder.append("アウェーチーム: " + entity.getAwayTeamName());
		return stringBuilder.toString();
	}

	/**
	 * 埋め字設定
	 * @param entity
	 * @return
	 */
	private String setLoggerFillChar(BookDataEntity entity) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("国,リーグ: " + entity.getGameTeamCategory() + ", ");
		stringBuilder.append("ホームチーム: " + entity.getHomeTeamName() + ", ");
		stringBuilder.append("アウェーチーム: " + entity.getAwayTeamName());
		return stringBuilder.toString();
	}

}
