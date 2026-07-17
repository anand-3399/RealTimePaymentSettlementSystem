package com.rtps.bank1.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = "com.rtps.bank1.secure.repository",
        entityManagerFactoryRef = "secureEntityManagerFactory",
        transactionManagerRef = "secureTransactionManager"
)
public class SecureDbConfig {

    @Bean(name = "secureDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.secure")
    public DataSource secureDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "secureEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean secureEntityManagerFactory(
            @Qualifier("secureDataSource") DataSource secureDataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(secureDataSource);
        em.setPackagesToScan("com.rtps.bank1.secure.entity");
        em.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        
        HashMap<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "update");
        properties.put("hibernate.dialect", "org.hibernate.dialect.OracleDialect");
        properties.put("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
        em.setJpaPropertyMap(properties);
        
        return em;
    }

    @Bean(name = "secureTransactionManager")
    public PlatformTransactionManager secureTransactionManager(
            @Qualifier("secureEntityManagerFactory") LocalContainerEntityManagerFactoryBean secureEntityManagerFactory) {
        return new JpaTransactionManager(secureEntityManagerFactory.getObject());
    }
}
