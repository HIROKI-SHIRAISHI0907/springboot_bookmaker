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

}
