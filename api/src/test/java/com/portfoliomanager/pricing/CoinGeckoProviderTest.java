package com.portfoliomanager.pricing;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CoinGeckoProviderTest {

    private static final String PATH = "/api/v3/simple/price";

    private WireMockServer server;

    @BeforeEach
    void startServer() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    private static String fixture() throws IOException {
        try (var in =
                CoinGeckoProviderTest.class.getResourceAsStream("/fixtures/coingecko-two.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private CoinGeckoProvider provider(String apiKey) {
        return new CoinGeckoProvider(
                new PricingProperties(
                        null,
                        server.baseUrl(),
                        apiKey,
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(5),
                        Duration.ofMillis(20),
                        5));
    }

    private void stubBatch() throws IOException {
        server.stubFor(
                get(urlPathEqualTo(PATH))
                        .willReturn(aResponse().withStatus(200).withBody(fixture())));
    }

    @Test
    void oneBatchedCallCoversManyCoins() throws Exception {
        stubBatch();

        Map<String, PriceResult> results = provider(null).fetch(List.of("bitcoin", "ethereum"));

        server.verify(
                1,
                getRequestedFor(urlPathEqualTo(PATH))
                        .withQueryParam("ids", equalTo("bitcoin,ethereum"))
                        .withQueryParam("vs_currencies", equalTo("usd"))
                        .withQueryParam("include_24hr_change", equalTo("true"))
                        .withQueryParam("include_last_updated_at", equalTo("true")));
        assertThat(results.get("bitcoin").isOk()).isTrue();
        assertThat(results.get("bitcoin").price()).isEqualByComparingTo("84122");
        assertThat(results.get("ethereum").price()).isEqualByComparingTo("2687.66");
    }

    @Test
    void previousPriceIsDerivedFromThe24HourChange() throws Exception {
        stubBatch();

        PriceResult bitcoin = provider(null).fetch(List.of("bitcoin")).get("bitcoin");

        // 84122 / (1 + (-0.666626775404369 / 100)), rounded to 8 places
        assertThat(bitcoin.prevPrice()).isEqualByComparingTo("84686.54317195");
        assertThat(bitcoin.prevPrice().scale()).isEqualTo(8);
    }

    @Test
    void priceDateIsTheUtcDateOfTheLastUpdate() throws Exception {
        stubBatch();

        PriceResult bitcoin = provider(null).fetch(List.of("bitcoin")).get("bitcoin");

        // last_updated_at 1790418490 = 2026-09-26T10:28:10Z
        assertThat(bitcoin.priceDate()).isEqualTo(LocalDate.parse("2026-09-26"));
    }

    @Test
    void missingCoinGetsItsOwnError() throws Exception {
        stubBatch();

        Map<String, PriceResult> results = provider(null).fetch(List.of("bitcoin", "no-such-coin"));

        assertThat(results.get("bitcoin").isOk()).isTrue();
        assertThat(results.get("no-such-coin").isOk()).isFalse();
        assertThat(results.get("no-such-coin").error()).contains("no-such-coin");
    }

    @Test
    void coinWithoutA24HourChangeHasNoPreviousPrice() {
        server.stubFor(
                get(urlPathEqualTo(PATH))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withBody(
                                                "{\"bitcoin\":{\"usd\":100,\"last_updated_at\":1790418490}}")));

        PriceResult bitcoin = provider(null).fetch(List.of("bitcoin")).get("bitcoin");

        assertThat(bitcoin.isOk()).isTrue();
        assertThat(bitcoin.prevPrice()).isNull();
    }

    @Test
    void sendsTheDemoApiKeyOnlyWhenConfigured() throws Exception {
        stubBatch();

        provider("cg-demo-123").fetch(List.of("bitcoin"));
        server.verify(
                getRequestedFor(urlPathEqualTo(PATH))
                        .withHeader("x-cg-demo-api-key", equalTo("cg-demo-123")));

        server.resetRequests();
        provider("").fetch(List.of("bitcoin"));
        server.verify(
                getRequestedFor(urlPathEqualTo(PATH)).withHeader("x-cg-demo-api-key", absent()));
    }

    @Test
    void rateLimitingFailsEveryCoinWithoutThrowing() {
        server.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(429)));

        Map<String, PriceResult> results = provider(null).fetch(List.of("bitcoin", "ethereum"));

        assertThat(results).hasSize(2);
        assertThat(results.values()).noneMatch(PriceResult::isOk);
        assertThat(results.get("bitcoin").error()).contains("429");
        server.verify(1, getRequestedFor(urlPathEqualTo(PATH)));
    }

    @Test
    void serverErrorIsRetriedOnceThenReportedForEveryCoin() {
        server.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(503)));

        Map<String, PriceResult> results = provider(null).fetch(List.of("bitcoin", "ethereum"));

        assertThat(results.values()).noneMatch(PriceResult::isOk);
        server.verify(2, getRequestedFor(urlPathEqualTo(PATH)));
    }

    @Test
    void unreachableServerFailsEveryCoinWithoutThrowing() {
        CoinGeckoProvider provider = provider(null);
        server.stop();

        Map<String, PriceResult> results = provider.fetch(List.of("bitcoin"));

        assertThat(results.get("bitcoin").isOk()).isFalse();
    }

    @Test
    void malformedBodyFailsEveryCoin() {
        server.stubFor(
                get(urlPathEqualTo(PATH))
                        .willReturn(aResponse().withStatus(200).withBody("not json")));

        assertThat(provider(null).fetch(List.of("bitcoin")).get("bitcoin").isOk()).isFalse();
    }

    @Test
    void noCoinsMeansNoCall() {
        Map<String, PriceResult> results = provider(null).fetch(List.of());

        assertThat(results).isEmpty();
        assertThat(server.getAllServeEvents()).isEmpty();
    }
}
