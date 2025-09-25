package dev.common.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("dev.common")
@MapperScan(
		basePackages = "dev.mng.domain.repository",
		annotationClass = org.apache.ibatis.annotations.Mapper.class // MyBatisの@Mapper限定
)
public class PathConfig {

	/**
	 * CSVフォルダ
	 */
	@Value("${app.csv-folder}")
	private String csvFolder;

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
	 * CSV作成パスを返す
	 * @return
	 */
	public String getCsvFolder() {
        return csvFolder;
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
