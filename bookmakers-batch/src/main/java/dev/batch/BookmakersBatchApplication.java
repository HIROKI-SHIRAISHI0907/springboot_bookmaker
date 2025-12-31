package dev.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"dev.batch", "dev.common"})
public class BookmakersBatchApplication {

	public static void main(String[] args) {
		SpringApplication.run(BookmakersBatchApplication.class, args);
	}

}
