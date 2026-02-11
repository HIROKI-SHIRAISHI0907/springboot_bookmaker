package dev.web.api.bm_a001;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.common.entity.DataEntity;
import dev.web.repository.bm.BookDataRepository;
import lombok.RequiredArgsConstructor;

/**
 * DataService
 * @author shiraishitoshio
 */
@Service
@RequiredArgsConstructor
public class DataService {

    private final BookDataRepository repo;

    @Transactional(readOnly = true)
    public Optional<DataEntity> find(DataRequest dto) {
        if (dto == null || isBlank(dto.getSeq())) {
            return Optional.empty();
        }
        return repo.findBySeq(dto.getSeq());
    }

    /**
     * 手動登録（INSERT）
     * ※ repo側に insert がある前提。なければ repoに追加してください。
     */
    @Transactional
    public DataResponse create(DataRequest req) {
        DataResponse res = new DataResponse();

        if (req == null || isBlank(req.getSeq())) {
            res.setResponseCode("400");
            res.setMessage("必須項目が未入力です。（seq）");
            return res;
        }

        // 既存チェック
        if (repo.findBySeq(req.getSeq()).isPresent()) {
            res.setResponseCode("409");
            res.setMessage("すでに存在するseqです。");
            return res;
        }

        try {
            DataEntity e = toEntity(req);

            // 手動フラグを立てたいならここで強制
            if (isBlank(e.getAddManualFlg())) {
                e.setAddManualFlg("1");
            }

            int inserted = repo.insert(e); // ← repoにinsertが必要
            if (inserted == 1) {
                res.setResponseCode("200");
                res.setMessage("登録成功しました。");
            } else {
                res.setResponseCode("500");
                res.setMessage("登録に失敗しました。");
            }
            return res;

        } catch (Exception ex) {
            res.setResponseCode("500");
            res.setMessage("システムエラーが発生しました。");
            return res;
        }
    }

    /**
     * 更新（UPDATE）
     * ※ repo側に updateBySeq がある前提。なければ repoに追加してください。
     */
    @Transactional
    public DataResponse update(DataRequest req) {
        DataResponse res = new DataResponse();

        if (req == null || isBlank(req.getSeq())) {
            res.setResponseCode("400");
            res.setMessage("必須項目が未入力です。（seq）");
            return res;
        }

        if (repo.findBySeq(req.getSeq()).isEmpty()) {
            res.setResponseCode("404");
            res.setMessage("更新対象が存在しません。");
            return res;
        }

        try {
            DataEntity e = toEntity(req);

            int updated = repo.updateBySeq(e); // ← repoにupdateBySeqが必要
            if (updated == 1) {
                res.setResponseCode("200");
                res.setMessage("更新成功しました。");
            } else {
                res.setResponseCode("404");
                res.setMessage("更新対象が存在しません。");
            }
            return res;

        } catch (Exception ex) {
            res.setResponseCode("500");
            res.setMessage("システムエラーが発生しました。");
            return res;
        }
    }

