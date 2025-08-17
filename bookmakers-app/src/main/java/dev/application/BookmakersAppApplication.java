package dev.application;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan(
	basePackages = "dev.application.domain.repository",
	annotationClass = org.apache.ibatis.annotations.Mapper.class // ← MyBatisの@Mapper限定
)
//@EnableScheduling
public class BookmakersAppApplication {

	public static void main(String[] args) {
		SpringApplication.run(BookmakersAppApplication.class, args);
	}

}
