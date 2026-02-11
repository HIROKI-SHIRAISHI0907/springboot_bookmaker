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
import dev.common.entity.AllLeagueMasterEntity;
import dev.common.logger.ManageLoggerComponent;
import dev.common.readfile.dto.ReadFileOutputDTO;

/**
 * 全容マスタ(CSV)読み込みクラス
 * 形式: country, league
 */
@Component
public class ReadAllLeague implements ReadFileBodyIF {

    /** プロジェクト名 */
    private static final String PROJECT_NAME = ReadAllLeague.class.getProtectionDomain()
            .getCodeSource().getLocation().getPath();

    /** クラス名 */
    private static final String CLASS_NAME = ReadAllLeague.class.getSimpleName();

    /** 実行モード */
    private static final String EXEC_MODE = "ALL_LEAGUE";

    /** 期待するCSV列数（country, league） */
    private static final int EXPECT_COLS = 2;

    @Autowired
    private ManageLoggerComponent manageLoggerComponent;

    @Override
    public ReadFileOutputDTO getFileBodyFromStream(InputStream is, String key) {
        final String METHOD_NAME = "getFileBodyFromStream";

        this.manageLoggerComponent.init(EXEC_MODE, key);
        this.manageLoggerComponent.debugStartInfoLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME);

        ReadFileOutputDTO dto = new ReadFileOutputDTO();
        List<AllLeagueMasterEntity> entityList = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            String line;
            boolean headerSkipped = false;

            while ((line = br.readLine()) != null) {

                // BOM除去（先頭行のみ想定だが、念のため毎回）
                if (!headerSkipped && !line.isEmpty() && line.charAt(0) == '\uFEFF') {
                    line = line.substring(1);
                }

                // 完全空行だけスキップ（空白は文字列として残したい方針）
                if (line.isEmpty()) continue;

                List<String> cols = parseCsvLine(line);
                while (cols.size() < EXPECT_COLS) cols.add("");

                // 1行目ヘッダーはスキップ
                if (!headerSkipped) {
                    headerSkipped = true;
                    continue;
                }

                // 全列 "" の行はスキップ
                if (isAllEmpty(cols)) continue;

                AllLeagueMasterEntity e = new AllLeagueMasterEntity();
                e.setCountry(safeGet(cols, 0));
                e.setLeague(safeGet(cols, 1));
                e.setLogicFlg("0");

                entityList.add(e);
            }

            dto.setResultCd(BookMakersCommonConst.NORMAL_CD);
            dto.setAllLeagueMasterList(entityList);
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

    /**
     * CSV 1行を簡易パース（ダブルクォート対応、"" は " に展開）
     * ※ trimしない：空欄/空白も「文字列」として保持
     */
    private static List<String> parseCsvLine(String line) {
        List<String> cols = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                // "" -> "
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }

            if (c == ',' && !inQuotes) {
                cols.add(sb.toString());
                sb.setLength(0);
                continue;
            }

            sb.append(c);
        }

        cols.add(sb.toString());
        return cols;
    }

    /** 全列が "" のときだけ true（空白スペースのみは文字列として扱う） */
    private static boolean isAllEmpty(List<String> cols) {
        for (String v : cols) {
            if (v != null && !v.isEmpty()) return false;
        }
        return true;
    }

    private static String safeGet(List<String> cols, int idx) {
        if (cols == null || idx < 0 || idx >= cols.size()) return "";
        String v = cols.get(idx);
        return v == null ? "" : v;
    }
}
