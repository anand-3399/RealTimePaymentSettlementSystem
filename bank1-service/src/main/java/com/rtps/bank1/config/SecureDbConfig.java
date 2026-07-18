package com.rtps.bank1.config;

import java.util.HashMap;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.zaxxer.hikari.HikariDataSource;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "com.rtps.bank1.secure.repository", entityManagerFactoryRef = "secureEntityManagerFactory", transactionManagerRef = "secureTransactionManager")
public class SecureDbConfig {

	@Bean(name = "secureDataSource")
	public DataSource secureDataSource(Environment env) {
		String url = env.getProperty("spring.datasource.secure.url");
		String username = env.getProperty("spring.datasource.secure.username");
		String password = env.getProperty("spring.datasource.secure.password");
		String driver = env.getProperty("spring.datasource.secure.driver-class-name");

		if (url == null || url.trim().isEmpty() || url.contains("${")) {
			System.out.println(
					"WARN: SECURE URL is null, empty, or unresolved. Using dummy H2 database to prevent hard crash.");
			HikariDataSource ds = new HikariDataSource();
			ds.setJdbcUrl("jdbc:h2:mem:secure_dummy;DB_CLOSE_DELAY=-1");
			ds.setDriverClassName("org.h2.Driver");
			ds.setUsername("sa");
			ds.setPassword("");
			return ds;
		}

		return DataSourceBuilder.create().url(url).username(username).password(password).driverClassName(driver)
				.build();
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
		properties.put("hibernate.physical_naming_strategy",
				"org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
		em.setJpaPropertyMap(properties);

		return em;
	}

	@Bean(name = "secureTransactionManager")
	public PlatformTransactionManager secureTransactionManager(
			@Qualifier("secureEntityManagerFactory") LocalContainerEntityManagerFactoryBean secureEntityManagerFactory) {
		return new JpaTransactionManager(secureEntityManagerFactory.getObject());
	}
}
