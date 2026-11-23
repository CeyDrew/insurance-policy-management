package com.portfolio.insurance;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.ZoneId;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    OracleContainer oracleContainer() {
        // Same time zone as the compose database: server-side timestamps (SYSTIMESTAMP) must match the JVM.
        return new OracleContainer(DockerImageName.parse("gvenzl/oracle-free:23-slim-faststart"))
                .withEnv("TZ", ZoneId.systemDefault().getId())
                .withReuse(true);
    }
}
