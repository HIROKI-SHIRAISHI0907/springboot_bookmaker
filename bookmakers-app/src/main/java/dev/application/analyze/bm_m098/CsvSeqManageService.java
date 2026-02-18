package dev.application.analyze.bm_m098;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.application.domain.repository.bm.CsvSeqManageRepository;
import dev.common.getinfo.GetStatInfo;
import lombok.Data;

/**
 * CSV連番管理サービス
 *
 * 要件:
 * - S3上の CSV は "X.csv" (番号のみ)
 * - 最低限「最後に成功したCSV番号(last_success_csv)」を管理できればOK
 * - 巻き戻り防止（前進のみ更新）
 * - 競合防止（SELECT FOR UPDATE で排他）
 */
@Service
public class CsvSeqManageService {

    /** 固定運用する管理行ID（必要なら job ごとに分けてOK） */
    public static final int DEFAULT_ID = 1;

    /** job名（監視しやすいように固定文字列） */
    public static final String DEFAULT_JOB = "MainStat";

    @Autowired
    private CsvSeqManageRepository repo;

    @Autowired
    private GetStatInfo getStatInfo;

    /**
     * 取り込み範囲を決める（排他込み）
     * - DBの last_success_csv を読み
     * - S3最大番号(max) を読み
     * - from=last+1, to=max を返す
     *
     * 追いついている場合は null を返す
     */
    @Transactional
    public CsvSeqRange decideRangeOrNull() {
        CsvSeqManageEntity row = lockOrCreate(DEFAULT_ID, DEFAULT_JOB);

        int last = safeInt(row.getLastSuccessCsv(), 0);

        int maxOnS3 = getStatInfo.getMaxCsvNo("0", null);

        int from = last + 1;
        int to = maxOnS3;

        if (from > to) return null;

        return new CsvSeqRange(from, to, last, maxOnS3);
    }

    /**
     * 成功したら last_success_csv を進める（前進のみ）
     * 例: range.to() を渡す
     */
    @Transactional
    public void markSuccess(int newLastSuccessCsv) {
        // 念のためロックしておく（同時実行抑止）
        repo.selectForUpdate(DEFAULT_ID);

        // 前進のみ更新（巻き戻り防止）
        repo.updateLastSuccessIfGreater(DEFAULT_ID, newLastSuccessCsv);
    }

    /**
     * 管理行をロックして取り、無ければ作成して再ロックする
     */
    private CsvSeqManageEntity lockOrCreate(int id, String job) {
        CsvSeqManageEntity row = repo.selectForUpdate(id);
        if (row != null) return row;

        CsvSeqManageEntity init = new CsvSeqManageEntity();
        init.setId(id);
        init.setJobName(job);
        init.setLastSuccessCsv(0);
        init.setBackRange(0);

        repo.insert(init);
        return repo.selectForUpdate(id);
    }

    private static int safeInt(Integer v, int def) {
        return (v == null) ? def : v.intValue();
    }

    // ============================================
    // DTO: このサービス内で使う「今回の読み取り範囲」
    // ============================================

    @Data
    public static class CsvSeqRange {
        private final int from;
        private final int to;
        private final int lastOnDb;
        private final int maxOnS3;

        public CsvSeqRange(int from, int to, int lastOnDb, int maxOnS3) {
            this.from = from;
            this.to = to;
            this.lastOnDb = lastOnDb;
            this.maxOnS3 = maxOnS3;
        }

        @Override
        public String toString() {
            return "CsvSeqRange{from=" + from + ", to=" + to
                    + ", lastOnDb=" + lastOnDb + ", maxOnS3=" + maxOnS3 + "}";
        }
    }
}
