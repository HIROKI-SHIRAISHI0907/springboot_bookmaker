package dev.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"dev.web", "dev.common"})
@MapperScan("dev.web.repository")
public class BookmakersWebApplication {

	public static void main(String[] args) {
		SpringApplication.run(BookmakersWebApplication.class, args);
	}

}
