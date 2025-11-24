package dev.mng.config;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
// ★ data テーブルを使う Mapper のパッケージ
//   BookCsvDataRepository などを dev.mng.domain.repository.data 配下に置くイメージ
@MapperScan(
    basePackages = "dev.mng.domain.repository.data",
    sqlSessionFactoryRef = "dataSqlSessionFactory"
)
public class DataDbConfig {

    @Bean
    @ConfigurationProperties("spring.datasource.datas")
    public DataSourceProperties dataDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "dataDataSource")
    public DataSource dataDataSource() {
        return dataDataSourceProperties()
                .initializeDataSourceBuilder()
                .build();
    }

    @Bean(name = "dataSqlSessionFactory")
    public SqlSessionFactory dataSqlSessionFactory(
            @Qualifier("dataDataSource") DataSource dataSource
    ) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        // factoryBean.setMapperLocations(
        //    new PathMatchingResourcePatternResolver().getResources("classpath*:mybatis/data/*.xml")
        // );
        return factoryBean.getObject();
    }

    @Bean(name = "dataSqlSessionTemplate")
    public SqlSessionTemplate dataSqlSessionTemplate(
            @Qualifier("dataSqlSessionFactory") SqlSessionFactory sqlSessionFactory
    ) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }
}
