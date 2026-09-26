package dev.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AWS ダッシュボードの日次集計（@Scheduled）を動かすための設定。
 * すでにアプリのどこかに @EnableScheduling があれば、このクラスは無くても動く（あっても問題ない）。
 */
@Configuration
@EnableScheduling
public class AwsStatsSchedulingConfig {
}
