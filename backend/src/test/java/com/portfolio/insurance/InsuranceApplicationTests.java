package com.portfolio.insurance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class InsuranceApplicationTests {

    @Autowired
    private HealthEndpoint healthEndpoint;

    @Test
    void contextLoadsAndHealthIsUp() {
        assertThat(healthEndpoint.health().getStatus()).isEqualTo(Status.UP);
    }
}
