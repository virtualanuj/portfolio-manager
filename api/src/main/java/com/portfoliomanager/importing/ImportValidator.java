package com.portfoliomanager.importing;

import com.portfoliomanager.domain.AccountType;
import com.portfoliomanager.domain.AssetType;
import com.portfoliomanager.domain.FifoEngine;
import com.portfoliomanager.domain.Txn;
import com.portfoliomanager.domain.TxnType;
import com.portfoliomanager.domain.Violation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates staged CSV rows against the schema (spec section 8) and against stored data. Field
 * rules are checked per row; then each affected position is replayed with the file's rows merged
 * into its existing history, so an oversell is reported on the line that causes it.
 */
public final class ImportValidator {

    private static final Pattern DECIMAL = Pattern.compile("\\d+(\\.\\d{1,8})?");
    private static final Pattern RATIO = Pattern.compile("([1-9]\\d{0,5}):([1-9]\\d{0,5})");
    private static final int MAX_INTEGER_DIGITS = 16;

    /** File rows replay after stored ones on the same date, in file order. */
    private static final long STAGED_SEQ_BASE = 1_000_000_000_000L;

    private ImportValidator() {}

    /**
     * @param includeDuplicates whether rows flagged as duplicates take part in the replay (they do
     *     when the user chooses to import them)
     */
    public static List<ImportRowResult> validate(
            List<CsvRow> rows, ExistingData existing, boolean includeDuplicates) {
        List<ImportRowResult> results = new ArrayList<>();
        Map<String, Integer> firstLineOfRow = new HashMap<>();
        Map<String, Boolean> sourceIdSeen = new HashMap<>();
        for (CsvRow row : rows) {
            results.add(checkRow(row, existing, firstLineOfRow, sourceIdSeen));
        }
        replayPositions(results, existing, includeDuplicates);
        return results;
    }

    private static ImportRowResult checkRow(
            CsvRow row,
            ExistingData existing,
            Map<String, Integer> firstLineOfRow,
            Map<String, Boolean> sourceIdSeen) {
        List<ImportIssue> errors = new ArrayList<>();

        LocalDate date = parseDate(row.get("date"), errors);
        String account = required(row, "account", errors);
        String symbol = required(row, "symbol", errors);
        AssetType assetType = parseEnum(row, "asset_type", AssetType.class, errors);
        TxnType type = parseEnum(row, "type", TxnType.class, errors);
        AccountType accountType = parseAccountType(row, errors);

        BigDecimal quantity = null;
        BigDecimal price = null;
        int numerator = 0;
        int denominator = 0;
        if (type == TxnType.SPLIT) {
            forbidden(row, "quantity", "A split has no quantity", errors);
            forbidden(row, "price", "A split has no price", errors);
            Matcher ratio = RATIO.matcher(row.get("split_ratio"));
            if (ratio.matches()) {
                numerator = Integer.parseInt(ratio.group(1));
                denominator = Integer.parseInt(ratio.group(2));
            } else {
                errors.add(
                        new ImportIssue(
                                "split_ratio",
                                "Use a ratio like 2:1 with whole numbers above zero"));
            }
        } else if (type != null) {
            forbidden(row, "split_ratio", "Only a split has a ratio", errors);
            quantity = parseDecimal(row, "quantity", true, errors);
            price = parseDecimal(row, "price", false, errors);
        }

        String sourceId = row.get("source_id");
        boolean createsAccount = account != null && !existing.accountExists(account);
        boolean createsInstrument = false;
        if (symbol != null && assetType != null) {
            createsInstrument = !existing.instrumentExists(symbol, assetType);
            String instrumentKey = symbol + "/" + assetType;
            if (!sourceId.isEmpty()) {
                sourceIdSeen.put(instrumentKey, true);
            }
            if (createsInstrument
                    && assetType == AssetType.CRYPTO
                    && !sourceIdSeen.containsKey(instrumentKey)) {
                errors.add(
                        new ImportIssue(
                                "source_id",
                                "A new crypto needs its CoinGecko coin id, for example bitcoin"));
            }
        }

        if (!errors.isEmpty()) {
            return new ImportRowResult(
                    row.lineNo(),
                    null,
                    errors,
                    List.of(),
                    createsAccount,
                    createsInstrument,
                    false);
        }

        ParsedRow parsed =
                new ParsedRow(
                        date,
                        account,
                        accountType,
                        symbol,
                        assetType,
                        type,
                        quantity,
                        price,
                        numerator,
                        denominator,
                        sourceId.isEmpty() ? null : sourceId,
                        row.get("note").isEmpty() ? null : row.get("note"));

        List<ImportIssue> warnings = new ArrayList<>();
        boolean duplicate = false;
        String signature = signature(parsed);
        Integer earlier = firstLineOfRow.putIfAbsent(signature, row.lineNo());
        if (earlier != null) {
            duplicate = true;
            warnings.add(
                    new ImportIssue(
                            "row",
                            "Identical to line "
                                    + earlier
                                    + "; skipped unless you include duplicates"));
        } else if (existing.transactionsOf(account, symbol, assetType).stream()
                .anyMatch(t -> sameTransaction(t, parsed))) {
            duplicate = true;
            warnings.add(
                    new ImportIssue(
                            "row",
                            "An identical transaction already exists; skipped unless you include duplicates"));
        }
        return new ImportRowResult(
                row.lineNo(),
                parsed,
                List.of(),
                warnings,
                createsAccount,
                createsInstrument,
                duplicate);
    }

