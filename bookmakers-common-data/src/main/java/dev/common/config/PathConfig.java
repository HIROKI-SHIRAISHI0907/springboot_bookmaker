package dev.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * パス設定Config
 * @author shiraishitoshio
 *
 */
@Configuration
public class PathConfig {

	/**
	 * JSONフォルダ
	 */
	@Value("${app.b001-json-folder}")
	private String b001JsonFolder;

	/**
	 * CSVフォルダ
	 */
	@Value("${app.csv-folder}")
	private String csvFolder;

	/**
	 * 未来フォルダ
	 */
	@Value("${app.future-csv-folder}")
	private String futureFolder;

	/**
	 * OUTPUTCSVフォルダ
	 */
	@Value("${app.output-csv-folder}")
	private String outputCsvFolder;

	/**
	 * TEAMCSVフォルダ
	 */
	@Value("${app.team-csv-folder}")
	private String teamCsvFolder;

	/**
	 * pythonファイル実行
	 */
	@Value("${process.python.root}")
	private String pythonRoot;

	/**
	 * pythonBinライブラリ
	 */
	@Value("${process.python.pythonBin}")
	private String pythonBin;

	/**
	 * S3リージョン
	 */
	@Value("${process.s3.region}")
	private String s3Region;

	/**
	 * S3バケット（teamSeasonDateData）
	 */
	@Value("${process.s3.buckets.teamSeasonDateData}")
	private String s3BucketsTeamSeasonDateData;

	/**
	 * S3バケット（teamData）
	 */
	@Value("${process.s3.buckets.teamData}")
	private String s3BucketsTeamData;

	/**
	 * S3バケット（teamMemberData）
	 */
	@Value("${process.s3.buckets.teamMemberData}")
	private String s3BucketsTeamMemberData;

	/**
	 * S3バケット（outputs）
	 */
	@Value("${process.s3.buckets.outputs}")
	private String s3BucketsOutputs;

	/**
	 * S3バケット（future）
	 */
	@Value("${process.s3.buckets.future}")
	private String s3BucketsFuture;

	/**
	 * S3バケット（stat）
	 */
	@Value("${process.s3.buckets.stats}")
	private String s3BucketsStats;

	/**
	 * JSONフォルダ作成パスを返す
	 * @return
	 */
	public String getB001JsonFolder() {
        return b001JsonFolder;
    }

	/**
	 * CSV作成パスを返す
	 * @return
	 */
	public String getCsvFolder() {
        return csvFolder;
    }

	/**
	 * 未来パスを返す
	 * @return
	 */
	public String getFutureFolder() {
        return futureFolder;
    }

	/**
	 * CSV作成パスを返す
	 * @return
	 */
	public String getOutputCsvFolder() {
        return outputCsvFolder;
    }

	/**
	 * CSV作成パスを返す
	 * @return
	 */
	public String getTeamCsvFolder() {
        return teamCsvFolder;
    }

	/**
	 * Python実行ルートディレクトリを取得する。
	 *
	 * @return Pythonファイル配置ルートパス
	 */
	public String getPythonRoot() {
	    return pythonRoot;
	}

	/**
	 * Python実行バイナリ名を取得する。
	 * <p>
	 * 例：python3, python
	 * </p>
	 *
	 * @return Python実行コマンド
	 */
	public String getPythonBin() {
	    return pythonBin;
	}

	/**
	 * S3リージョンを取得する。
	 *
	 * @return S3リージョン（例：ap-northeast-1）
	 */
	public String getS3Region() {
	    return s3Region;
	}

	/**
	 * TeamSeasonDateData用のS3バケット名を取得する。
	 *
	 * @return TeamSeasonDateData用S3バケット名
	 */
	public String getS3BucketsTeamSeasonDateData() {
	    return s3BucketsTeamSeasonDateData;
	}

	/**
	 * TeamData用のS3バケット名を取得する。
	 *
	 * @return TeamData用S3バケット名
	 */
	public String getS3BucketsTeamData() {
	    return s3BucketsTeamData;
	}

	/**
	 * TeamMemberData用のS3バケット名を取得する。
	 *
	 * @return TeamMemberData用S3バケット名
	 */
	public String getS3BucketsTeamMemberData() {
	    return s3BucketsTeamMemberData;
	}

	/**
	 * Outputs（汎用出力）用のS3バケット名を取得する。
	 *
	 * @return Outputs用S3バケット名
	 */
	public String getS3BucketsOutputs() {
	    return s3BucketsOutputs;
	}

	/**
	 * Future用のS3バケット名を取得する。
	 *
	 * @return Future用S3バケット名
	 */
	public String getS3BucketsFuture() {
	    return s3BucketsFuture;
	}

	/**
	 * Stat用のS3バケット名を取得する。
	 *
	 * @return Stat用S3バケット名
	 */
	public String getS3BucketsStats() {
	    return s3BucketsStats;
	}

}
