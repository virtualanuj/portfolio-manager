package com.portfoliomanager.e2e;

import com.portfoliomanager.pricing.PriceSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Replaces the real price providers with stubs under the {@code e2e} profile. */
@Configuration
@Profile("e2e")
class E2eConfig {

    @Bean
    StubPriceProvider yahooStub() {
        return new StubPriceProvider(PriceSource.YAHOO);
    }

    @Bean
    StubPriceProvider coingeckoStub() {
        return new StubPriceProvider(PriceSource.COINGECKO);
    }
}
