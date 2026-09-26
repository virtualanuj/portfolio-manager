package com.portfoliomanager.importing;

import static org.assertj.core.api.Assertions.assertThat;

import com.portfoliomanager.domain.AssetType;
import com.portfoliomanager.domain.Txn;
import com.portfoliomanager.domain.TxnType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ImportValidatorTest {

    /** In-memory stand-in for what is already in the database. */
    private static final class Existing implements ExistingData {
        final Set<String> accounts = new HashSet<>();
        final Set<String> instruments = new HashSet<>();
        final Map<String, List<Txn>> transactions = new HashMap<>();
        long seq = 1;

        Existing account(String name) {
            accounts.add(name);
            return this;
        }

        Existing instrument(String symbol, AssetType type) {
            instruments.add(symbol + "/" + type);
            return this;
        }

        Existing buy(
                String account,
                String symbol,
                AssetType type,
                String date,
                String quantity,
                String price) {
            transactions
                    .computeIfAbsent(key(account, symbol, type), k -> new ArrayList<>())
                    .add(
                            new Txn(
                                    UUID.randomUUID(),
                                    seq++,
                                    TxnType.BUY,
                                    LocalDate.parse(date),
                                    new BigDecimal(quantity),
                                    new BigDecimal(price),
                                    0,
                                    0));
            return this;
        }

        static String key(String account, String symbol, AssetType type) {
            return account + "|" + symbol + "|" + type;
        }

        @Override
        public boolean accountExists(String name) {
            return accounts.contains(name);
        }

        @Override
        public boolean instrumentExists(String symbol, AssetType assetType) {
            return instruments.contains(symbol + "/" + assetType);
        }

        @Override
        public List<Txn> transactionsOf(String account, String symbol, AssetType assetType) {
            return transactions.getOrDefault(key(account, symbol, assetType), List.of());
        }
    }

    private static final List<String> COLUMNS =
            List.of(
                    "date",
                    "account",
                    "symbol",
                    "asset_type",
                    "type",
                    "quantity",
                    "price",
                    "split_ratio",
                    "source_id",
                    "account_type",
                    "note");

    /** Builds a row from "column=value" pairs; the line number is the given one. */
    private static CsvRow row(int line, String... pairs) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String column : COLUMNS) {
            values.put(column, "");
        }
        for (String pair : pairs) {
            int eq = pair.indexOf('=');
            values.put(pair.substring(0, eq), pair.substring(eq + 1));
        }
        return new CsvRow(line, values);
    }

    private static CsvRow buy(int line, String date, String quantity, String price) {
        return row(
                line,
                "date=" + date,
                "account=Main",
                "symbol=VTI",
                "asset_type=ETF",
                "type=BUY",
                "quantity=" + quantity,
                "price=" + price);
    }

    private static CsvRow sell(int line, String date, String quantity) {
        return row(
                line,
                "date=" + date,
                "account=Main",
                "symbol=VTI",
                "asset_type=ETF",
                "type=SELL",
                "quantity=" + quantity,
                "price=100");
    }

    private static Existing withMainAndVti() {
        return new Existing().account("Main").instrument("VTI", AssetType.ETF);
    }

    private static List<ImportRowResult> validate(Existing existing, CsvRow... rows) {
        return ImportValidator.validate(List.of(rows), existing, false);
    }

    @Test
    void aWellFormedBuyForKnownAccountAndInstrumentIsOk() {
        ImportRowResult result =
                validate(withMainAndVti(), buy(2, "2026-01-05", "10", "100")).get(0);

        assertThat(result.status()).isEqualTo(RowStatus.OK);
        assertThat(result.errors()).isEmpty();
        ParsedRow parsed = result.parsed();
        assertThat(parsed.date()).isEqualTo(LocalDate.parse("2026-01-05"));
        assertThat(parsed.type()).isEqualTo(TxnType.BUY);
        assertThat(parsed.quantity()).isEqualByComparingTo("10");
        assertThat(parsed.unitPrice()).isEqualByComparingTo("100");
        assertThat(parsed.accountName()).isEqualTo("Main");
        assertThat(parsed.assetType()).isEqualTo(AssetType.ETF);
    }

    @Test
    void enumsAreCaseInsensitive() {
        ImportRowResult result =
                validate(
                                withMainAndVti(),
                                row(
                                        2,
                                        "date=2026-01-05",
                                        "account=Main",
                                        "symbol=VTI",
                                        "asset_type=etf",
                                        "type=buy",
                                        "quantity=1",
                                        "price=1"))
                        .get(0);

        assertThat(result.errors()).isEmpty();
        assertThat(result.parsed().type()).isEqualTo(TxnType.BUY);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "date=,date",
        "date=05/01/2026,date",
        "date=2026-13-01,date",
        "account=,account",
        "symbol=,symbol",
        "asset_type=,asset_type",
        "asset_type=BOND,asset_type",
        "type=,type",
        "type=DIVIDEND,type",
        "quantity=,quantity",
        "quantity=abc,quantity",
        "quantity=0,quantity",
        "quantity=-1,quantity",
        "quantity=1.123456789,quantity",
        "price=,price",
        "price=-1,price",
        "price=1e3,price"
    })
    void invalidFieldsAreReportedAgainstTheirColumn(String override, String expectedField) {
        CsvRow row = buy(7, "2026-01-05", "10", "100");
        Map<String, String> values = new LinkedHashMap<>(row.values());
        int eq = override.indexOf('=');
        values.put(override.substring(0, eq), override.substring(eq + 1));

        ImportRowResult result = validate(withMainAndVti(), new CsvRow(7, values)).get(0);

        assertThat(result.status()).isEqualTo(RowStatus.ERROR);
        assertThat(result.errors()).extracting(ImportIssue::field).contains(expectedField);
        assertThat(result.lineNo()).isEqualTo(7);
    }

    @Test
    void zeroPriceIsAllowed() {
        assertThat(validate(withMainAndVti(), buy(2, "2026-01-05", "10", "0")).get(0).errors())
                .isEmpty();
    }

    @Test
    void severalProblemsOnOneRowAreAllReported() {
        ImportRowResult result =
                validate(
                                withMainAndVti(),
                                row(
                                        2,
                                        "date=nope",
                                        "account=Main",
                                        "symbol=VTI",
                                        "asset_type=ETF",
                                        "type=BUY",
                                        "quantity=x",
                                        "price=y"))
                        .get(0);

        assertThat(result.errors())
                .extracting(ImportIssue::field)
                .contains("date", "quantity", "price");
    }

    @ParameterizedTest(name = "ratio {0} valid={1}")
    @CsvSource({
        "2:1,true",
        "1:10,true",
        "3:2,true",
        "0:1,false",
        "2:0,false",
        "a:b,false",
        "2,false",
        "2:1:1,false",
        "-2:1,false",
        "2:,false"
    })
    void splitRatiosMustBePositiveWholeNumbersOverAColon(String ratio, boolean valid) {
        ImportRowResult result =
                validate(
                                withMainAndVti(),
                                row(
                                        2,
                                        "date=2026-01-05",
                                        "account=Main",
                                        "symbol=VTI",
                                        "asset_type=ETF",
                                        "type=SPLIT",
                                        "split_ratio=" + ratio))
                        .get(0);

        if (valid) {
            assertThat(result.errors()).isEmpty();
        } else {
            assertThat(result.errors()).extracting(ImportIssue::field).contains("split_ratio");
        }
    }

    @Test
    void aValidSplitParsesItsRatio() {
        ImportRowResult result =
                validate(
                                withMainAndVti()
                                        .buy(
                                                "Main",
                                                "VTI",
                                                AssetType.ETF,
                                                "2026-01-01",
                                                "10",
                                                "100"),
                                row(
                                        2,
                                        "date=2026-02-01",
                                        "account=Main",
                                        "symbol=VTI",
                                        "asset_type=ETF",
                                        "type=SPLIT",
                                        "split_ratio=2:1"))
                        .get(0);

        assertThat(result.parsed().splitNumerator()).isEqualTo(2);
        assertThat(result.parsed().splitDenominator()).isEqualTo(1);
    }

    @Test
    void aSplitForbidsQuantityAndPriceAndATradeForbidsARatio() {
        ImportRowResult split =
                validate(
                                withMainAndVti(),
                                row(
                                        2,
                                        "date=2026-02-01",
                                        "account=Main",
                                        "symbol=VTI",
                                        "asset_type=ETF",
                                        "type=SPLIT",
                                        "split_ratio=2:1",
                                        "quantity=5",
                                        "price=1"))
                        .get(0);
        ImportRowResult trade =
                validate(
                                withMainAndVti(),
                                row(
                                        2,
                                        "date=2026-02-01",
                                        "account=Main",
                                        "symbol=VTI",
                                        "asset_type=ETF",
                                        "type=BUY",
                                        "quantity=5",
                                        "price=1",
                                        "split_ratio=2:1"))
                        .get(0);

        assertThat(split.errors()).extracting(ImportIssue::field).contains("quantity", "price");
        assertThat(trade.errors()).extracting(ImportIssue::field).contains("split_ratio");
    }

    @Test
    void aNewCryptoInstrumentNeedsASourceId() {
        Existing existing = new Existing().account("Main");
        CsvRow noSource =
                row(
                        2,
                        "date=2026-01-05",
                        "account=Main",
                        "symbol=BTC",
                        "asset_type=CRYPTO",
                        "type=BUY",
                        "quantity=1",
                        "price=1");
        CsvRow withSource =
                row(
                        3,
                        "date=2026-01-05",
                        "account=Main",
                        "symbol=ETH",
                        "asset_type=CRYPTO",
                        "type=BUY",
                        "quantity=1",
                        "price=1",
                        "source_id=ethereum");

        List<ImportRowResult> results = validate(existing, noSource, withSource);

        assertThat(results.get(0).errors()).extracting(ImportIssue::field).contains("source_id");
        assertThat(results.get(1).errors()).isEmpty();
    }

    @Test
    void anExistingCryptoInstrumentDoesNotNeedASourceId() {
        Existing existing = new Existing().account("Main").instrument("BTC", AssetType.CRYPTO);
        CsvRow row =
                row(
                        2,
                        "date=2026-01-05",
                        "account=Main",
                        "symbol=BTC",
                        "asset_type=CRYPTO",
                        "type=BUY",
                        "quantity=1",
                        "price=1");

        assertThat(validate(existing, row).get(0).errors()).isEmpty();
    }

    @Test
    void newAccountsAndInstrumentsAreMarkedWillCreate() {
        ImportRowResult result = validate(new Existing(), buy(2, "2026-01-05", "10", "100")).get(0);

        assertThat(result.status()).isEqualTo(RowStatus.WILL_CREATE);
        assertThat(result.createsAccount()).isTrue();
        assertThat(result.createsInstrument()).isTrue();
    }

    @Test
    void accountTypeDefaultsToOtherAndMustBeValid() {
        ImportRowResult defaulted = validate(new Existing(), buy(2, "2026-01-05", "1", "1")).get(0);
        ImportRowResult invalid =
                validate(
                                new Existing(),
                                row(
                                        2,
                                        "date=2026-01-05",
                                        "account=Main",
                                        "symbol=VTI",
                                        "asset_type=ETF",
                                        "type=BUY",
                                        "quantity=1",
                                        "price=1",
                                        "account_type=SAVINGS"))
                        .get(0);
        ImportRowResult valid =
                validate(
                                new Existing(),
                                row(
                                        2,
                                        "date=2026-01-05",
                                        "account=Main",
                                        "symbol=VTI",
                                        "asset_type=ETF",
                                        "type=BUY",
                                        "quantity=1",
                                        "price=1",
                                        "account_type=retirement"))
                        .get(0);

        assertThat(defaulted.parsed().accountType())
                .isEqualTo(com.portfoliomanager.domain.AccountType.OTHER);
        assertThat(invalid.errors()).extracting(ImportIssue::field).contains("account_type");
        assertThat(valid.parsed().accountType())
                .isEqualTo(com.portfoliomanager.domain.AccountType.RETIREMENT);
    }

    @Test
    void aRowIdenticalToAnEarlierRowInTheFileIsAWarning() {
        List<ImportRowResult> results =
                validate(
                        withMainAndVti(),
                        buy(2, "2026-01-05", "10", "100"),
                        buy(3, "2026-01-05", "10.0", "100.00"));

        assertThat(results.get(0).status()).isEqualTo(RowStatus.OK);
        assertThat(results.get(1).status()).isEqualTo(RowStatus.WARNING);
        assertThat(results.get(1).isDuplicate()).isTrue();
        assertThat(results.get(1).warnings().get(0).message()).contains("line 2");
    }

    @Test
    void aRowIdenticalToAnExistingTransactionIsAWarning() {
        Existing existing =
                withMainAndVti().buy("Main", "VTI", AssetType.ETF, "2026-01-05", "10", "100");

        ImportRowResult result = validate(existing, buy(2, "2026-01-05", "10", "100")).get(0);

        assertThat(result.status()).isEqualTo(RowStatus.WARNING);
        assertThat(result.isDuplicate()).isTrue();
        assertThat(result.warnings().get(0).message()).contains("already exists");
    }

    @Test
    void rowsThatDifferInAnyFieldAreNotDuplicates() {
        List<ImportRowResult> results =
                validate(
                        withMainAndVti(),
                        buy(2, "2026-01-05", "10", "100"),
                        buy(3, "2026-01-05", "10", "101"),
                        buy(4, "2026-01-06", "10", "100"));

        assertThat(results).allMatch(r -> r.warnings().isEmpty());
    }

    @Test
    void anOversellAcrossFileRowsIsAnErrorOnTheOffendingLine() {
        List<ImportRowResult> results =
                validate(
                        withMainAndVti(),
                        buy(2, "2026-01-05", "10", "100"),
                        sell(3, "2026-01-06", "4"),
                        sell(4, "2026-01-07", "7"));

        assertThat(results.get(0).errors()).isEmpty();
        assertThat(results.get(1).errors()).isEmpty();
        assertThat(results.get(2).status()).isEqualTo(RowStatus.ERROR);
        assertThat(results.get(2).errors().get(0).message()).contains("exceeds");
    }

    @Test
    void anOversellAgainstExistingHistoryIsAnErrorOnThatLine() {
        Existing existing =
                withMainAndVti().buy("Main", "VTI", AssetType.ETF, "2026-01-05", "10", "100");

        List<ImportRowResult> results =
                validate(existing, sell(2, "2026-01-06", "5"), sell(3, "2026-01-07", "6"));

        assertThat(results.get(0).errors()).isEmpty();
        assertThat(results.get(1).status()).isEqualTo(RowStatus.ERROR);
    }

    @Test
    void aSellBeforeItsBuyOnTheSameDateIsAnErrorBecauseFileOrderIsKept() {
        List<ImportRowResult> results =
                validate(
                        withMainAndVti(),
                        sell(2, "2026-01-05", "5"),
                        buy(3, "2026-01-05", "10", "100"));

        assertThat(results.get(0).status()).isEqualTo(RowStatus.ERROR);
        assertThat(results.get(1).errors()).isEmpty();
    }

    @Test
    void anEarlierDatedBuyLaterInTheFileStillCoversALaterDatedSell() {
        List<ImportRowResult> results =
                validate(
                        withMainAndVti(),
                        sell(2, "2026-02-01", "5"),
                        buy(3, "2026-01-01", "10", "100"));

        assertThat(results).allMatch(r -> r.errors().isEmpty());
    }

    @Test
    void positionsAreReplayedIndependently() {
        CsvRow otherAccountSell =
                row(
                        3,
                        "date=2026-01-06",
                        "account=Other",
                        "symbol=VTI",
                        "asset_type=ETF",
                        "type=SELL",
                        "quantity=5",
                        "price=1");
        Existing existing = withMainAndVti().account("Other");

        List<ImportRowResult> results =
                validate(existing, buy(2, "2026-01-05", "10", "100"), otherAccountSell);

        assertThat(results.get(0).errors()).isEmpty();
        assertThat(results.get(1).status()).isEqualTo(RowStatus.ERROR);
    }

    @Test
    void duplicatesAreLeftOutOfTheReplayUnlessTheyAreIncluded() {
        Existing existing =
                withMainAndVti().buy("Main", "VTI", AssetType.ETF, "2026-01-05", "10", "100");
        List<CsvRow> rows = List.of(buy(2, "2026-01-05", "10", "100"), sell(3, "2026-01-06", "15"));

        List<ImportRowResult> skipping = ImportValidator.validate(rows, existing, false);
        List<ImportRowResult> including = ImportValidator.validate(rows, existing, true);

        assertThat(skipping.get(1).status()).isEqualTo(RowStatus.ERROR);
        assertThat(including.get(1).errors()).isEmpty();
    }

    @Test
    void rowsWithFieldErrorsDoNotTakePartInTheReplay() {
        List<ImportRowResult> results =
                validate(
                        withMainAndVti(),
                        buy(2, "2026-01-05", "oops", "100"),
                        sell(3, "2026-01-06", "1"));

        assertThat(results.get(0).errors()).extracting(ImportIssue::field).contains("quantity");
        assertThat(results.get(1).status()).isEqualTo(RowStatus.ERROR);
    }
}
