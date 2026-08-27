package dev.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {"dev.web", "dev.common"})
@ConfigurationPropertiesScan(basePackages = {"dev.common.mail"})
public class BookmakersWebApplication {

	public static void main(String[] args) {
		SpringApplication.run(BookmakersWebApplication.class, args);
	}

}
