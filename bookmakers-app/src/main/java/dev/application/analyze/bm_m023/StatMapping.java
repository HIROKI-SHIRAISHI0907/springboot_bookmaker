package dev.application.analyze.bm_m023;

import java.util.ArrayList;
import java.util.List;

import dev.application.analyze.common.entity.ThresHoldEntity;


public class StatMapping {
    public static List<FieldMapping> createFieldMappings() {
        List<FieldMapping> mappings = new ArrayList<>();

        mappings.add(new FieldMapping(0, ThresHoldEntity::getHomeExp));
        mappings.add(new FieldMapping(1, ThresHoldEntity::getAwayExp));
        mappings.add(new FieldMapping(2, ThresHoldEntity::getHomeDonation));
        mappings.add(new FieldMapping(3, ThresHoldEntity::getAwayDonation));
        mappings.add(new FieldMapping(4, ThresHoldEntity::getHomeShootAll));
        mappings.add(new FieldMapping(5, ThresHoldEntity::getAwayShootAll));
        mappings.add(new FieldMapping(6, ThresHoldEntity::getHomeShootIn));
        mappings.add(new FieldMapping(7, ThresHoldEntity::getAwayShootIn));
        mappings.add(new FieldMapping(8, ThresHoldEntity::getHomeShootOut));
        mappings.add(new FieldMapping(9, ThresHoldEntity::getAwayShootOut));
        mappings.add(new FieldMapping(10, ThresHoldEntity::getHomeBlockShoot));
        mappings.add(new FieldMapping(11, ThresHoldEntity::getAwayBlockShoot));
        mappings.add(new FieldMapping(12, ThresHoldEntity::getHomeBigChance));
        mappings.add(new FieldMapping(13, ThresHoldEntity::getAwayBigChance));
        mappings.add(new FieldMapping(14, ThresHoldEntity::getHomeCorner));
        mappings.add(new FieldMapping(15, ThresHoldEntity::getAwayCorner));
        mappings.add(new FieldMapping(16, ThresHoldEntity::getHomeBoxShootIn));
        mappings.add(new FieldMapping(17, ThresHoldEntity::getAwayBoxShootIn));
        mappings.add(new FieldMapping(18, ThresHoldEntity::getHomeBoxShootOut));
        mappings.add(new FieldMapping(19, ThresHoldEntity::getAwayBoxShootOut));
        mappings.add(new FieldMapping(20, ThresHoldEntity::getHomeGoalPost));
        mappings.add(new FieldMapping(21, ThresHoldEntity::getAwayGoalPost));
        mappings.add(new FieldMapping(22, ThresHoldEntity::getHomeGoalHead));
        mappings.add(new FieldMapping(23, ThresHoldEntity::getAwayGoalHead));
        mappings.add(new FieldMapping(24, ThresHoldEntity::getHomeKeeperSave));
        mappings.add(new FieldMapping(25, ThresHoldEntity::getAwayKeeperSave));
        mappings.add(new FieldMapping(26, ThresHoldEntity::getHomeFreeKick));
        mappings.add(new FieldMapping(27, ThresHoldEntity::getAwayFreeKick));
        mappings.add(new FieldMapping(28, ThresHoldEntity::getHomeOffside));
        mappings.add(new FieldMapping(29, ThresHoldEntity::getAwayOffside));
        mappings.add(new FieldMapping(30, ThresHoldEntity::getHomeFoul));
        mappings.add(new FieldMapping(31, ThresHoldEntity::getAwayFoul));
        mappings.add(new FieldMapping(32, ThresHoldEntity::getHomeYellowCard));
        mappings.add(new FieldMapping(33, ThresHoldEntity::getAwayYellowCard));
        mappings.add(new FieldMapping(34, ThresHoldEntity::getHomeRedCard));
        mappings.add(new FieldMapping(35, ThresHoldEntity::getAwayRedCard));
        mappings.add(new FieldMapping(36, ThresHoldEntity::getHomeSlowIn));
        mappings.add(new FieldMapping(37, ThresHoldEntity::getAwaySlowIn));
        mappings.add(new FieldMapping(38, ThresHoldEntity::getHomeBoxTouch));
        mappings.add(new FieldMapping(39, ThresHoldEntity::getAwayBoxTouch));
        mappings.add(new FieldMapping(40, ThresHoldEntity::getHomePassCount)); //3分割
        mappings.add(new FieldMapping(41, ThresHoldEntity::getAwayPassCount)); //3分割
        mappings.add(new FieldMapping(42, ThresHoldEntity::getHomeFinalThirdPassCount)); //3分割
        mappings.add(new FieldMapping(43, ThresHoldEntity::getAwayFinalThirdPassCount)); //3分割
        mappings.add(new FieldMapping(44, ThresHoldEntity::getHomeCrossCount)); //3分割
        mappings.add(new FieldMapping(45, ThresHoldEntity::getAwayCrossCount)); //3分割
        mappings.add(new FieldMapping(46, ThresHoldEntity::getHomeTackleCount)); //3分割
        mappings.add(new FieldMapping(47, ThresHoldEntity::getAwayTackleCount)); //3分割
        mappings.add(new FieldMapping(48, ThresHoldEntity::getHomeClearCount));
        mappings.add(new FieldMapping(49, ThresHoldEntity::getAwayClearCount));
        mappings.add(new FieldMapping(50, ThresHoldEntity::getHomeInterceptCount));
        mappings.add(new FieldMapping(51, ThresHoldEntity::getAwayInterceptCount));

        return mappings;
    }
}
