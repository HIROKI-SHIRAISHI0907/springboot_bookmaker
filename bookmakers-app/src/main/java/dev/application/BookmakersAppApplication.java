package dev.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
		"dev.application",
		"dev.common" // ← これを追加
})
//@EnableScheduling
public class BookmakersAppApplication {

	public static void main(String[] args) {
		SpringApplication.run(BookmakersAppApplication.class, args);
	}

}
