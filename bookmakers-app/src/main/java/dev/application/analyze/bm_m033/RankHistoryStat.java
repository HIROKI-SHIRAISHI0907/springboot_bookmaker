package dev.application.analyze.bm_m033;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.analyze.interf.AnalyzeEntityIF;
import dev.application.domain.repository.bm.BookDataRepository;
import dev.application.domain.repository.bm.RankHistoryStatRepository;
import dev.common.constant.BookMakersCommonConst;
import dev.common.entity.BookDataEntity;
import dev.common.exception.wrap.RootCauseWrapper;
import dev.common.logger.ManageLoggerComponent;
import dev.common.util.ExecuteMainUtil;

/**
 * BM_M033統計分析ロジック
 * @author shiraishitoshio
 *
 */
@Component
public class RankHistoryStat implements AnalyzeEntityIF {

    /** プロジェクト名 */
    private static final String PROJECT_NAME = RankHistoryStat.class.getProtectionDomain()
            .getCodeSource().getLocation().getPath();

    /** クラス名 */
    private static final String CLASS_NAME = RankHistoryStat.class.getSimpleName();

    /** 実行モード */
    private static final String EXEC_MODE = "BM_M033_RANK_HISTORY";

    /** BookDataRepositoryレポジトリクラス */
    @Autowired
    private BookDataRepository bookDataRepository;

    /** RankHistoryStatRepositoryレポジトリクラス */
    @Autowired
    private RankHistoryStatRepository rankHistoryStatRepository;

    /** ログ管理ラッパー*/
    @Autowired
    private RootCauseWrapper rootCauseWrapper;

    /** ログ管理クラス */
    @Autowired
    private ManageLoggerComponent manageLoggerComponent;

