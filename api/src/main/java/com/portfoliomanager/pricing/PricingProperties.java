package com.portfoliomanager.pricing;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings under {@code app.pricing.*}; a blank CoinGecko key means the free unauthenticated tier.
 */
@ConfigurationProperties("app.pricing")
public record PricingProperties(
        @DefaultValue("https://query1.finance.yahoo.com") String yahooBaseUrl,
        @DefaultValue("https://api.coingecko.com") String coingeckoBaseUrl,
        String coingeckoApiKey,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("10s") Duration requestTimeout,
        @DefaultValue("500ms") Duration retryBackoff,
        @DefaultValue("5") int maxConcurrentRequests) {}
