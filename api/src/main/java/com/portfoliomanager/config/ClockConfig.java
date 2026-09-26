package com.portfoliomanager.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The application clock, in {@code app.timezone}; tests replace it with a fixed clock. */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class ClockConfig {

    @Bean
    Clock clock(AppProperties properties) {
        return Clock.system(ZoneId.of(properties.timezone()));
    }
}