    private static void replayPositions(
            List<ImportRowResult> results, ExistingData existing, boolean includeDuplicates) {
        Map<String, List<Integer>> byPosition = new LinkedHashMap<>();
        for (int i = 0; i < results.size(); i++) {
            ImportRowResult result = results.get(i);
            if (result.parsed() == null || (result.duplicate() && !includeDuplicates)) {
                continue;
            }
            ParsedRow p = result.parsed();
            byPosition
                    .computeIfAbsent(
                            p.accountName() + "|" + p.symbol() + "|" + p.assetType(),
                            k -> new ArrayList<>())
                    .add(i);
        }

        long seq = STAGED_SEQ_BASE;
        for (List<Integer> indexes : byPosition.values()) {
            ParsedRow first = results.get(indexes.get(0)).parsed();
            List<Txn> history =
                    new ArrayList<>(
                            existing.transactionsOf(
                                    first.accountName(), first.symbol(), first.assetType()));
            Map<UUID, Integer> indexById = new HashMap<>();
            for (int index : indexes) {
                Txn txn = toTxn(results.get(index).parsed(), seq++);
                indexById.put(txn.id(), index);
                history.add(txn);
            }
            Optional<Violation> violation = FifoEngine.replay(history).violation();
            violation.ifPresent(
                    v -> {
                        Integer index = indexById.get(v.transactionId());
                        if (index != null) {
                            ImportRowResult bad = results.get(index);
                            results.set(
                                    index, bad.withError(new ImportIssue("quantity", v.message())));
                        }
                    });
        }
    }

    private static Txn toTxn(ParsedRow p, long seq) {
        return new Txn(
                UUID.randomUUID(),
                seq,
                p.type(),
                p.date(),
                p.quantity(),
                p.unitPrice(),
                p.splitNumerator(),
                p.splitDenominator());
    }

    private static String signature(ParsedRow p) {
        return String.join(
                "|",
                p.accountName(),
                p.symbol(),
                p.assetType().name(),
                p.date().toString(),
                p.type().name(),
                plain(p.quantity()),
                plain(p.unitPrice()),
                p.splitNumerator() + ":" + p.splitDenominator());
    }

    private static boolean sameTransaction(Txn t, ParsedRow p) {
        return t.type() == p.type()
                && t.date().equals(p.date())
                && sameDecimal(t.quantity(), p.quantity())
                && sameDecimal(t.unitPrice(), p.unitPrice())
                && t.splitNum() == p.splitNumerator()
                && t.splitDen() == p.splitDenominator();
    }

    private static boolean sameDecimal(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    private static String plain(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    private static LocalDate parseDate(String text, List<ImportIssue> errors) {
        if (text.isEmpty()) {
            errors.add(new ImportIssue("date", "Date is required"));
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            errors.add(new ImportIssue("date", "Use a valid date as YYYY-MM-DD"));
            return null;
        }
    }

    private static String required(CsvRow row, String column, List<ImportIssue> errors) {
        String value = row.get(column);
        if (value.isEmpty()) {
            errors.add(new ImportIssue(column, column + " is required"));
            return null;
        }
        return value;
    }

    private static <E extends Enum<E>> E parseEnum(
            CsvRow row, String column, Class<E> type, List<ImportIssue> errors) {
        String value = row.get(column);
        if (value.isEmpty()) {
            errors.add(new ImportIssue(column, column + " is required"));
            return null;
        }
        try {
            return Enum.valueOf(type, value.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            errors.add(
                    new ImportIssue(
                            column,
                            "Unknown "
                                    + column
                                    + " "
                                    + value
                                    + "; use one of "
                                    + java.util.Arrays.toString(type.getEnumConstants())));
            return null;
        }
    }

    private static AccountType parseAccountType(CsvRow row, List<ImportIssue> errors) {
        String value = row.get("account_type");
        if (value.isEmpty()) {
            return AccountType.OTHER;
        }
        try {
            return AccountType.valueOf(value.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            errors.add(
                    new ImportIssue(
                            "account_type",
                            "Unknown account_type "
                                    + value
                                    + "; use one of "
                                    + java.util.Arrays.toString(AccountType.values())));
            return null;
        }
    }

    private static void forbidden(
            CsvRow row, String column, String message, List<ImportIssue> errors) {
        if (!row.get(column).isEmpty()) {
            errors.add(new ImportIssue(column, message));
        }
    }

    /** Quantity must be above zero, a price may be zero; both have at most 8 decimals. */
    private static BigDecimal parseDecimal(
            CsvRow row, String column, boolean mustBePositive, List<ImportIssue> errors) {
        String text = row.get(column);
        if (text.isEmpty()) {
            errors.add(new ImportIssue(column, column + " is required"));
            return null;
        }
        if (!DECIMAL.matcher(text).matches()) {
            errors.add(
                    new ImportIssue(
                            column, "Enter a number of zero or more with at most 8 decimals"));
            return null;
        }
        BigDecimal value = new BigDecimal(text);
        if (value.precision() - value.scale() > MAX_INTEGER_DIGITS) {
            errors.add(new ImportIssue(column, "The number is too large"));
            return null;
        }
        if (mustBePositive && value.signum() == 0) {
            errors.add(new ImportIssue(column, "Quantity must be greater than zero"));
            return null;
        }
        return value;
    }
}