    /**
     * {@inheritDoc}
     * @throws Exception
     */
    @Override
    public void calcStat(Map<String, Map<String, List<BookDataEntity>>> entities) {
        final String METHOD_NAME = "calcStat";
        // ログ出力
        this.manageLoggerComponent.init(EXEC_MODE, null);
        this.manageLoggerComponent.debugStartInfoLog(
                PROJECT_NAME, CLASS_NAME, METHOD_NAME);

        // 全リーグ・国を走査
        for (Map.Entry<String, Map<String, List<BookDataEntity>>> entry : entities.entrySet()) {
            Map<String, List<BookDataEntity>> entrySub = entry.getValue();

            for (List<BookDataEntity> entityList : entrySub.values()) {
                // null や空リストはスキップ
                if (entityList == null || entityList.isEmpty()) {
                    continue;
                }

                int match = -1;

                // 1チームぶんの試合リスト
                for (BookDataEntity entity : entityList) {

                    // gameTeamCategory から match 抽出（null/blank ケア）
                    String category = entity.getGameTeamCategory();
                    if (category != null && !category.isBlank()) {
                        match = ExecuteMainUtil.extractRoundNumbers(category);
                    }

                    // 終了済以外はスキップ
                    if (!BookMakersCommonConst.FIN.equals(entity.getTime())) {
                        continue;
                    }

                    List<String> countryLeague = ExecuteMainUtil
                            .getCountryLeagueByRegex(category);
                    if (countryLeague.isEmpty()) {
                    	String messageCd = "国リーグ情報警告";
                    	this.manageLoggerComponent.debugWarnLog(
                                PROJECT_NAME, "ExecuteMainUtil", "getCountryLeagueByRegex", messageCd, null,
                                "分割失敗(" + category + ")");
                    	continue;
                    }

                    // 試合ごとに homeRank / awayRank をリセット
                    int homeRank = -1;
                    int awayRank = -1;

                    // 基本はアプリ稼働の同タイミングでhome, awayが同じチームがある、
                    // home,awayに同じチームが登録される場合でもmatchが異なる
                    // 順位が設定済み（ホーム）
                    String homeRankStr = entity.getHomeRank();
                    if (homeRankStr != null && !homeRankStr.isBlank()) {
                        RankHistoryEntity rankHistoryEntity = new RankHistoryEntity();
                        homeRank = Integer.parseInt(homeRankStr.replace(".0", "").replace(".", ""));
                        rankHistoryEntity.setCountry(countryLeague.get(0));
                        rankHistoryEntity.setLeague(countryLeague.get(1));
                        rankHistoryEntity.setTeam(entity.getHomeTeamName());
                        rankHistoryEntity.setMatch(match);
                        rankHistoryEntity.setRank(homeRank);
                        String key = countryLeague.get(0) + ": " + countryLeague.get(1) + ": " + entity.getHomeTeamName();

                        if (this.rankHistoryStatRepository.select(rankHistoryEntity) > 0) {
                            int result = this.rankHistoryStatRepository.update(rankHistoryEntity);
                            if (result != 1) {
                                String messageCd = "更新エラー";
                                this.rootCauseWrapper.throwUnexpectedRowCount(
                                        PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                                        messageCd,
                                        1, result,
                                        key);
                            }
                            String messageCd = "ホーム更新件数";
                            this.manageLoggerComponent.debugInfoLog(
                                    PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null,
                                    "BM_M033 更新件数: 1件");
                        } else {
                            int result = this.rankHistoryStatRepository.insert(rankHistoryEntity);
                            if (result != 1) {
                                String messageCd = "新規登録エラー";
                                this.rootCauseWrapper.throwUnexpectedRowCount(
                                        PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                                        messageCd,
                                        1, result,
                                        key);
                            }
                            String messageCd = "ホーム登録件数";
                            this.manageLoggerComponent.debugInfoLog(
                                    PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null,
                                    "BM_M033 登録件数: 1件(" + key + ")");
                        }
                    }

                    // 順位が設定済み（アウェイ）
                    String awayRankStr = entity.getAwayRank();
                    if (awayRankStr != null && !awayRankStr.isBlank()) {
                        RankHistoryEntity rankHistoryEntity2 = new RankHistoryEntity();
                        awayRank = Integer.parseInt(awayRankStr.replace(".0", "").replace(".", ""));
                        rankHistoryEntity2.setCountry(countryLeague.get(0));
                        rankHistoryEntity2.setLeague(countryLeague.get(1));
                        rankHistoryEntity2.setTeam(entity.getAwayTeamName());
                        rankHistoryEntity2.setMatch(match);
                        rankHistoryEntity2.setRank(awayRank);
                        String key = countryLeague.get(0) + ": " + countryLeague.get(1) + ": " + entity.getAwayTeamName();

                        if (this.rankHistoryStatRepository.select(rankHistoryEntity2) > 0) {
                            int result = this.rankHistoryStatRepository.update(rankHistoryEntity2);
                            if (result != 1) {
                                String messageCd = "更新エラー";
                                this.rootCauseWrapper.throwUnexpectedRowCount(
                                        PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                                        messageCd,
                                        1, result,
                                        key);
                            }
                            String messageCd = "アウェー更新件数";
                            this.manageLoggerComponent.debugInfoLog(
                                    PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null,
                                    "BM_M033 更新件数: 1件(" + key + ")");
                        } else {
                            int result = this.rankHistoryStatRepository.insert(rankHistoryEntity2);
                            if (result != 1) {
                                String messageCd = "新規登録エラー";
                                this.rootCauseWrapper.throwUnexpectedRowCount(
                                        PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                                        messageCd,
                                        1, result,
                                        key);
                            }
                            String messageCd = "アウェー登録件数";
                            this.manageLoggerComponent.debugInfoLog(
                                    PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null,
                                    "BM_M033 登録件数: 1件(" + key + ")");
                        }
                    }

                    // 片方だけ順位が入っている場合 → 自前ロジックで順位を補完
                    if (homeRank == -1 || awayRank == -1) {
                        try {
                            List<TeamPoints> teamPoints = getOriginRank(
                                    countryLeague.get(0),
                                    countryLeague.get(1),
                                    String.valueOf(match));
                            List<RankedTeamPoints> ranks = rankTeams(teamPoints);

                            for (RankedTeamPoints pointDTO : ranks) {
                                RankHistoryEntity rankHistoryEntity = new RankHistoryEntity();
                                rankHistoryEntity.setCountry(countryLeague.get(0));
                                rankHistoryEntity.setLeague(countryLeague.get(1));
                                rankHistoryEntity.setTeam(pointDTO.getTeam());
                                rankHistoryEntity.setMatch(match);
                                rankHistoryEntity.setRank(pointDTO.getRank());
                                String key = countryLeague.get(0) + ": " + countryLeague.get(1) + ": " + pointDTO.getTeam();

                                if (this.rankHistoryStatRepository.select(rankHistoryEntity) > 0) {
                                    int result = this.rankHistoryStatRepository.update(rankHistoryEntity);
                                    if (result != 1) {
                                        String messageCd = "順位後付け更新エラー";
                                        this.rootCauseWrapper.throwUnexpectedRowCount(
                                                PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                                                messageCd,
                                                1, result,
                                                String.join(",", countryLeague.get(0), countryLeague.get(1),
                                                		pointDTO.getTeam()), String.valueOf(match),
                                                String.valueOf(pointDTO.getRank()));
                                    }
                                    String messageCd = "順位後付け更新件数";
                                    this.manageLoggerComponent.debugInfoLog(
                                            PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null,
                                            "BM_M033 更新件数: 1件(" + key + ")");
                                } else {
                                    int result = this.rankHistoryStatRepository.insert(rankHistoryEntity);
                                    if (result != 1) {
                                        String messageCd = "順位後付け新規登録エラー";
                                        this.rootCauseWrapper.throwUnexpectedRowCount(
                                                PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                                                messageCd,
                                                1, result,
                                                String.join(",", countryLeague.get(0), countryLeague.get(1),
                                                		pointDTO.getTeam()), String.valueOf(match),
                                                String.valueOf(pointDTO.getRank()));
                                    }
                                    String messageCd = "順位後付け登録件数";
                                    this.manageLoggerComponent.debugInfoLog(
                                            PROJECT_NAME, CLASS_NAME, METHOD_NAME, messageCd, null,
                                            "BM_M033 登録件数: 1件(" + key + ")");
                                }
                            }
                        } catch (Exception e) {
                            String messageCd = "DBエラー";
                            this.rootCauseWrapper.throwUnexpectedRowCount(
                                    PROJECT_NAME, CLASS_NAME, METHOD_NAME,
                                    messageCd,
                                    -99, -99,
                                    null);
                        }
                    }
                }
            }
        }

        // endLog
        this.manageLoggerComponent.debugEndInfoLog(
                PROJECT_NAME, CLASS_NAME, METHOD_NAME);
        this.manageLoggerComponent.clear();
    }

