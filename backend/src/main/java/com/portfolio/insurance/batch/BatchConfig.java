package com.portfolio.insurance.batch;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Batch infrastructure comes from Spring Boot: JDBC job repository on the application's Oracle schema
 * (tables created by Flyway V10, not by Boot) and a synchronous JobOperator used by tests and the scheduler.
 * Jobs are defined in the {@code batch.*} sub-packages.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BatchProperties.class)
class BatchConfig {
}
