package dev.application;

import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(
	    exclude = MybatisAutoConfiguration.class
	)
	@ComponentScan(basePackages = {
	    "dev.application",
	    "dev.common",
	    "dev.mng"
	})
@MapperScan("dev.application.domain")
//@EnableScheduling
public class BookmakersAppApplication {

	public static void main(String[] args) {
		SpringApplication.run(BookmakersAppApplication.class, args);
	}

}
