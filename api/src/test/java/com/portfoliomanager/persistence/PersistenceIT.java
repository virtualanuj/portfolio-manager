package com.portfoliomanager.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.portfoliomanager.AbstractIntegrationTest;
import com.portfoliomanager.domain.AccountType;
import com.portfoliomanager.domain.AssetType;
import com.portfoliomanager.domain.TxnType;
import com.portfoliomanager.pricing.PriceFetchStatus;
import com.portfoliomanager.pricing.PriceSource;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PersistenceIT extends AbstractIntegrationTest {

    @Autowired private AccountRepository accounts;
    @Autowired private InstrumentRepository instruments;
    @Autowired private TransactionRepository transactions;
    @Autowired private PriceRepository prices;
    @Autowired private TargetAllocationRepository targets;
    @Autowired private EntityManager entityManager;

    private AccountEntity account() {
        return accounts.saveAndFlush(
                new AccountEntity("Main-" + UUID.randomUUID(), AccountType.BROKERAGE));
    }

    private static String uniqueSymbol() {
        return "S" + UUID.randomUUID().toString().substring(0, 8);
    }

    private InstrumentEntity instrument(String symbol, AssetType type) {
        return instruments.saveAndFlush(
                new InstrumentEntity(symbol, "Name", type, PriceSource.YAHOO, symbol));
    }

    private TransactionEntity buy(
            UUID accountId, UUID instrumentId, String quantity, String price) {
        return TransactionEntity.trade(
                accountId,
                instrumentId,
                TxnType.BUY,
                LocalDate.parse("2026-01-05"),
                new BigDecimal(quantity),
                new BigDecimal(price),
                null);
    }

    private <T> T reload(Class<T> type, Object id) {
        entityManager.flush();
        entityManager.clear();
        return entityManager.find(type, id);
    }

    @Test
    void accountRoundTripsWithGeneratedIdAndCreatedAt() {
        AccountEntity saved = account();

        AccountEntity loaded = reload(AccountEntity.class, saved.getId());

        assertThat(loaded.getId()).isNotNull();
        assertThat(loaded.getName()).isEqualTo(saved.getName());
        assertThat(loaded.getType()).isEqualTo(AccountType.BROKERAGE);
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    void transactionQuantityAndPriceKeepEightDecimalPlaces() {
        AccountEntity account = account();
        InstrumentEntity btc = instrument(uniqueSymbol(), AssetType.CRYPTO);
        TransactionEntity saved =
                transactions.saveAndFlush(
                        buy(account.getId(), btc.getId(), "0.00000001", "65000.5"));

        TransactionEntity loaded = reload(TransactionEntity.class, saved.getId());

        assertThat(loaded.getQuantity()).isEqualByComparingTo("0.00000001");
        assertThat(loaded.getQuantity().scale()).isEqualTo(8);
        assertThat(loaded.getUnitPrice()).isEqualByComparingTo("65000.5");
        assertThat(loaded.getUnitPrice().scale()).isEqualTo(8);
    }

    @Test
    void splitTransactionStoresRatioWithoutQuantityOrPrice() {
        AccountEntity account = account();
        InstrumentEntity etf = instrument(uniqueSymbol(), AssetType.ETF);
        TransactionEntity saved =
                transactions.saveAndFlush(
                        TransactionEntity.split(
                                account.getId(),
                                etf.getId(),
                                LocalDate.parse("2026-02-01"),
                                2,
                                1,
                                null));

        TransactionEntity loaded = reload(TransactionEntity.class, saved.getId());

        assertThat(loaded.getType()).isEqualTo(TxnType.SPLIT);
        assertThat(loaded.getQuantity()).isNull();
        assertThat(loaded.getUnitPrice()).isNull();
        assertThat(loaded.getSplitNumerator()).isEqualTo(2);
        assertThat(loaded.getSplitDenominator()).isEqualTo(1);
    }

    @Test
    void transactionSeqIsGeneratedByTheDatabaseAndIncreases() {
        AccountEntity account = account();
        InstrumentEntity etf = instrument(uniqueSymbol(), AssetType.ETF);

        TransactionEntity first =
                transactions.saveAndFlush(buy(account.getId(), etf.getId(), "1", "10"));
        TransactionEntity second =
                transactions.saveAndFlush(buy(account.getId(), etf.getId(), "1", "10"));

        assertThat(first.getSeq()).isNotNull();
        assertThat(second.getSeq()).isGreaterThan(first.getSeq());
    }

    @Test
    void instrumentSymbolAndAssetTypeMustBeUnique() {
        String symbol = uniqueSymbol();
        instrument(symbol, AssetType.STOCK);

        assertThatThrownBy(() -> instrument(symbol, AssetType.STOCK))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameSymbolWithDifferentAssetTypeIsAllowed() {
        String symbol = uniqueSymbol();
        instrument(symbol, AssetType.STOCK);
        instrument(symbol, AssetType.CRYPTO);

        assertThat(instruments.findAll()).filteredOn(i -> i.getSymbol().equals(symbol)).hasSize(2);
    }

    @Test
    void priceRoundTripsManualPriceAndFeedFields() {
        InstrumentEntity fund = instrument(uniqueSymbol(), AssetType.MUTUAL_FUND);
        PriceEntity price = new PriceEntity(fund.getId());
        price.setManualPrice(new BigDecimal("118.4"), LocalDate.parse("2026-09-01"));
        prices.saveAndFlush(price);

        PriceEntity loaded = reload(PriceEntity.class, fund.getId());

        assertThat(loaded.getManualPrice()).isEqualByComparingTo("118.4");
        assertThat(loaded.getManualPrice().scale()).isEqualTo(8);
        assertThat(loaded.getManualAsOf()).isEqualTo(LocalDate.parse("2026-09-01"));
        assertThat(loaded.getStatus()).isEqualTo(PriceFetchStatus.OK);
        assertThat(loaded.getPrice()).isNull();
    }

    @Test
    void targetAllocationRoundTripsWithTwoDecimalPlaces() {
        targets.saveAndFlush(new TargetAllocationEntity(AssetType.ETF, new BigDecimal("60.5")));

        TargetAllocationEntity loaded = reload(TargetAllocationEntity.class, AssetType.ETF);

        assertThat(loaded.getTargetPct()).isEqualByComparingTo("60.5");
        assertThat(loaded.getTargetPct().scale()).isEqualTo(2);
    }
}
