package dev.common.readfile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.common.constant.BookMakersCommonConst;
import dev.common.entity.CountryLeagueMasterEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.readfile.dto.ReadFileOutputDTO;

/**
 * チームマスタ(CSV)読み込みクラス
 * 形式: country, league, team, link
 */
@Component
public class ReadTeam implements ReadFileBodyIF {

    /** プロジェクト名 */
    private static final String PROJECT_NAME = ReadTeam.class.getProtectionDomain()
            .getCodeSource().getLocation().getPath();

    /** クラス名 */
    private static final String CLASS_NAME = ReadTeam.class.getName();



    /** 実行モード */
    private static final String EXEC_MODE = "READ_TEAM";

    /** ログ管理クラス */
    @Autowired
    private ManageLoggerComponent manageLoggerComponent;

    /**
     * CSVファイルの中身を取得する
     * @param fileFullPath ファイル名（フルパス）
     * @return readFileOutputDTO
     */
    @Override
    public ReadFileOutputDTO getFileBodyFromStream(InputStream is, String key) {
        final String METHOD_NAME = "getFileBodyFromStream";

        this.manageLoggerComponent.init(EXEC_MODE, key);
        this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

        ReadFileOutputDTO dto = new ReadFileOutputDTO();
        List<CountryLeagueMasterEntity> entityList = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            String line;
            int rowNo = 0;

            while ((line = br.readLine()) != null) {
                rowNo++;
                // 1行目ヘッダーはスキップ
                if (rowNo == 1) continue;
                if (line.trim().isEmpty()) continue;
                List<String> cols = parseCsvLine(line);

                if (cols.size() < 3) continue;
                String country = getCol(cols, 0);
                String league  = getCol(cols, 1);
                String team    = getCol(cols, 2);
                String link    = getCol(cols, 3);
                if (country.isBlank() && league.isBlank()) continue;
                if (team.isBlank()) continue;

                CountryLeagueMasterEntity e = new CountryLeagueMasterEntity();
                e.setCountry(country);
                e.setLeague(league);
                e.setTeam(team);
                e.setLink(link);
                entityList.add(e);
            }

            dto.setResultCd(BookMakersCommonConst.NORMAL_CD);
            dto.setCountryLeagueMasterList(entityList);
            return dto;

        } catch (Exception e) {
            dto.setExceptionProject(PROJECT_NAME);
            dto.setExceptionClass(CLASS_NAME);
            dto.setExceptionMethod(METHOD_NAME);
            dto.setResultCd(BookMakersCommonConst.ERR_CD_ERR_FILE_READS);
            dto.setErrMessage(BookMakersCommonConst.ERR_MESSAGE_ERR_FILE_READS);
            dto.setThrowAble(e);
            return dto;

        } finally {
            this.manageLoggerComponent.debugEndInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);
            this.manageLoggerComponent.clear();
        }
    }

    /** cols[i] を安全に取得してtrimして返す */
    private static String getCol(List<String> cols, int idx) {
        if (idx < 0 || idx >= cols.size()) return "";
        String s = cols.get(idx);
        return s == null ? "" : s.trim();
    }

    /**
     * CSV 1行パース（カンマ、ダブルクォート対応）
     * - "a,b" のようにカンマを含む列OK
     * - "" は " のエスケープとして扱う
     */
    private static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        boolean inQuotes = false;
        int i = 0;

        while (i < line.length()) {
            char c = line.charAt(i);

            if (inQuotes) {
                if (c == '"') {
                    // "" -> " として扱う
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        sb.append('"');
                        i += 2;
                        continue;
                    } else {
                        inQuotes = false;
                        i++;
                        continue;
                    }
                } else {
                    sb.append(c);
                    i++;
                    continue;
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                    i++;
                    continue;
                }
                if (c == ',') {
                    out.add(sb.toString());
                    sb.setLength(0);
                    i++;
                    continue;
                }
                sb.append(c);
                i++;
            }
        }

        out.add(sb.toString());
        return out;
    }
}
