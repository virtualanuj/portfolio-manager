package com.portfoliomanager.pricing;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Prices stocks, ETFs and funds from Yahoo Finance's unofficial chart API, one call per ticker, run
 * on virtual threads with a cap on requests in flight. The API is unofficial and can change without
 * notice; there is deliberately no fallback feed.
 */
@Component
@Profile("!e2e")
public class YahooProvider implements PriceProvider {

    private static final Logger log = LoggerFactory.getLogger(YahooProvider.class);

    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko)"
                    + " Chrome/124.0 Safari/537.36";

    /** Yahoo's closes carry single-precision noise (378.0799865...); 7 digits removes it. */
    private static final MathContext PRICE_PRECISION = new MathContext(7, RoundingMode.HALF_UP);

    private static final int ATTEMPTS = 2;

    private final PricingProperties properties;
    private final HttpClient client;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public YahooProvider(PricingProperties properties) {
        this.properties = properties;
        this.client = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
    }

    @Override
    public PriceSource source() {
        return PriceSource.YAHOO;
    }

    @Override
    public Map<String, PriceResult> fetch(Collection<String> tickers) {
        Semaphore inFlight = new Semaphore(properties.maxConcurrentRequests());
        Map<String, Future<PriceResult>> pending = new LinkedHashMap<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (String ticker : tickers) {
                pending.put(ticker, executor.submit(() -> fetchLimited(ticker, inFlight)));
            }
        }
        Map<String, PriceResult> results = new LinkedHashMap<>();
        pending.forEach((ticker, future) -> results.put(ticker, resultOf(ticker, future)));
        return results;
    }

    private PriceResult resultOf(String ticker, Future<PriceResult> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return PriceResult.failure("Interrupted while fetching " + ticker);
        } catch (ExecutionException e) {
            log.warn("Unexpected failure fetching {} from Yahoo", ticker, e.getCause());
            return PriceResult.failure("Unexpected error: " + e.getCause().getMessage());
        }
    }

    private PriceResult fetchLimited(String ticker, Semaphore inFlight)
            throws InterruptedException {
        inFlight.acquire();
        try {
            return fetchWithRetry(ticker);
        } finally {
            inFlight.release();
        }
    }

    private PriceResult fetchWithRetry(String ticker) throws InterruptedException {
        PriceResult last = PriceResult.failure("No attempt was made");
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            Attempt outcome = attemptOnce(ticker);
            if (!outcome.retryable() || attempt == ATTEMPTS) {
                return outcome.result();
            }
            last = outcome.result();
            Thread.sleep(properties.retryBackoff());
        }
        return last;
    }

    private record Attempt(PriceResult result, boolean retryable) {}

    private Attempt attemptOnce(String ticker) throws InterruptedException {
        URI uri =
                URI.create(
                        properties.yahooBaseUrl()
                                + "/v8/finance/chart/"
                                + URLEncoder.encode(ticker, StandardCharsets.UTF_8)
                                + "?range=5d&interval=1d");
        HttpRequest request =
                HttpRequest.newBuilder(uri)
                        .timeout(properties.requestTimeout())
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json")
                        .GET()
                        .build();
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            return new Attempt(PriceResult.failure("Yahoo request failed: " + describe(e)), true);
        }

        int status = response.statusCode();
        if (status >= 500) {
            return new Attempt(PriceResult.failure("Yahoo returned HTTP " + status), true);
        }
        if (status >= 400) {
            return new Attempt(PriceResult.failure(errorMessage(response.body(), status)), false);
        }
        return new Attempt(parse(response.body()), false);
    }

    private static String describe(IOException e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private String errorMessage(String body, int status) {
        try {
            JsonNode description =
                    mapper.readTree(body).path("chart").path("error").path("description");
            if (description.isString()) {
                return description.asString();
            }
        } catch (RuntimeException ignored) {
            // The body was not JSON; fall through to the status code.
        }
        return "Yahoo returned HTTP " + status;
    }

    private PriceResult parse(String body) {
        JsonNode chart;
        try {
            chart = mapper.readTree(body).path("chart");
        } catch (RuntimeException e) {
            return PriceResult.failure("Yahoo returned an unreadable response");
        }
        JsonNode error = chart.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            return PriceResult.failure(
                    error.path("description").asString("Yahoo reported an error"));
        }
        JsonNode result = chart.path("result").path(0);
        JsonNode timestamps = result.path("timestamp");
        JsonNode closes = result.path("indicators").path("quote").path(0).path("close");
        if (!timestamps.isArray() || !closes.isArray()) {
            return PriceResult.failure("Yahoo returned no price history");
        }

        Long lastTimestamp = null;
        BigDecimal last = null;
        BigDecimal previous = null;
        for (int i = 0; i < Math.min(timestamps.size(), closes.size()); i++) {
            JsonNode close = closes.get(i);
            if (close == null || !close.isNumber()) {
                continue;
            }
            previous = last;
            last = BigDecimal.valueOf(close.asDouble()).round(PRICE_PRECISION);
            lastTimestamp = timestamps.get(i).asLong();
        }
        if (last == null) {
            return PriceResult.failure("Yahoo returned no closing prices");
        }
        int gmtOffsetSeconds = result.path("meta").path("gmtoffset").asInt(0);
        LocalDate date =
                Instant.ofEpochSecond(lastTimestamp)
                        .atOffset(ZoneOffset.ofTotalSeconds(gmtOffsetSeconds))
                        .toLocalDate();
        return PriceResult.ok(last, previous, date);
    }
}
