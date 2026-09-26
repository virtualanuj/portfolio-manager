package com.portfoliomanager.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.portfoliomanager.AbstractIntegrationTest;
import com.portfoliomanager.ApiApplication;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Boots the real application under the {@code local} profile to prove, beyond the unit test, that
 * it cannot be started bound to anything but loopback (NFR-SEC-2).
 */
class LocalProfileBindingIT {

    @BeforeAll
    static void startSharedDatabase() {
        // Loading the base class starts the shared Testcontainers Postgres.
        AbstractIntegrationTest.datasourceProperties();
    }

    private static ConfigurableApplicationContext boot(String... extra) {
        // Command-line arguments outrank application-local.yml, which sets its own port and
        // address.
        List<String> arguments = new ArrayList<>();
        for (String property : AbstractIntegrationTest.datasourceProperties()) {
            arguments.add("--" + property);
        }
        arguments.add("--server.port=0");
        for (String property : extra) {
            arguments.add("--" + property);
        }
        return new SpringApplicationBuilder(ApiApplication.class)
                .web(WebApplicationType.SERVLET)
                .profiles("local")
                .run(arguments.toArray(String[]::new));
    }

    @Test
    void refusesToStartBoundToAllInterfaces() {
        assertThatThrownBy(() -> boot("server.address=0.0.0.0"))
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("loopback");
    }

    @Test
    void startsWhenBoundToLoopback() {
        try (ConfigurableApplicationContext context = boot("server.address=127.0.0.1")) {
            assertThat(context.isRunning()).isTrue();
        }
    }
}