    /** DataRequest -> DataEntity 変換 */
    private DataEntity toEntity(DataRequest r) {
        DataEntity e = new DataEntity();

        e.setFile(r.getFile());
        e.setSeq(r.getSeq());
        e.setConditionResultDataSeqId(r.getConditionResultDataSeqId());
        e.setDataCategory(r.getDataCategory());
        e.setTimes(r.getTimes());

        e.setHomeRank(r.getHomeRank());
        e.setHomeTeamName(r.getHomeTeamName());
        e.setHomeScore(r.getHomeScore());

        e.setAwayRank(r.getAwayRank());
        e.setAwayTeamName(r.getAwayTeamName());
        e.setAwayScore(r.getAwayScore());

        e.setHomeExp(r.getHomeExp());
        e.setAwayExp(r.getAwayExp());
        e.setHomeInGoalExp(r.getHomeInGoalExp());
        e.setAwayInGoalExp(r.getAwayInGoalExp());

        e.setHomeDonation(r.getHomeDonation());
        e.setAwayDonation(r.getAwayDonation());

        e.setHomeShootAll(r.getHomeShootAll());
        e.setAwayShootAll(r.getAwayShootAll());
        e.setHomeShootIn(r.getHomeShootIn());
        e.setAwayShootIn(r.getAwayShootIn());
        e.setHomeShootOut(r.getHomeShootOut());
        e.setAwayShootOut(r.getAwayShootOut());
        e.setHomeBlockShoot(r.getHomeBlockShoot());
        e.setAwayBlockShoot(r.getAwayBlockShoot());
        e.setHomeBigChance(r.getHomeBigChance());
        e.setAwayBigChance(r.getAwayBigChance());
        e.setHomeCorner(r.getHomeCorner());
        e.setAwayCorner(r.getAwayCorner());
        e.setHomeBoxShootIn(r.getHomeBoxShootIn());
        e.setAwayBoxShootIn(r.getAwayBoxShootIn());
        e.setHomeBoxShootOut(r.getHomeBoxShootOut());
        e.setAwayBoxShootOut(r.getAwayBoxShootOut());
        e.setHomeGoalPost(r.getHomeGoalPost());
        e.setAwayGoalPost(r.getAwayGoalPost());
        e.setHomeGoalHead(r.getHomeGoalHead());
        e.setAwayGoalHead(r.getAwayGoalHead());
        e.setHomeKeeperSave(r.getHomeKeeperSave());
        e.setAwayKeeperSave(r.getAwayKeeperSave());

        e.setHomeFreeKick(r.getHomeFreeKick());
        e.setAwayFreeKick(r.getAwayFreeKick());
        e.setHomeOffside(r.getHomeOffside());
        e.setAwayOffside(r.getAwayOffside());
        e.setHomeFoul(r.getHomeFoul());
        e.setAwayFoul(r.getAwayFoul());
        e.setHomeYellowCard(r.getHomeYellowCard());
        e.setAwayYellowCard(r.getAwayYellowCard());
        e.setHomeRedCard(r.getHomeRedCard());
        e.setAwayRedCard(r.getAwayRedCard());
        e.setHomeSlowIn(r.getHomeSlowIn());
        e.setAwaySlowIn(r.getAwaySlowIn());

        e.setHomeBoxTouch(r.getHomeBoxTouch());
        e.setAwayBoxTouch(r.getAwayBoxTouch());

        e.setHomePassCount(r.getHomePassCount());
        e.setAwayPassCount(r.getAwayPassCount());
        e.setHomeLongPassCount(r.getHomeLongPassCount());
        e.setAwayLongPassCount(r.getAwayLongPassCount());
        e.setHomeFinalThirdPassCount(r.getHomeFinalThirdPassCount());
        e.setAwayFinalThirdPassCount(r.getAwayFinalThirdPassCount());
        e.setHomeCrossCount(r.getHomeCrossCount());
        e.setAwayCrossCount(r.getAwayCrossCount());
        e.setHomeTackleCount(r.getHomeTackleCount());
        e.setAwayTackleCount(r.getAwayTackleCount());
        e.setHomeClearCount(r.getHomeClearCount());
        e.setAwayClearCount(r.getAwayClearCount());
        e.setHomeDuelCount(r.getHomeDuelCount());
        e.setAwayDuelCount(r.getAwayDuelCount());
        e.setHomeInterceptCount(r.getHomeInterceptCount());
        e.setAwayInterceptCount(r.getAwayInterceptCount());

        e.setRecordTime(r.getRecordTime());
        e.setWeather(r.getWeather());
        e.setTemparature(r.getTemparature());
        e.setHumid(r.getHumid());

        e.setJudgeMember(r.getJudgeMember());
        e.setHomeManager(r.getHomeManager());
        e.setAwayManager(r.getAwayManager());
        e.setHomeFormation(r.getHomeFormation());
        e.setAwayFormation(r.getAwayFormation());

        e.setStudium(r.getStudium());
        e.setCapacity(r.getCapacity());
        e.setAudience(r.getAudience());
        e.setLocation(r.getLocation());

        e.setHomeMaxGettingScorer(r.getHomeMaxGettingScorer());
        e.setAwayMaxGettingScorer(r.getAwayMaxGettingScorer());
        e.setHomeMaxGettingScorerGameSituation(r.getHomeMaxGettingScorerGameSituation());
        e.setAwayMaxGettingScorerGameSituation(r.getAwayMaxGettingScorerGameSituation());

        e.setHomeTeamHomeScore(r.getHomeTeamHomeScore());
        e.setHomeTeamHomeLost(r.getHomeTeamHomeLost());
        e.setAwayTeamHomeScore(r.getAwayTeamHomeScore());
        e.setAwayTeamHomeLost(r.getAwayTeamHomeLost());
        e.setHomeTeamAwayScore(r.getHomeTeamAwayScore());
        e.setHomeTeamAwayLost(r.getHomeTeamAwayLost());
        e.setAwayTeamAwayScore(r.getAwayTeamAwayScore());
        e.setAwayTeamAwayLost(r.getAwayTeamAwayLost());

        e.setNoticeFlg(r.getNoticeFlg());
        e.setGameLink(r.getGameLink());
        e.setGoalTime(r.getGoalTime());
        e.setGoalTeamMember(r.getGoalTeamMember());
        e.setJudge(r.getJudge());

        e.setHomeTeamStyle(r.getHomeTeamStyle());
        e.setAwayTeamStyle(r.getAwayTeamStyle());

        e.setProbablity(r.getProbablity());
        e.setPredictionScoreTime(r.getPredictionScoreTime());

        e.setGameId(r.getGameId());
        e.setMatchId(r.getMatchId());

        e.setTimeSortSeconds(r.getTimeSortSeconds());
        e.setAddManualFlg(r.getAddManualFlg());
        e.setFileCount(r.getFileCount());

        return e;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
