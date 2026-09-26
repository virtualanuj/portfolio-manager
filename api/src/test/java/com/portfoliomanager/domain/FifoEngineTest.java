package com.portfoliomanager.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FifoEngineTest {

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static Txn buy(long seq, String date, String quantity, String price) {
        return txn(TxnType.BUY, seq, date, quantity, price);
    }

    private static Txn sell(long seq, String date, String quantity) {
        return txn(TxnType.SELL, seq, date, quantity, "0");
    }

    private static Txn reinvest(long seq, String date, String quantity, String price) {
        return txn(TxnType.REINVEST, seq, date, quantity, price);
    }

    private static Txn txn(TxnType type, long seq, String date, String quantity, String price) {
        return new Txn(
                UUID.randomUUID(), seq, type, LocalDate.parse(date), d(quantity), d(price), 0, 0);
    }

    private static Txn split(long seq, String date, int numerator, int denominator) {
        return new Txn(
                UUID.randomUUID(),
                seq,
                TxnType.SPLIT,
                LocalDate.parse(date),
                null,
                null,
                numerator,
                denominator);
    }

    @Test
    void specWorkedExampleBuysSellAndSplit() {
        Txn firstBuy = buy(1, "2026-01-10", "10", "100");
        Txn secondBuy = buy(2, "2026-02-10", "10", "120");
        Txn partialSell = sell(3, "2026-03-10", "15");
        Txn twoForOne = split(4, "2026-04-10", 2, 1);

        Position afterFeb = FifoEngine.replay(List.of(firstBuy, secondBuy)).position();
        assertThat(afterFeb.quantity()).isEqualByComparingTo("20");
        assertThat(afterFeb.costBasis()).isEqualByComparingTo("2200");

        Position afterMar = FifoEngine.replay(List.of(firstBuy, secondBuy, partialSell)).position();
        assertThat(afterMar.lots()).hasSize(1);
        assertThat(afterMar.quantity()).isEqualByComparingTo("5");
        assertThat(afterMar.costBasis()).isEqualByComparingTo("600");

        ReplayResult result =
                FifoEngine.replay(List.of(firstBuy, secondBuy, partialSell, twoForOne));
        assertThat(result.violation()).isEmpty();
        assertThat(result.position().lots()).hasSize(1);
        assertThat(result.position().quantity()).isEqualByComparingTo("10");
        assertThat(result.position().costBasis()).isEqualByComparingTo("600");
        assertThat(result.position().avgCost())
                .hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo("60"));
        assertThat(result.position().lots().get(0).unitCost()).isEqualByComparingTo("60");
    }

    @Test
    void partiallyConsumedLotKeepsItsOriginalUnitCost() {
        Position position =
                FifoEngine.replay(
                                List.of(
                                        buy(1, "2026-01-10", "10", "100"),
                                        sell(2, "2026-01-11", "4")))
                        .position();

        assertThat(position.lots()).hasSize(1);
        assertThat(position.lots().get(0).quantity()).isEqualByComparingTo("6");
        assertThat(position.lots().get(0).unitCost()).isEqualByComparingTo("100");
        assertThat(position.costBasis()).isEqualByComparingTo("600");
    }

    @Test
    void sellBeyondHoldingsIsAViolationNamingTheSell() {
        Txn oversell = sell(2, "2026-01-11", "15");

        ReplayResult result =
                FifoEngine.replay(List.of(buy(1, "2026-01-10", "10", "100"), oversell));

        assertThat(result.violation()).isPresent();
        assertThat(result.violation().get().transactionId()).isEqualTo(oversell.id());
        assertThat(result.violation().get().message()).contains("15").contains("10");
        assertThat(result.position().quantity()).isEqualByComparingTo("10");
    }

    @Test
    void reverseSplitOneForTenMultipliesCostAndShrinksQuantity() {
        Position position =
                FifoEngine.replay(
                                List.of(
                                        buy(1, "2026-01-10", "100", "5"),
                                        split(2, "2026-02-10", 1, 10)))
                        .position();

        assertThat(position.quantity()).isEqualByComparingTo("10");
        assertThat(position.lots().get(0).unitCost()).isEqualByComparingTo("50");
        assertThat(position.costBasis()).isEqualByComparingTo("500");
    }

    @Test
    void splitRoundsOncePerLotAndBasisIsRoundedOnceAtTheEnd() {
        Position position =
                FifoEngine.replay(
                                List.of(
                                        buy(1, "2026-01-10", "1", "100"),
                                        split(2, "2026-02-10", 3, 1)))
                        .position();

        assertThat(position.quantity()).isEqualByComparingTo("3");
        assertThat(position.lots().get(0).unitCost()).isEqualByComparingTo("33.33333333");
        assertThat(position.costBasis()).isEqualByComparingTo("100.0000");
        assertThat(position.costBasis().scale()).isEqualTo(4);
    }

    @Test
    void fractionalCryptoQuantitiesKeepExactBasis() {
        Position position =
                FifoEngine.replay(
                                List.of(
                                        buy(1, "2026-01-10", "0.12345678", "50000"),
                                        sell(2, "2026-01-11", "0.00000001")))
                        .position();

        assertThat(position.quantity()).isEqualByComparingTo("0.12345677");
        assertThat(position.costBasis()).isEqualByComparingTo("6172.8385");
    }

    @Test
    void reinvestmentBehavesAsABuy() {
        Position position =
                FifoEngine.replay(
                                List.of(
                                        buy(1, "2026-01-10", "10", "100"),
                                        reinvest(2, "2026-02-10", "0.5", "110")))
                        .position();

        assertThat(position.lots()).hasSize(2);
        assertThat(position.quantity()).isEqualByComparingTo("10.5");
        assertThat(position.costBasis()).isEqualByComparingTo("1055");
    }

    @Test
    void sameDateTransactionsReplayInSeqOrder() {
        Txn sellFirst = sell(1, "2026-01-10", "5");
        Txn buyLater = buy(2, "2026-01-10", "10", "100");

        ReplayResult result = FifoEngine.replay(List.of(buyLater, sellFirst));

        assertThat(result.violation()).isPresent();
        assertThat(result.violation().get().transactionId()).isEqualTo(sellFirst.id());
    }

    @Test
    void sameDateBuyBeforeSellByLowerSeqIsValidEvenWhenGivenOutOfOrder() {
        ReplayResult result =
                FifoEngine.replay(
                        List.of(sell(2, "2026-01-10", "5"), buy(1, "2026-01-10", "10", "100")));

        assertThat(result.violation()).isEmpty();
        assertThat(result.position().quantity()).isEqualByComparingTo("5");
    }

    @Test
    void earlierDatesReplayBeforeLaterDatesRegardlessOfSeq() {
        ReplayResult result =
                FifoEngine.replay(
                        List.of(sell(1, "2026-03-01", "5"), buy(2, "2026-02-01", "10", "100")));

        assertThat(result.violation()).isEmpty();
    }

    @Test
    void inputListIsNotMutated() {
        List<Txn> unsorted =
                java.util.Arrays.asList(
                        sell(2, "2026-01-11", "1"), buy(1, "2026-01-10", "10", "100"));
        List<Txn> copy = List.copyOf(unsorted);

        FifoEngine.replay(unsorted);

        assertThat(unsorted).containsExactlyElementsOf(copy);
    }

    @Test
    void emptyHistoryGivesEmptyPosition() {
        ReplayResult result = FifoEngine.replay(List.of());

        assertThat(result.violation()).isEmpty();
        assertThat(result.position().lots()).isEmpty();
        assertThat(result.position().quantity()).isEqualByComparingTo("0");
        assertThat(result.position().costBasis()).isEqualByComparingTo("0");
        assertThat(result.position().avgCost()).isEmpty();
    }

    @Test
    void fullySoldPositionHasNoAverageCost() {
        Position position =
                FifoEngine.replay(
                                List.of(
                                        buy(1, "2026-01-10", "10", "100"),
                                        sell(2, "2026-01-11", "10")))
                        .position();

        assertThat(position.quantity()).isEqualByComparingTo("0");
        assertThat(position.avgCost()).isEmpty();
    }
}
