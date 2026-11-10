package dev.web.api.bm_a011;

import lombok.Data;

@Data
public class IngestedDataReferenceRequest {

    /** 国 */
    private String country;

    /**
     * true:
     *   「終了済」データがない、または未来データがない
     *   のいずれかを満たすものだけ返す
     *
     * false:
     *   条件を考慮せず、国に該当するものを全件返す
     */
    private boolean onlyMissingFinishedOrFuture = false;

    /** 未来データ（試合予定）が無いもの（2026-11 追加。チェックしたものは「いずれか」＝OR でまとめる） */
    private boolean missingFuture = false;

    /** 終了済データが無いもの（2026-11 追加） */
    private boolean missingFinished = false;

    /** 統計詳細データ（match_detail_match）が無いもの（2026-11 追加） */
    private boolean missingDetail = false;

    /** 不足の条件が 1 つでもあるか */
    public boolean hasMissingFilter() {
        return onlyMissingFinishedOrFuture || missingFuture || missingFinished || missingDetail;
    }

    /** キャッシュのキー用（未来・終了済・統計詳細の順に 0/1） */
    public String missingFilterKey() {
        boolean all = onlyMissingFinishedOrFuture;
        return (all || missingFuture ? "1" : "0") + (all || missingFinished ? "1" : "0") + (all || missingDetail ? "1" : "0");
    }

    /** page offset */
    private int offset = 0;

    /** page size */
    private int limit = 100;
}
