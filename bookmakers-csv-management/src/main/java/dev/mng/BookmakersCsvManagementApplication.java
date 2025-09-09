package dev.mng;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "dev")
public class BookmakersCsvManagementApplication {

	public static void main(String[] args) {
		SpringApplication.run(BookmakersCsvManagementApplication.class, args);
	}

}
