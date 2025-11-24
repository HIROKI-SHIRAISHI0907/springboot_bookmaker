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
import org.springframework.context.annotation.Primary;

@Configuration
// ★ この DB を使う Mapper のパッケージを指定
//   例: StatSizeFinalizeMasterRepository などを dev.mng.domain.repository.user 配下に移動しておく
@MapperScan(
    basePackages = "dev.mng.domain.repository.user",
    sqlSessionFactoryRef = "userSqlSessionFactory"
)
public class UserDbConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.user")
    public DataSourceProperties userDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "userDataSource")
    @Primary
    public DataSource userDataSource() {
        return userDataSourceProperties()
                .initializeDataSourceBuilder()
                .build();
    }

    @Bean(name = "userSqlSessionFactory")
    @Primary
    public SqlSessionFactory userSqlSessionFactory(
            @Qualifier("userDataSource") DataSource dataSource
    ) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        // Mapper XML のパスを指定したい場合はここでセット
        // factoryBean.setMapperLocations(
        //    new PathMatchingResourcePatternResolver().getResources("classpath*:mybatis/user/*.xml")
        // );
        return factoryBean.getObject();
    }

    @Bean(name = "userSqlSessionTemplate")
    @Primary
    public SqlSessionTemplate userSqlSessionTemplate(
            @Qualifier("userSqlSessionFactory") SqlSessionFactory sqlSessionFactory
    ) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }
}