    /**
     * 国,リーグから勝利,敗北,引き分け,得失点差を導出し,<br>
     * 現在実施済みの試合から最終的な順位を出す
     * @param country 国
     * @param league リーグ
     * @param match 節
     * @return
     */
    private List<TeamPoints> getOriginRank(String country, String league, String match) throws Exception {
        return this.bookDataRepository.selectTeamPoints(country, league, match);
    }

    /**
     * 順位付け
     * @param teamPoints
     * @return
     */
    private List<RankedTeamPoints> rankTeams(List<TeamPoints> teamPoints) {

        // 1. ソート（勝ち点 → 得失点差 → 得点）
        List<TeamPoints> sorted = teamPoints.stream()
                .sorted((a, b) -> {
                    int pointsA = a.getPoints() != null ? a.getPoints() : 0;
                    int pointsB = b.getPoints() != null ? b.getPoints() : 0;
                    int cmpPoints = Integer.compare(pointsB, pointsA);
                    if (cmpPoints != 0)
                        return cmpPoints;

                    int gfA = a.getGf() != null ? a.getGf() : 0;
                    int gaA = a.getGa() != null ? a.getGa() : 0;
                    int gfB = b.getGf() != null ? b.getGf() : 0;
                    int gaB = b.getGa() != null ? b.getGa() : 0;

                    int goalDiffA = gfA - gaA;
                    int goalDiffB = gfB - gaB;
                    int cmpGoalDiff = Integer.compare(goalDiffB, goalDiffA);
                    if (cmpGoalDiff != 0)
                        return cmpGoalDiff;

                    return Integer.compare(gfB, gfA);
                })
                .collect(Collectors.toList());

        // 2. 順位付け
        List<RankedTeamPoints> rankedList = new ArrayList<>();

        int rank = 1;
        int index = 0;

        Integer prevPoints = null;
        Integer prevGoalDiff = null;
        Integer prevGf = null;

        for (TeamPoints tp : sorted) {
            int points = tp.getPoints() != null ? tp.getPoints() : 0;
            int gf = tp.getGf() != null ? tp.getGf() : 0;
            int ga = tp.getGa() != null ? tp.getGa() : 0;
            int goalDiff = gf - ga;

            // 前のチームと比較して順位決定
            if (prevPoints != null &&
                    points == prevPoints &&
                    goalDiff == prevGoalDiff &&
                    gf == prevGf) {
                // 同順位 → rank そのまま
            } else {
                // 新しい順位
                rank = index + 1;
            }

            RankedTeamPoints rtp = new RankedTeamPoints();
            rtp.setRank(rank);
            rtp.setTeam(tp.getTeam());
            rtp.setPoints(points);
            rtp.setGf(gf);
            rtp.setGa(ga);
            rtp.setPlayed(tp.getPlayed());

            rankedList.add(rtp);

            prevPoints = points;
            prevGoalDiff = goalDiff;
            prevGf = gf;

            index++;
        }

        return rankedList;
    }

}
