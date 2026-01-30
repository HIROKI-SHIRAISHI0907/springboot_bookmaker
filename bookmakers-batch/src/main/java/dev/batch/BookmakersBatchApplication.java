package dev.batch;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"dev.batch", "dev.common"})
@MapperScan("dev.batch")
public class BookmakersBatchApplication {

	public static void main(String[] args) {
		SpringApplication.run(BookmakersBatchApplication.class, args);
	}

}
