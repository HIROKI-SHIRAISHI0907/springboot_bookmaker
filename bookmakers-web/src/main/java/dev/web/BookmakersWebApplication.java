package dev.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"dev.web", "dev.common", "dev.batch"})
public class BookmakersWebApplication {

	public static void main(String[] args) {
		SpringApplication.run(BookmakersWebApplication.class, args);
	}

}
