package com.portfoliomanager.e2e;

import com.portfoliomanager.pricing.PriceResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets end-to-end tests script the stub providers. It exists only under the {@code e2e} profile and
 * must never be enabled anywhere else.
 */
@RestController
@Profile("e2e")
@RequestMapping("/api/e2e")
public class E2eController {

    /** One scripted answer: either a price (with optional previous close and date) or an error. */
    public record StubPrice(
            BigDecimal price, BigDecimal prevPrice, LocalDate priceDate, String error) {}

    /** A provider's script; {@code unreachable} makes every fetch fail. */
    public record ProviderScript(Boolean unreachable, Map<String, StubPrice> prices) {}

    public record Script(ProviderScript yahoo, ProviderScript coingecko) {}

    private final StubPriceProvider yahoo;
    private final StubPriceProvider coingecko;
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    E2eController(
            StubPriceProvider yahooStub,
            StubPriceProvider coingeckoStub,
            JdbcTemplate jdbc,
            DataSource dataSource) {
        this.yahoo = yahooStub;
        this.coingecko = coingeckoStub;
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    /** Replaces the script of each provider that is present in the body. */
    @PutMapping("/prices")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void script(@RequestBody Script script) {
        apply(yahoo, script.yahoo());
        apply(coingecko, script.coingecko());
    }

    /**
     * Clears the price scripts and deletes all data. It refuses to touch any database that is not
     * the dedicated end-to-end one, so a misconfigured run cannot wipe real data.
     */
    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reset() throws java.sql.SQLException {
        try (var connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            if (!url.endsWith("/portfolio_e2e")) {
                throw new IllegalStateException(
                        "Refusing to reset a database that is not portfolio_e2e");
            }
        }
        jdbc.execute(
                "TRUNCATE snapshot_holding, snapshot, \"transaction\", price, refresh_run,"
                        + " target_allocation, import_row, import_batch, instrument, account CASCADE");
        yahoo.configure(false, Map.of());
        coingecko.configure(false, Map.of());
    }

    private static void apply(StubPriceProvider provider, ProviderScript script) {
        if (script == null) {
            return;
        }
        Map<String, PriceResult> answers = new LinkedHashMap<>();
        if (script.prices() != null) {
            script.prices()
                    .forEach(
                            (id, stub) ->
                                    answers.put(
                                            id,
                                            stub.error() != null
                                                    ? PriceResult.failure(stub.error())
                                                    : PriceResult.ok(
                                                            stub.price(),
                                                            stub.prevPrice(),
                                                            stub.priceDate())));
        }
        provider.configure(Boolean.TRUE.equals(script.unreachable()), answers);
    }
}
