package dev.web.api.bm_w020;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import dev.common.config.PathConfig;
import dev.common.logger.ManageLoggerComponent;
import dev.common.s3.S3Operator;
import dev.web.repository.bm.BookCsvDataRepository;
import dev.web.util.CsvArtifactHelper;

@SpringBootTest
@ActiveProfiles("test")
class ExportCsvTest {

    @Autowired ExportCsv target;

    @Autowired JdbcTemplate jdbc;

    // DBは実接続にしたいので repository は mock しない
    @Autowired BookCsvDataRepository bookCsvDataRepository;

    @Mock S3Operator s3Operator;
    @Mock PathConfig config;
    @Mock ReaderCurrentCsvInfoBean bean;
    @Mock CsvArtifactHelper helper;
    @Mock ManageLoggerComponent manageLoggerComponent;

    // テスト要件どおり固定
    static final String BUCKET = "aws-s3-outputs-csv";
    static final String CSV_FOLDER = "/Users/shiraishitoshio/bookmaker/csv/";

    // S3 mockの呼び出し記録用
    List<String> uploadedTmpKeys;

    @BeforeEach
    void setup() throws Exception {
        uploadedTmpKeys = new CopyOnWriteArrayList<>();

        // ===== PathConfig mock =====
        when(config.getS3BucketsStats()).thenReturn(BUCKET);
        when(config.getCsvFolder()).thenReturn(CSV_FOLDER);

        // ローカル作業ディレクトリを作る（実ファイルを書きに行くため）
        Files.createDirectories(Paths.get(CSV_FOLDER));

        // ===== ReaderCurrentCsvInfoBean mock =====
        doNothing().when(bean).init();
        when(bean.getCsvInfo()).thenReturn(Collections.emptyMap());

        // ===== helper mock（条件で落とさない）=====
        var dummyResource = mock(CsvArtifactResource.class);
        when(helper.getData()).thenReturn(dummyResource);
        when(helper.csvCondition(anyList(), any())).thenReturn(true);
        when(helper.abnormalChk(anyList())).thenAnswer(inv -> inv.getArgument(0)); // そのまま通す

        // ===== s3Operator mock =====

        // buildKey はmockだとnullになるので、簡易結合ロジックをAnswerで与える
        when(s3Operator.buildKey(anyString(), anyString())).thenAnswer(inv -> {
            String prefix = inv.getArgument(0);
            String name = inv.getArgument(1);
            if (prefix == null || prefix.isBlank()) return name;
            prefix = prefix.replaceAll("/+$", "");
            return prefix + "/" + name;
        });

        // downloadToFile: seqListは「存在しない」想定で例外にして firstRun にする
        // teamListは空ファイルとして作っておく（後工程が読む可能性があるため）
        doAnswer(inv -> {
            String key = inv.getArgument(1);
            Path out = inv.getArgument(2);

            if (key.endsWith("seqList.txt")) {
                throw new RuntimeException("not found");
            }
            if (key.endsWith("data_team_list.txt")) {
                Files.writeString(out, "", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                return null;
            }
            throw new RuntimeException("unexpected download key: " + key);
        }).when(s3Operator).downloadToFile(eq(BUCKET), anyString(), any(Path.class));

        // listKeys:
        // - getMaxCsvNoFromS3 用（finalPrefix=""）: 既存CSVが2まである体にする
        // - commit用（tmpPrefix="tmp/..."）: 直前にuploadしたキー一覧を返す
        when(s3Operator.listKeys(eq(BUCKET), anyString())).thenAnswer(inv -> {
            String prefix = inv.getArgument(1);

            if (prefix != null && prefix.startsWith("tmp/")) {
                return new ArrayList<>(uploadedTmpKeys);
            }
            // finalPrefix="" の一覧（最大番号=2にしたい）
            return List.of("1.csv", "2.csv", "seqList.txt", "data_team_list.txt");
        });

        // uploadFile: 呼ばれたtmpKeyを記録
        doAnswer(inv -> {
            String key = inv.getArgument(1);
            uploadedTmpKeys.add(key);
            return null;
        }).when(s3Operator).uploadFile(eq(BUCKET), anyString(), any(Path.class));

        // copy/delete は成功扱い
        doNothing().when(s3Operator).copy(eq(BUCKET), anyString(), eq(BUCKET), anyString());
        doNothing().when(s3Operator).delete(eq(BUCKET), anyString());

        // ===== DBテストデータ投入（要調整）=====
        seedDb();
    }

    @AfterEach
    void cleanup() throws Exception {
        // 必要ならCSV_FOLDER配下の生成物を削除（ローカル汚染防止）
        // あなたの運用に合わせて残す/消すを選択してください
    }

    void seedDb() {
        // ここはあなたの実DBスキーマに合わせてINSERTしてください。
        // bookCsvDataRepository.findAllSeqsWithKey() が参照するテーブルへ
        // 「同カテゴリ+同home+同awayでグルーピングされる」ように2グループ作るとテストしやすいです。

        // 例（ダミー）：book_csv_data(seq, data_category, home_team_name, away_team_name, ... )
        // jdbc.update("insert into book_csv_data(seq, data_category, home_team_name, away_team_name) values (?,?,?,?)",
        //        1, "A", "HOME1", "AWAY1");
        // jdbc.update("insert into book_csv_data(seq, data_category, home_team_name, away_team_name) values (?,?,?,?)",
        //        2, "A", "HOME1", "AWAY1");
        // jdbc.update("insert into book_csv_data(seq, data_category, home_team_name, away_team_name) values (?,?,?,?)",
        //        3, "B", "HOME2", "AWAY2");

        // findByData(ids) が参照するテーブル/ビューにも DataEntity が返るように必要な行を入れてください。
    }

    @Test
    void 初回実行_全成功なら_tmpPut後に_commitされる() throws Exception {
        target.execute();

        // uploadFile: CSV複数 + data_team_list + seqList が上がる想定（件数はデータ次第）
        verify(s3Operator, atLeast(2)).uploadFile(eq(BUCKET), startsWith("tmp/"), any(Path.class));

        // commit: tmpのオブジェクト分 copy/delete が呼ばれる
        verify(s3Operator, atLeast(1)).copy(eq(BUCKET), startsWith("tmp/"), eq(BUCKET), anyString());
        verify(s3Operator, atLeast(1)).delete(eq(BUCKET), startsWith("tmp/"));
    }

    @Test
    void CSVのtmpPUTが1件でも失敗したら_commitしない() throws Exception {
        // 例えば「4.csv」を上げようとしたら失敗させる（ファイル名はデータとmax計算に依存）
        doAnswer(inv -> {
            String key = inv.getArgument(1);
            if (key.endsWith("4.csv")) {
                throw new RuntimeException("upload failed");
            }
            uploadedTmpKeys.add(key);
            return null;
        }).when(s3Operator).uploadFile(eq(BUCKET), anyString(), any(Path.class));

        target.execute();

        // failed>0 → commitしないので copy/delete が走らない
        verify(s3Operator, never()).copy(eq(BUCKET), startsWith("tmp/"), eq(BUCKET), anyString());
        verify(s3Operator, never()).delete(eq(BUCKET), startsWith("tmp/"));
    }
}
