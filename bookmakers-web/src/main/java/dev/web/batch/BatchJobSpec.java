package dev.web.batch;

import lombok.Data;

/**
 * バッチSpec
 * @author shiraishitoshio
 *
 */
@Data
public class BatchJobSpec {

	/** ジョブコード */
    private final String jobCode;

    /** タスク定義 */
    private final String taskDefinition;

    /** コンテナ名 */
    private final String containerName;

    /**
     * コンストラクタ
     * @param b ビルダー
     */
    private BatchJobSpec(Builder b) {
        this.jobCode = b.jobCode;
        this.taskDefinition = b.taskDefinition;
        this.containerName = b.containerName;
    }

    /** ビルダ- */
    public static Builder builder() { return new Builder(); }

    /**
     * ビルダー
     * @author shiraishitoshio
     *
     */
    public static class Builder {
    	/** ジョブコード */
        private String jobCode;

        /** タスク定義 */
        private String taskDefinition;

        /** コンテナ名 */
        private String containerName;

        public Builder jobCode(String v) { this.jobCode = v; return this; }
        public Builder taskDefinition(String v) { this.taskDefinition = v; return this; }
        public Builder containerName(String v) { this.containerName = v; return this; }

        /**
         * ビルドクラス
         * @return
         */
        public BatchJobSpec build() {
            if (jobCode == null || jobCode.isBlank()) throw new IllegalArgumentException("jobCode required");
            if (taskDefinition == null || taskDefinition.isBlank()) throw new IllegalArgumentException("taskDefinition required");
            if (containerName == null || containerName.isBlank()) throw new IllegalArgumentException("containerName required");
            return new BatchJobSpec(this);
        }
    }
}
