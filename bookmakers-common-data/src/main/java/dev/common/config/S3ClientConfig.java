package dev.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.common.constant.S3Const;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * S3Client を Spring Bean として登録する設定クラス。
 * アプリ全体で単一の S3Client インスタンスを共有する。
 *
 * @author shiraishitoshio
 */
@Configuration
public class S3ClientConfig {

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(S3Const.TOKYO_REGION_AP_NORTHEAST_1))
                .build();
    }
}