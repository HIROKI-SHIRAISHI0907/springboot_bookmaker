package dev.application.config;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.annotation.MapperScans;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/**
 * アプリ共通設定。
 * <ul>
 *   <li>Component / Mapper のスキャン</li>
 *   <li>BM_M024 相関登録用スレッドプールの提供（calcCorrelationExecutor）</li>
 * </ul>
 */
@Configuration
@ComponentScan(basePackages = {
        "dev.application.constant",
        "dev.application.common",
        "dev.application.analyze",
        "dev.common.getstatinfo",
        "dev.common.findcsv",
        "dev.common.server",
        "dev.common.delete",
        "dev.common.copy",
        "dev.common.convertcsvandread",
        "dev.common.readfile",
        "dev.common.logger"
})
@MapperScans({
        @MapperScan(
                basePackages = "dev.application.domain.repository.bm",
                annotationClass = org.apache.ibatis.annotations.Mapper.class,
                sqlSessionTemplateRef = "bmSqlSessionTemplate"
        ),
        @MapperScan(
                basePackages = "dev.application.domain.repository.master",
                annotationClass = org.apache.ibatis.annotations.Mapper.class,
                sqlSessionTemplateRef = "masterSqlSessionTemplate"
        )
})
public class BookMakersConfig {

    /**
     * BM_M024 の登録処理で使用する共有 ExecutorService。
     *
     * <p>特長:</p>
     * <ul>
     *   <li>CPU コア数×2 の最大スレッド</li>
     *   <li>キューは適度に制限（バックプレッシャをかけて OOM を回避）</li>
     *   <li>アイドル 60 秒でコア超のスレッドは終了</li>
     *   <li>拒否時は呼び出しスレッドで実行（最終安全策）</li>
     * </ul>
     *
     * <p>CalcCorrelationStat 側では
     * {@code @Qualifier("calcCorrelationExecutor")} で注入して利用します。</p>
     */
    @Bean(destroyMethod = "shutdown")
    @Qualifier("calcCorrelationExecutor")
    public ExecutorService calcCorrelationExecutor() {
        int cores = Runtime.getRuntime().availableProcessors();
        int corePoolSize = Math.max(2, cores);          // 最低2
        int maxPoolSize  = Math.max(corePoolSize, cores * 2);
        int queueCapacity = 1024;                        // 必要に応じて調整

        ThreadFactory factory = new ThreadFactory() {
            private final ThreadFactory defaultFactory = java.util.concurrent.Executors.defaultThreadFactory();
            private int seq = 0;
            @Override
            public Thread newThread(Runnable r) {
                Thread t = defaultFactory.newThread(r);
                t.setName("bm-m024-db-" + (++seq));
                t.setDaemon(false);
                t.setUncaughtExceptionHandler((th, ex) -> {
                    // ここでログ基盤に流すなら、Static ロガー等で対応
                    System.err.println("[bm-m024-db] Uncaught: " + ex.getMessage());
                });
                return t;
            }
        };

        ThreadPoolExecutor exec = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                factory,
                new ThreadPoolExecutor.CallerRunsPolicy() // 飽和時は呼び出しスレッドで実行
        );
        exec.allowCoreThreadTimeOut(true);
        return exec;
    }

 // ===== メインDB (soccer_bm) =====
    @Primary
    @Bean(name = "bmDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.bm")
    public DataSource bmDataSource() {
        return DataSourceBuilder.create().build();
    }

    // ===== マスターDB (soccer_bm_master) =====
    @Bean(name = "masterDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.master")
    public DataSource masterDataSource() {
        return DataSourceBuilder.create().build();
    }

    // ===== SqlSessionFactory (bm) =====
    @Primary
    @Bean(name = "bmSqlSessionFactory")
    public SqlSessionFactory bmSqlSessionFactory(
            @Qualifier("bmDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        return factory.getObject();
    }

    // ===== SqlSessionFactory (master) =====
    @Bean(name = "masterSqlSessionFactory")
    public SqlSessionFactory masterSqlSessionFactory(
            @Qualifier("masterDataSource") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        return factory.getObject();
    }

    // ===== TransactionManager (bm) =====
    @Primary
    @Bean(name = "bmTxManager")
    public DataSourceTransactionManager bmTxManager(
            @Qualifier("bmDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    // ===== TransactionManager (master) =====
    @Bean(name = "masterTxManager")
    public DataSourceTransactionManager masterTxManager(
            @Qualifier("masterDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    // ===== SqlSessionTemplate (bm) =====
    @Primary
    @Bean(name = "bmSqlSessionTemplate")
    public SqlSessionTemplate bmSqlSessionTemplate(
            @Qualifier("bmSqlSessionFactory") SqlSessionFactory factory) {
        return new SqlSessionTemplate(factory);
    }

    // ===== SqlSessionTemplate (master) =====
    @Bean(name = "masterSqlSessionTemplate")
    public SqlSessionTemplate masterSqlSessionTemplate(
            @Qualifier("masterSqlSessionFactory") SqlSessionFactory factory) {
        return new SqlSessionTemplate(factory);
    }
}
