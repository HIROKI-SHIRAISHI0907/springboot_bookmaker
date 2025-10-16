package dev.application.main.service;

import java.lang.reflect.Field;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.application.domain.repository.BookDataRepository;
import dev.common.entity.DataEntity;
import dev.common.filemng.FileMngWrapper;

/**
 * dataテーブルCSV出力
 * @author shiraishitoshio
 *
 */
@Component
public class OutputData {

    /** ファイル名 */
    private static final String FILE = "/Users/shiraishitoshio/dumps/soccer_bm_data.csv";

    /** BookDataRepository */
    @Autowired
    private BookDataRepository bookDataRepository;

    @Autowired
    private FileMngWrapper mngWrapper;

    /**
     * 実行
     * @throws IllegalAccessException
     * @throws IllegalArgumentException
     */
    public void execute() throws IllegalArgumentException, IllegalAccessException {

        List<DataEntity> result = this.bookDataRepository.getData();
        StringBuilder header = new StringBuilder();
        boolean headFlg = true;
        int data = 1;
        for (DataEntity entity : result) {
            StringBuilder body = new StringBuilder();
            Field[] fields = DataEntity.class.getDeclaredFields();

            for (Field field : fields) {
                field.setAccessible(true);

                // フィールド名をスネークケースに変換
                String fieldName = toSnakeCase(field.getName());

                if ("serial_version_uid".equals(fieldName) || "file".equals(fieldName)
                		|| "file_count".equals(fieldName)) {
                	continue;
                }

                if (headFlg) {
                    if (header.length() > 0) {
                        header.append(",");
                    }
                    header.append(fieldName);
                }

                Object value = field.get(entity);
                if (body.length() > 0) {
                    body.append(",");
                }
                body.append(value == null ? "" : value.toString());
            }

            if (headFlg) {
            	header.append(",register_id");
                header.append(",register_time");
                header.append(",update_id");
                header.append(",update_time");
                headFlg = false;
            }

            body.append(",bm_user");
            body.append(",2025-10-04 09:02:54+09");
            body.append(",bm_user");
            body.append(",2025-10-04 09:02:54+09");

            // File
            mngWrapper.write(FILE, header.toString(), body.toString());
            System.out.println("filerecord: " + data + " / " + result.size());
            data++;
        }
    }

    /**
     * キャメルケースをスネークケースに変換するユーティリティメソッド
     * 例: "userName" -> "user_name"
     */
    private String toSnakeCase(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
