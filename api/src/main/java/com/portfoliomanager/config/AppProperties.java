package com.portfoliomanager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Application settings under {@code app.*}. */
@ConfigurationProperties("app")
public record AppProperties(String timezone) {}
