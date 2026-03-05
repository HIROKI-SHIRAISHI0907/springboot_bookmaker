package dev.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication(scanBasePackages = {"dev.batch", "dev.common"})
@EnableTransactionManagement
public class BookmakersBatchApplication {

	public static void main(String[] args) {
		SpringApplication.run(BookmakersBatchApplication.class, args);
	}

}
