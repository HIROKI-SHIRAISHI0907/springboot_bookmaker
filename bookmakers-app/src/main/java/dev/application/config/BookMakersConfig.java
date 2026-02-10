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
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/**
 * アプリ共通設定。
 * - Component / Mapper のスキャン
 * - 2 DataSource（bm / master） + MyBatis / TxManager を明示構成
 * - BM_M024 相関登録用スレッドプールの提供（calcCorrelationExecutor）
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
                sqlSessionTemplateRef = "bmSqlSessionTemplate"
        ),
        @MapperScan(
                basePackages = "dev.application.domain.repository.master",
                sqlSessionTemplateRef = "masterSqlSessionTemplate"
        )
})
public class BookMakersConfig {

    // =========================================================
    // Executor
    // =========================================================

    @Bean(destroyMethod = "shutdown")
    @Qualifier("calcCorrelationExecutor")
    public ExecutorService calcCorrelationExecutor() {
        int cores = Runtime.getRuntime().availableProcessors();
        int corePoolSize = Math.max(2, cores);
        int maxPoolSize  = Math.max(corePoolSize, cores * 2);
        int queueCapacity = 1024;

        ThreadFactory factory = new ThreadFactory() {
            private final ThreadFactory defaultFactory = java.util.concurrent.Executors.defaultThreadFactory();
            private int seq = 0;

            @Override
            public Thread newThread(Runnable r) {
                Thread t = defaultFactory.newThread(r);
                t.setName("bm-m024-db-" + (++seq));
                t.setDaemon(false);
                t.setUncaughtExceptionHandler((th, ex) ->
                        System.err.println("[bm-m024-db] Uncaught: " + ex.getMessage())
                );
                return t;
            }
        };

        ThreadPoolExecutor exec = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                factory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        exec.allowCoreThreadTimeOut(true);
        return exec;
    }

    // =========================================================
    // DataSourceProperties（bm / master）
    // application-prod.yml:
    // spring.datasource.bm.url / username / password / driver-class-name
    // spring.datasource.master.url / username / password / driver-class-name
    // =========================================================

    @Bean(name = "bmDataSourceProperties")
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.bm")
    public DataSourceProperties bmDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "masterDataSourceProperties")
    @ConfigurationProperties(prefix = "spring.datasource.master")
    public DataSourceProperties masterDataSourceProperties() {
        return new DataSourceProperties();
    }

    // =========================================================
    // DataSource（bm / master）
    // =========================================================

    @Bean(name = "bmDataSource")
    @Primary
    public DataSource bmDataSource(
            @Qualifier("bmDataSourceProperties") DataSourceProperties props
    ) {
        // url/username/password/driver-class-name を確実に反映
        return props.initializeDataSourceBuilder().build();
    }

    @Bean(name = "masterDataSource")
    public DataSource masterDataSource(
            @Qualifier("masterDataSourceProperties") DataSourceProperties props
    ) {
        return props.initializeDataSourceBuilder().build();
    }

    // =========================================================
    // TransactionManager（bm / master）
    // =========================================================

    @Bean(name = "bmTxManager")
    @Primary
    public DataSourceTransactionManager bmTxManager(
            @Qualifier("bmDataSource") DataSource ds
    ) {
        return new DataSourceTransactionManager(ds);
    }

    @Bean(name = "masterTxManager")
    public DataSourceTransactionManager masterTxManager(
            @Qualifier("masterDataSource") DataSource ds
    ) {
        return new DataSourceTransactionManager(ds);
    }

    // =========================================================
    // MyBatis: SqlSessionFactory（bm / master）
    // ※ mybatis.configuration(map-underscore-to-camel-case等)は
    //   application.yml 側の MyBatisAutoConfiguration が拾ってくれる構成もありますが、
    //   2 factory を自前で作る場合は必要に応じてここで setConfiguration してください。
    //   （現状、Mapperは注釈SQL中心なので最低限でOK）
    // =========================================================

    @Bean(name = "bmSqlSessionFactory")
    @Primary
    public SqlSessionFactory bmSqlSessionFactory(
            @Qualifier("bmDataSource") DataSource ds
    ) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(ds);
        return factory.getObject();
    }

    @Bean(name = "masterSqlSessionFactory")
    public SqlSessionFactory masterSqlSessionFactory(
            @Qualifier("masterDataSource") DataSource ds
    ) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(ds);
        return factory.getObject();
    }

    // =========================================================
    // MyBatis: SqlSessionTemplate（bm / master）
    // =========================================================

    @Bean(name = "bmSqlSessionTemplate")
    @Primary
    public SqlSessionTemplate bmSqlSessionTemplate(
            @Qualifier("bmSqlSessionFactory") SqlSessionFactory sf
    ) {
        return new SqlSessionTemplate(sf);
    }

    @Bean(name = "masterSqlSessionTemplate")
    public SqlSessionTemplate masterSqlSessionTemplate(
            @Qualifier("masterSqlSessionFactory") SqlSessionFactory sf
    ) {
        return new SqlSessionTemplate(sf);
    }
}
