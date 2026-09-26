package com.portfoliomanager.pricing;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class YahooProviderTest {

    private static final String CHART = "/v8/finance/chart/";

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

    private static String fixture(String name) throws IOException {
        try (var in = YahooProviderTest.class.getResourceAsStream("/fixtures/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private YahooProvider provider(Duration requestTimeout) {
        return new YahooProvider(
                new PricingProperties(
                        server.baseUrl(),
                        null,
                        null,
                        Duration.ofSeconds(2),
                        requestTimeout,
                        Duration.ofMillis(20),
                        5));
    }

    private YahooProvider provider() {
        return provider(Duration.ofSeconds(5));
    }

    private void stubOk(String ticker) throws IOException {
        server.stubFor(
                get(urlPathEqualTo(CHART + ticker))
                        .willReturn(
                                aResponse().withStatus(200).withBody(fixture("yahoo-vti.json"))));
    }

    @Test
    void parsesLastCloseAndPriorCloseAndTheTradingDate() throws Exception {
        server.stubFor(
                get(urlPathEqualTo(CHART + "VTI"))
                        .withQueryParam("range", equalTo("5d"))
                        .withQueryParam("interval", equalTo("1d"))
                        .willReturn(
                                aResponse().withStatus(200).withBody(fixture("yahoo-vti.json"))));

        PriceResult result = provider().fetch(List.of("VTI")).get("VTI");

        assertThat(result.isOk()).isTrue();
        assertThat(result.price()).isEqualByComparingTo("379.77");
        assertThat(result.prevPrice()).isEqualByComparingTo("378.08");
        assertThat(result.priceDate()).isEqualTo(LocalDate.parse("2026-09-25"));
    }

    @Test
    void unknownTickerReportsTheProvidersMessage() throws Exception {
        server.stubFor(
                get(urlPathEqualTo(CHART + "NOSUCH"))
                        .willReturn(
                                aResponse()
                                        .withStatus(404)
                                        .withBody(fixture("yahoo-unknown.json"))));

        PriceResult result = provider().fetch(List.of("NOSUCH")).get("NOSUCH");

        assertThat(result.isOk()).isFalse();
        assertThat(result.error()).contains("No data found");
        server.verify(1, getRequestedFor(urlPathEqualTo(CHART + "NOSUCH")));
    }

    @Test
    void errorPayloadWithStatus200IsStillAnError() throws Exception {
        server.stubFor(
                get(urlPathEqualTo(CHART + "NOSUCH"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withBody(fixture("yahoo-unknown.json"))));

        assertThat(provider().fetch(List.of("NOSUCH")).get("NOSUCH").error())
                .contains("No data found");
    }

    @Test
    void serverErrorIsRetriedOnceAndThenSucceeds() throws Exception {
        server.stubFor(
                get(urlPathEqualTo(CHART + "VTI"))
                        .inScenario("flaky")
                        .whenScenarioStateIs(Scenario.STARTED)
                        .willReturn(aResponse().withStatus(500))
                        .willSetStateTo("recovered"));
        server.stubFor(
                get(urlPathEqualTo(CHART + "VTI"))
                        .inScenario("flaky")
                        .whenScenarioStateIs("recovered")
                        .willReturn(
                                aResponse().withStatus(200).withBody(fixture("yahoo-vti.json"))));

        PriceResult result = provider().fetch(List.of("VTI")).get("VTI");

        assertThat(result.isOk()).isTrue();
        server.verify(2, getRequestedFor(urlPathEqualTo(CHART + "VTI")));
    }

    @Test
    void persistentServerErrorIsRetriedOnceThenReported() {
        server.stubFor(get(urlPathEqualTo(CHART + "VTI")).willReturn(aResponse().withStatus(500)));

        PriceResult result = provider().fetch(List.of("VTI")).get("VTI");

        assertThat(result.isOk()).isFalse();
        assertThat(result.error()).contains("500");
        server.verify(2, getRequestedFor(urlPathEqualTo(CHART + "VTI")));
    }

    @Test
    void clientErrorIsNotRetried() {
        server.stubFor(get(urlPathEqualTo(CHART + "VTI")).willReturn(aResponse().withStatus(429)));

        PriceResult result = provider().fetch(List.of("VTI")).get("VTI");

        assertThat(result.isOk()).isFalse();
        assertThat(result.error()).contains("429");
        server.verify(1, getRequestedFor(urlPathEqualTo(CHART + "VTI")));
    }

    @Test
    void readTimeoutBecomesAnErrorResultNotAnException() throws Exception {
        server.stubFor(
                get(urlPathEqualTo(CHART + "VTI"))
                        .willReturn(
                                aResponse()
                                        .withStatus(200)
                                        .withBody(fixture("yahoo-vti.json"))
                                        .withFixedDelay(1500)));

        PriceResult result = provider(Duration.ofMillis(300)).fetch(List.of("VTI")).get("VTI");

        assertThat(result.isOk()).isFalse();
        assertThat(result.error()).isNotBlank();
    }

    @Test
    void malformedBodyBecomesAnError() {
        server.stubFor(
                get(urlPathEqualTo(CHART + "VTI"))
                        .willReturn(aResponse().withStatus(200).withBody("<html>nope</html>")));

        assertThat(provider().fetch(List.of("VTI")).get("VTI").isOk()).isFalse();
    }

    @Test
    void sendsABrowserLikeUserAgent() throws Exception {
        stubOk("VTI");

        provider().fetch(List.of("VTI"));

        server.verify(
                getRequestedFor(urlPathEqualTo(CHART + "VTI"))
                        .withHeader("User-Agent", matching("Mozilla/5\\.0.*")));
    }

    @Test
    void neverHasMoreThanFiveRequestsInFlight() throws Exception {
        int delayMillis = 300;
        List<String> tickers = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String ticker = "T" + i;
            tickers.add(ticker);
            server.stubFor(
                    get(urlPathEqualTo(CHART + ticker))
                            .willReturn(
                                    aResponse()
                                            .withStatus(200)
                                            .withBody(fixture("yahoo-vti.json"))
                                            .withFixedDelay(delayMillis)));
        }

        Map<String, PriceResult> results = provider().fetch(tickers);

        assertThat(results).hasSize(12);
        assertThat(results.values()).allMatch(PriceResult::isOk);
        assertThat(
                        maxOverlap(
                                server.getAllServeEvents().stream()
                                        .map(e -> e.getRequest())
                                        .toList(),
                                delayMillis))
                .isLessThanOrEqualTo(5);
    }

    /** Peak number of requests whose fixed-delay windows overlap, from their arrival times. */
    private static int maxOverlap(List<LoggedRequest> requests, int delayMillis) {
        long[] starts =
                requests.stream().mapToLong(r -> r.getLoggedDate().getTime()).sorted().toArray();
        int peak = 0;
        for (long start : starts) {
            int concurrent = 0;
            for (long other : starts) {
                if (other <= start && other + delayMillis > start) {
                    concurrent++;
                }
            }
            peak = Math.max(peak, concurrent);
        }
        return peak;
    }

    @Test
    void reportsEveryRequestedTickerEvenWhenSomeFail() throws Exception {
        stubOk("VTI");
        server.stubFor(get(urlPathEqualTo(CHART + "BAD")).willReturn(aResponse().withStatus(404)));

        Map<String, PriceResult> results = provider().fetch(List.of("VTI", "BAD"));

        assertThat(results.keySet()).containsExactlyInAnyOrder("VTI", "BAD");
        assertThat(results.get("VTI").isOk()).isTrue();
        assertThat(results.get("BAD").isOk()).isFalse();
    }
}
