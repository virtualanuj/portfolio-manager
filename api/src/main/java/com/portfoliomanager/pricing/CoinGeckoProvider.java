package com.portfoliomanager.pricing;

import java.io.IOException;
import java.math.BigDecimal;
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
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Prices crypto from CoinGecko's simple-price endpoint with one batched call for every coin.
 * CoinGecko gives a 24-hour change rather than a previous close, so the previous price is derived
 * from it.
 */
@Component
@Profile("!e2e")
public class CoinGeckoProvider implements PriceProvider {

    private static final int PRICE_SCALE = 8;
    private static final int ATTEMPTS = 2;

    private final PricingProperties properties;
    private final HttpClient client;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public CoinGeckoProvider(PricingProperties properties) {
        this.properties = properties;
        this.client = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
    }

    @Override
    public PriceSource source() {
        return PriceSource.COINGECKO;
    }

    @Override
    public Map<String, PriceResult> fetch(Collection<String> coinIds) {
        Map<String, PriceResult> results = new LinkedHashMap<>();
        if (coinIds.isEmpty()) {
            return results;
        }
        String body;
        try {
            body = requestWithRetry(coinIds);
        } catch (FetchFailure failure) {
            PriceResult error = PriceResult.failure(failure.getMessage());
            coinIds.forEach(id -> results.put(id, error));
            return results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            PriceResult error = PriceResult.failure("Interrupted while fetching CoinGecko prices");
            coinIds.forEach(id -> results.put(id, error));
            return results;
        }

        JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (RuntimeException e) {
            PriceResult error = PriceResult.failure("CoinGecko returned an unreadable response");
            coinIds.forEach(id -> results.put(id, error));
            return results;
        }
        for (String id : coinIds) {
            results.put(id, parse(id, root.path(id)));
        }
        return results;
    }

    private static final class FetchFailure extends Exception {
        FetchFailure(String message) {
            super(message);
        }
    }

    private String requestWithRetry(Collection<String> coinIds)
            throws FetchFailure, InterruptedException {
        String ids =
                coinIds.stream()
                        .map(id -> URLEncoder.encode(id, StandardCharsets.UTF_8))
                        .collect(Collectors.joining(","));
        URI uri =
                URI.create(
                        properties.coingeckoBaseUrl()
                                + "/api/v3/simple/price?ids="
                                + ids
                                + "&vs_currencies=usd&include_24hr_change=true"
                                + "&include_last_updated_at=true");
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(uri)
                        .timeout(properties.requestTimeout())
                        .header("Accept", "application/json")
                        .GET();
        String apiKey = properties.coingeckoApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("x-cg-demo-api-key", apiKey);
        }
        HttpRequest request = builder.build();

        FetchFailure last = new FetchFailure("No attempt was made");
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status >= 500) {
                    last = new FetchFailure("CoinGecko returned HTTP " + status);
                } else if (status >= 400) {
                    throw new FetchFailure("CoinGecko returned HTTP " + status);
                } else {
                    return response.body();
                }
            } catch (IOException e) {
                last =
                        new FetchFailure(
                                "CoinGecko request failed: "
                                        + (e.getMessage() == null
                                                ? e.getClass().getSimpleName()
                                                : e.getMessage()));
            }
            if (attempt < ATTEMPTS) {
                Thread.sleep(properties.retryBackoff());
            }
        }
        throw last;
    }

    private static PriceResult parse(String id, JsonNode coin) {
        JsonNode usd = coin.path("usd");
        if (!usd.isNumber()) {
            return PriceResult.failure("CoinGecko has no price for " + id);
        }
        BigDecimal price = usd.decimalValue();
        JsonNode change = coin.path("usd_24h_change");
        BigDecimal previous = null;
        if (change.isNumber()) {
            BigDecimal divisor = BigDecimal.ONE.add(change.decimalValue().movePointLeft(2));
            if (divisor.signum() != 0) {
                previous = price.divide(divisor, PRICE_SCALE, RoundingMode.HALF_UP);
            }
        }
        JsonNode updatedAt = coin.path("last_updated_at");
        LocalDate date =
                updatedAt.isNumber()
                        ? Instant.ofEpochSecond(updatedAt.asLong())
                                .atOffset(ZoneOffset.UTC)
                                .toLocalDate()
                        : LocalDate.now(ZoneOffset.UTC);
        return PriceResult.ok(price, previous, date);
    }
}
