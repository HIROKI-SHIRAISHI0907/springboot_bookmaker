package dev.mng.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PathConfig {

	@Value("${app.csv-folder}")
	private String csvFolder;

	/**
	 * CSV作成パスを返す
	 * @return
	 */
	public String getCsvFolder() {
        return csvFolder;
    }

}
