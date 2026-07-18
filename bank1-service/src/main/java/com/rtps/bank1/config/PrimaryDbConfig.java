package com.rtps.bank1.config;

import java.util.HashMap;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import com.zaxxer.hikari.HikariDataSource;

@Configuration
@EnableJpaRepositories(basePackages = "com.rtps.bank1.repository", entityManagerFactoryRef = "entityManagerFactory", transactionManagerRef = "transactionManager")
public class PrimaryDbConfig {

	@Primary
	@Bean(name = "dataSource")
	public DataSource dataSource(Environment env) {
		String url = env.getProperty("spring.datasource.url");
		String username = env.getProperty("spring.datasource.username");
		String password = env.getProperty("spring.datasource.password");
		String driver = env.getProperty("spring.datasource.driver-class-name");

		System.out.println("=============== DEBUG PRIMARY URL ===============");
		System.out.println("Active Profiles: " + String.join(",", env.getActiveProfiles()));
		System.out.println("Default Profiles: " + String.join(",", env.getDefaultProfiles()));
		System.out.println("Resolved URL: " + url);
		System.out.println("Resolved Username: " + username);
		System.out.println("=================================================");

		if (url == null || url.trim().isEmpty() || url.contains("${")) {
			// Fallback for Eclipse Clean empty states to prevent crash
			System.out
					.println("WARN: URL is null, empty, or unresolved. Using dummy H2 database to prevent hard crash.");
			HikariDataSource ds = new HikariDataSource();
			ds.setJdbcUrl("jdbc:h2:mem:dummy;DB_CLOSE_DELAY=-1");
			ds.setDriverClassName("org.h2.Driver");
			ds.setUsername("sa");
			ds.setPassword("");
			return ds;
		}

		return DataSourceBuilder.create().url(url).username(username).password(password).driverClassName(driver)
				.build();
	}

	@Primary
	@Bean(name = "entityManagerFactory")
	public LocalContainerEntityManagerFactoryBean entityManagerFactory(@Qualifier("dataSource") DataSource dataSource) {
		LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
		em.setDataSource(dataSource);
		em.setPackagesToScan("com.rtps.bank1.entity");
		em.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

		HashMap<String, Object> properties = new HashMap<>();
		properties.put("hibernate.hbm2ddl.auto", "validate");
		properties.put("hibernate.dialect", "org.hibernate.dialect.OracleDialect");
		properties.put("hibernate.physical_naming_strategy",
				"org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
		em.setJpaPropertyMap(properties);

		return em;
	}

	@Primary
	@Bean(name = "transactionManager")
	public PlatformTransactionManager transactionManager(
			@Qualifier("entityManagerFactory") LocalContainerEntityManagerFactoryBean entityManagerFactory) {
		return new JpaTransactionManager(entityManagerFactory.getObject());
	}
}
