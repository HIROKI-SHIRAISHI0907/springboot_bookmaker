package dev.common.readfile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.entity.BookDataEntity;
import dev.common.logger.ManageLoggerComponent;

@Component
public class ReadStat {

    private static final String PROJECT_NAME = ReadStat.class.getProtectionDomain()
            .getCodeSource().getLocation().getPath();

    private static final String CLASS_NAME = ReadStat.class.getName();

    private static final String EXEC_MODE = "READ_FILE";

    @Autowired
    private ManageLoggerComponent manageLoggerComponent;

    /** 既存CSVの「組み合わせ復元」専用：軽量インデックスを作る */
    public StatCsvIndexDTO readIndex(InputStream is, String key) {
        final String METHOD_NAME = "readIndex";

        manageLoggerComponent.init(EXEC_MODE, key);
        manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

        StatCsvIndexDTO dto = new StatCsvIndexDTO();
        dto.setKey(key);
        dto.setFileNo(extractFileNo(key));

        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            // ヘッダ
            String line = br.readLine();
            if (line == null) return dto;

            // 先頭データ行（ここだけ split して category/home/away を取る）
            line = br.readLine();
            if (line == null) return dto;

            String[] parts = line.split(",", -1);
            if (parts.length >= 9) {
                dto.setCategory(parts[2]);
                dto.setHome(parts[5]);
                dto.setAway(parts[8]);
            }
            addSeq(dto, (parts.length > 0 ? parts[0] : null));

            // 以降は seq（先頭カンマまで）だけ抜く
            while ((line = br.readLine()) != null) {
                int comma = line.indexOf(',');
                String seqStr = (comma >= 0) ? line.substring(0, comma) : line;
                addSeq(dto, seqStr);
            }

            return dto;

        } catch (Exception e) {
            manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, null, e,
                    "readIndex failed key=" + key);
            throw new RuntimeException(e);

        } finally {
            manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, "", "読み取りファイル名: " + key);
            manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
            manageLoggerComponent.clear();
        }
    }

    /** ★ CoreStat 用：CSVを BookDataEntity のListに復元して返す（S3 InputStream版） */
    public List<BookDataEntity> readEntities(InputStream is, String key) {
        final String METHOD_NAME = "readEntities";

        manageLoggerComponent.init(EXEC_MODE, key);
        manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

        List<BookDataEntity> list = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String text;
            int row = 0;

            while ((text = br.readLine()) != null) {
                row++;
                if (row == 1) continue; // ヘッダスキップ

                String[] parts = text.split(",", -1);

                // 参照している最大 index=99 なので 100列以上必須
                if (parts.length < 100) continue;

                BookDataEntity e = new BookDataEntity();
                e.setSeq(parts[0]);
                e.setConditionResultDataSeqId(parts[1]);
                e.setGameTeamCategory(parts[2]);
                e.setTime(parts[3]);
                e.setHomeRank(parts[4].replace(".0", ""));
                e.setHomeTeamName(parts[5]);
                e.setHomeScore(parts[6].replace(".0", ""));
                e.setAwayRank(parts[7].replace(".0", ""));
                e.setAwayTeamName(parts[8]);
                e.setAwayScore(parts[9].replace(".0", ""));
                e.setHomeExp(parts[10]);
                e.setAwayExp(parts[11]);
                e.setHomeInGoalExp(parts[12]);
                e.setAwayInGoalExp(parts[13]);
                e.setHomeBallPossesion(parts[14]);
                e.setAwayBallPossesion(parts[15]);
                e.setHomeShootAll(parts[16]);
                e.setAwayShootAll(parts[17]);
                e.setHomeShootIn(parts[18]);
                e.setAwayShootIn(parts[19]);
                e.setHomeShootOut(parts[20]);
                e.setAwayShootOut(parts[21]);
                e.setHomeShootBlocked(parts[22]);
                e.setAwayShootBlocked(parts[23]);
                e.setHomeBigChance(parts[24]);
                e.setAwayBigChance(parts[25]);
                e.setHomeCornerKick(parts[26]);
                e.setAwayCornerKick(parts[27]);
                e.setHomeBoxShootIn(parts[28]);
                e.setAwayBoxShootIn(parts[29]);
                e.setHomeBoxShootOut(parts[30]);
                e.setAwayBoxShootOut(parts[31]);
                e.setHomeGoalPost(parts[32]);
                e.setAwayGoalPost(parts[33]);
                e.setHomeGoalHead(parts[34]);
                e.setAwayGoalHead(parts[35]);
                e.setHomeKeeperSave(parts[36]);
                e.setAwayKeeperSave(parts[37]);
                e.setHomeFreeKick(parts[38]);
                e.setAwayFreeKick(parts[39]);
                e.setHomeOffSide(parts[40]);
                e.setAwayOffSide(parts[41]);
                e.setHomeFoul(parts[42]);
                e.setAwayFoul(parts[43]);
                e.setHomeYellowCard(parts[44]);
                e.setAwayYellowCard(parts[45]);
                e.setHomeRedCard(parts[46]);
                e.setAwayRedCard(parts[47]);
                e.setHomeSlowIn(parts[48]);
                e.setAwaySlowIn(parts[49]);
                e.setHomeBoxTouch(parts[50]);
                e.setAwayBoxTouch(parts[51]);
                e.setHomePassCount(parts[52]);
                e.setAwayPassCount(parts[53]);
                e.setHomePassCount(parts[54]);
                e.setAwayPassCount(parts[55]);
                e.setHomeFinalThirdPassCount(parts[56]);
                e.setAwayFinalThirdPassCount(parts[57]);
                e.setHomeCrossCount(parts[58]);
                e.setAwayCrossCount(parts[59]);
                e.setHomeTackleCount(parts[60]);
                e.setAwayTackleCount(parts[61]);
                e.setHomeClearCount(parts[62]);
                e.setAwayClearCount(parts[63]);
                e.setHomeDuelCount(parts[64]);
                e.setAwayDuelCount(parts[65]);
                e.setHomeInterceptCount(parts[66]);
                e.setAwayInterceptCount(parts[67]);
                e.setRecordTime(parts[68]);
                e.setWeather(parts[69]);
                e.setTemperature(parts[70]);
                e.setHumid(parts[71]);
                e.setJudgeMember(parts[72]);
                e.setHomeManager(parts[73]);
                e.setAwayManager(parts[74]);
                e.setHomeFormation(parts[75]);
                e.setAwayFormation(parts[76]);
                e.setStudium(parts[77]);
                e.setCapacity(parts[78]);
                e.setAudience(parts[79]);
                e.setHomeMaxGettingScorer(parts[80]);
                e.setAwayMaxGettingScorer(parts[81]);
                e.setHomeMaxGettingScorerGameSituation(parts[82]);
                e.setAwayMaxGettingScorerGameSituation(parts[83]);
                e.setHomeTeamHomeScore(parts[84]);
                e.setHomeTeamHomeLost(parts[85]);
                e.setAwayTeamHomeScore(parts[86]);
                e.setAwayTeamHomeLost(parts[87]);
                e.setHomeTeamAwayScore(parts[88]);
                e.setHomeTeamAwayLost(parts[89]);
                e.setAwayTeamAwayScore(parts[90]);
                e.setAwayTeamAwayLost(parts[91]);
                e.setNoticeFlg(parts[92]);
                e.setGoalTime(parts[93]);
                e.setGoalTeamMember(parts[94]);
                e.setJudge(parts[95]);
                e.setHomeTeamStyle(parts[96]);
                e.setAwayTeamStyle(parts[97]);
                e.setProbablity(parts[98]);
                e.setPredictionScoreTime(parts[99]);

                // ★ S3 key を保持
                e.setFilePath(key);

                list.add(e);
            }

            return list;

        } catch (Exception ex) {
            manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, null, ex,
                    "readEntities failed key=" + key);
            throw new RuntimeException(ex);

        } finally {
            manageLoggerComponent.debugInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, "", "読み取りファイル名: " + key);
            manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
            manageLoggerComponent.clear();
        }
    }

    /** ★ これを追加：ローカルファイルの condition data を読む（既存コンパイルエラー解消用） */
    public String getConditionDataFileBody(String fileFullPath) throws Exception {
        final String METHOD_NAME = "getConditionDataFileBody";

        manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

        File file = new File(fileFullPath);
        if (!file.exists()) {
            manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, null, null);
            return null;
        }

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            StringBuilder data = new StringBuilder();
            String text;
            while ((text = br.readLine()) != null) data.append(text);

            String repData = data.toString().strip().replace(" ", "");

            manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
            return repData;

        } catch (Exception e) {
            manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, null, e);
            throw e;
        }
    }

    private static void addSeq(StatCsvIndexDTO dto, String s) {
        if (s == null) return;
        String t = s.trim();
        if (t.isEmpty()) return;
        try { dto.getSeqs().add(Integer.parseInt(t)); }
        catch (NumberFormatException ignore) {}
    }

    /** "stats/6770.csv" / "6770.csv" どっちでもOK */
    private static Integer extractFileNo(String key) {
        if (key == null) return null;

        String name = key;
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);

        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);

        try { return Integer.parseInt(name); }
        catch (NumberFormatException e) { return null; }
    }
}
