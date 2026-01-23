package dev.common.readfile;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
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
    private static final String CLASS_NAME = ReadTeam.class.getSimpleName();

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
    public ReadFileOutputDTO getFileBody(String fileFullPath) {
        final String METHOD_NAME = "getFileBody";

        this.manageLoggerComponent.init(EXEC_MODE, fileFullPath);
        this.manageLoggerComponent.debugStartInfoLog(
                PROJECT_NAME, CLASS_NAME, METHOD_NAME);

        ReadFileOutputDTO readFileOutputDTO = new ReadFileOutputDTO();
        List<CountryLeagueMasterEntity> entityList = new ArrayList<>();

        // 文字コードは運用に合わせて変更可（UTF-8/Shift_JIS など）
        Charset charset = Charset.forName("UTF-8");

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(fileFullPath), charset))) {

            String line;
            int rowNo = 0;

            while ((line = br.readLine()) != null) {
                rowNo++;

                // 1行目ヘッダーはスキップ
                if (rowNo == 1) {
                    continue;
                }

                // 空行スキップ
                if (line.trim().isEmpty()) {
                    continue;
                }

                List<String> cols = parseCsvLine(line);

                // 必要列が足りない場合はスキップ（または警告ログ）
                if (cols.size() < 3) {
                    // team列が無いので読みようがない
                    continue;
                }

                String country = getCol(cols, 0);
                String league  = getCol(cols, 1);
                String team    = getCol(cols, 2);
                String link    = getCol(cols, 3); // 無い場合は空文字

                // 国・リーグが両方空ならスキップ
                if (country.isBlank() && league.isBlank()) {
                    continue;
                }

                // チームが空ならスキップ
                if (team.isBlank()) {
                    continue;
                }

                CountryLeagueMasterEntity e = new CountryLeagueMasterEntity();
                e.setCountry(country);
                e.setLeague(league);
                e.setTeam(team);
                e.setLink(link);

                entityList.add(e);
            }

            readFileOutputDTO.setResultCd(BookMakersCommonConst.NORMAL_CD);
            readFileOutputDTO.setCountryLeagueMasterList(entityList);

        } catch (Exception e) {
            readFileOutputDTO.setExceptionProject(PROJECT_NAME);
            readFileOutputDTO.setExceptionClass(CLASS_NAME);
            readFileOutputDTO.setExceptionMethod(METHOD_NAME);
            readFileOutputDTO.setResultCd(BookMakersCommonConst.ERR_CD_ERR_FILE_READS);
            readFileOutputDTO.setErrMessage(BookMakersCommonConst.ERR_MESSAGE_ERR_FILE_READS);
            readFileOutputDTO.setThrowAble(e);
            return readFileOutputDTO;
        }

        this.manageLoggerComponent.debugEndInfoLog(
                PROJECT_NAME, CLASS_NAME, METHOD_NAME);
        this.manageLoggerComponent.clear();

        return readFileOutputDTO;
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
