package dev.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BookmakersAppApplication {

	public static void main(String[] args) {
		SpringApplication.run(BookmakersAppApplication.class, args);
	}

}
