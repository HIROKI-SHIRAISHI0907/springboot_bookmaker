package dev.batch.config;

import javax.sql.DataSource;

import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import dev.common.logger.ManageLoggerComponentImpl;


@EnableTransactionManagement
@MapperScan(basePackages = {
        "dev.batch.repository.master",
        "dev.common.mapper"
})
@ComponentScan(
        basePackages = {
                "dev.batch.bm_b002",
                "dev.batch.bm_b003",
                "dev.batch.bm_b004",
                "dev.common"
        },
        excludeFilters = {
                // ★ここで「落ちる本番Bean」を作らせない
        		@ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {
                        ManageLoggerComponentImpl.class
                })
        }
)
@ActiveProfiles("ut")
public class TestMyBatisH2Config {

    @Bean
    public DataSource dataSource() {
        // H2 in-memory（PostgreSQL互換モード）
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:bm_ut;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        ds.setUsername("sa");
        ds.setPassword("");
        return ds;
    }

    @Bean
    public PlatformTransactionManager transactionManager(DataSource ds) {
        return new DataSourceTransactionManager(ds);
    }

    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(ds);

        Configuration mybatisConf = new Configuration();
        mybatisConf.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(mybatisConf);

        return factory.getObject();
    }

    @Bean
    public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sf) {
        return new SqlSessionTemplate(sf);
    }

    /**
     * schema.sql をH2へ流し込む（src/test/resources/schema.sql）
     */
    @Bean
    public boolean initSchema(DataSource ds) {
        ResourceDatabasePopulator populator =
                new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));
        populator.setContinueOnError(false);
        DatabasePopulatorUtils.execute(populator, ds);
        return true;
    }
}
