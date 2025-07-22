package dev.application.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 設定クラス
 * @author shiraishitoshio
 *
 */
@Configuration
@ComponentScan(basePackages = {
	"dev.application.constant",
	"dev.application.common",
	"dev.common.getstatinfo",
	"dev.common.findcsv",
	"dev.common.delete",
	"dev.common.copy",
	"dev.common.convertcsvandread",
	"dev.common.readfile",
	"dev.common.logger"
})
@MapperScan(basePackages = {
		"dev.application.domain.repository",
		"dev.application.analyze"
})
public class BookMakersConfig {


}
