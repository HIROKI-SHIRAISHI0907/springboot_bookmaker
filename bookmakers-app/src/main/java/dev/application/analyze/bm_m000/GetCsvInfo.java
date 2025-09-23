package dev.application.analyze.bm_m000;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import dev.common.config.PathConfig;
import dev.common.constant.BookMakersCommonConst;
import dev.common.entity.BookDataEntity;
import dev.common.find.dto.FindBookInputDTO;
import dev.common.find.dto.FindBookOutputDTO;
import dev.common.findcsv.FindStat;
import dev.common.getstatinfo.GetStatInfo;
import dev.common.logger.ManageLoggerComponent;
import dev.common.readfile.ReadStat;
import dev.common.readfile.dto.ReadFileOutputDTO;

/**
 * 既存CSV情報取得管理クラス
 */
@Component
public class GetCsvInfo {

    /** プロジェクト名 */
    private static final String PROJECT_NAME = GetStatInfo.class.getProtectionDomain()
            .getCodeSource().getLocation().getPath();

    /** クラス名 */
    private static final String CLASS_NAME = GetStatInfo.class.getSimpleName();

    /** Configクラス */
    @Autowired
    private PathConfig config;

    /** 統計データCsv読み取りクラス */
    @Autowired
    private FindStat findStatCsv;

    /** ファイル読み込みクラス */
    @Autowired
    private ReadStat readStat;

    /** CSV用のスレッドプール（CsvQueueConfigで定義） */
    @Autowired
    private ThreadPoolTaskExecutor csvTaskExecutor;

    /** ログ管理クラス */
    @Autowired
    private ManageLoggerComponent manageLoggerComponent;

    /** ルートパス */
    private String PATH;

    /**
     * filePath名(=ファイル名) -> CSVの中身(List<BookDataEntity>) のマップを返す
     * ・ファイル名(拡張子除く)を数値として昇順ソート
     * ・並列で読み込みつつ、ソート順のまま LinkedHashMap に格納（順序保持）
     */
    public Map<String, List<BookDataEntity>> getDataByFile(String csvNumber, String csvBackNumber) {
        final String METHOD_NAME = "getDataByFile";
        PATH = config.getCsvFolder();

        long startTime = System.nanoTime();

        // 入力DTO組み立て
        FindBookInputDTO in = setBookInputDTO(csvNumber, csvBackNumber);

        // CSV一覧取得
        FindBookOutputDTO out = this.findStatCsv.execute(in);
        if (!BookMakersCommonConst.NORMAL_CD.equals(out.getResultCd())) {
            this.manageLoggerComponent.createBusinessException(
                    out.getExceptionProject(), out.getExceptionClass(), out.getExceptionMethod(),
                    out.getErrMessage(), out.getThrowAble());
        }
        List<String> fileStatList = out.getBookList();

        // 例: "235.csv" を 235 として比較 → 数値昇順（非数値は末尾）
        fileStatList.sort(Comparator.nullsLast(
                Comparator.comparingLong(GetCsvInfo::fileNumber)
                          .thenComparing(Comparator.naturalOrder())
        ));

        final int csvCount = fileStatList.size();
        this.manageLoggerComponent.debugInfoLog(
                PROJECT_NAME, CLASS_NAME, METHOD_NAME, "CSV件数: " + csvCount, "GetCsvInfo");

        // 空なら即返し
        Map<String, List<BookDataEntity>> result = new LinkedHashMap<>();
        if (csvCount == 0) {
            return result;
        }

        // 同時実行を制限（設定値に合わせるなら getMaxPoolSize() を利用）
        final int concurrency = 8;
        final Semaphore gate = new Semaphore(concurrency);

        // ソート済みの順序を維持するため、(file, future) のペアで保持
        class Job {
            final String file;
            final CompletableFuture<ReadFileOutputDTO> future;
            Job(String file, CompletableFuture<ReadFileOutputDTO> future) {
                this.file = file; this.future = future;
            }
        }
        List<Job> jobs = new ArrayList<>(fileStatList.size());

        // 並列で読み込みを開始
        for (String file : fileStatList) {
            try {
                gate.acquire();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                this.manageLoggerComponent.createBusinessException(
                        PROJECT_NAME, CLASS_NAME, METHOD_NAME, "Semaphore acquire 中断", ie);
                break;
            }
            CompletableFuture<ReadFileOutputDTO> f = CompletableFuture
                    .supplyAsync(() -> this.readStat.getFileBody(file), csvTaskExecutor)
                    .whenComplete((r, t) -> gate.release());
            jobs.add(new Job(file, f));
        }

        // ソート順に join して、LinkedHashMap に挿入（=昇順のまま）
        for (Job job : jobs) {
            try {
                ReadFileOutputDTO dto = job.future.join();
                if (dto == null || dto.getReadHoldDataList() == null || dto.getReadHoldDataList().isEmpty()) {
                    this.manageLoggerComponent.createBusinessException(
                            PROJECT_NAME, CLASS_NAME, METHOD_NAME, "dto/readHoldDataList: null/empty", null);
                    continue;
                }
                List<BookDataEntity> list = dto.getReadHoldDataList();

                // 総CSV件数を必要なら保持（先頭のみ／全件どちらでもOK）
                list.get(0).setFileCount(csvCount);
                // for (BookDataEntity e : list) { e.setFileCount(csvCount); }

                // キーは「ソート基準に使った元ファイル」のファイル名（衝突が嫌ならフルパスに）
                String key = Paths.get(job.file).getFileName().toString();
                // String key = Paths.get(job.file).toString(); // フルパスにしたい場合

                result.computeIfAbsent(key, k -> new ArrayList<>()).addAll(list);

            } catch (RuntimeException e) {
                this.manageLoggerComponent.debugErrorLog(PROJECT_NAME, CLASS_NAME, METHOD_NAME, null, e);
                this.manageLoggerComponent.createBusinessException(
                        PROJECT_NAME, CLASS_NAME, METHOD_NAME, "非同期処理中にエラー", e);
            }
        }

        long durationMs = (System.nanoTime() - startTime) / 1_000_000;
        System.out.println("時間: " + durationMs);
        return result;
    }

    /** 読み取りinputDTOに設定する */
    private FindBookInputDTO setBookInputDTO(String csvNumber, String csvBackNumber) {
        FindBookInputDTO findBookInputDTO = new FindBookInputDTO();
        findBookInputDTO.setDataPath(PATH);
        findBookInputDTO.setCopyFlg(false);
        findBookInputDTO.setGetBookFlg(false);
        findBookInputDTO.setCsvNumber(csvNumber);
        findBookInputDTO.setCsvBackNumber(csvBackNumber);
        String[] containsList = new String[6];
        containsList[0] = "breakfile";
        containsList[1] = "all.csv";
        containsList[2] = "conditiondata/";
        containsList[3] = "python_analytics/";
        containsList[4] = "average_stats/";
        findBookInputDTO.setContainsList(containsList);
        findBookInputDTO.setSuffixFile(BookMakersCommonConst.CSV);
        return findBookInputDTO;
    }

    /** ファイル名（拡張子除去）が数値ならそれを返し、そうでなければ Long.MAX_VALUE を返す */
    private static long fileNumber(String path) {
        String name = Paths.get(path).getFileName().toString();   // 例: "2348.csv"
        int dot = name.lastIndexOf('.');
        String base = (dot >= 0) ? name.substring(0, dot) : name; // 例: "2348"
        return base.matches("\\d+") ? Long.parseLong(base) : Long.MAX_VALUE;
    }
}
