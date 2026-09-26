package com.portfoliomanager.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.portfoliomanager.AbstractIntegrationTest;
import com.portfoliomanager.application.TransactionService;
import com.portfoliomanager.domain.AccountType;
import com.portfoliomanager.domain.AssetType;
import com.portfoliomanager.persistence.AccountEntity;
import com.portfoliomanager.persistence.AccountRepository;
import com.portfoliomanager.persistence.InstrumentEntity;
import com.portfoliomanager.persistence.InstrumentRepository;
import com.portfoliomanager.persistence.TransactionRepository;
import com.portfoliomanager.pricing.PriceSource;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

class TransactionApiIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private AccountRepository accounts;
    @Autowired private InstrumentRepository instruments;
    @Autowired private TransactionRepository transactions;
    @Autowired private TransactionService service;

    private UUID accountId;
    private UUID otherAccountId;
    private UUID etfId;
    private UUID fundId;

    @BeforeEach
    void seed() {
        accountId = accounts.save(new AccountEntity("Main", AccountType.BROKERAGE)).getId();
        otherAccountId = accounts.save(new AccountEntity("Other", AccountType.RETIREMENT)).getId();
        etfId =
                instruments
                        .save(
                                new InstrumentEntity(
                                        "VTI", "VTI", AssetType.ETF, PriceSource.YAHOO, "VTI"))
                        .getId();
        fundId =
                instruments
                        .save(
                                new InstrumentEntity(
                                        "VTSAX",
                                        "VTSAX",
                                        AssetType.MUTUAL_FUND,
                                        PriceSource.MANUAL,
                                        null))
                        .getId();
    }

    private String trade(
            UUID account, UUID instrument, String type, String date, String qty, String price) {
        return """
                {"accountId":"%s","instrumentId":"%s","type":"%s","tradeDate":"%s","quantity":"%s","unitPrice":"%s"}"""
                .formatted(account, instrument, type, date, qty, price);
    }

    private String split(String date, String numerator, String denominator) {
        return """
                {"accountId":"%s","instrumentId":"%s","type":"SPLIT","tradeDate":"%s","splitNumerator":%s,"splitDenominator":%s}"""
                .formatted(accountId, etfId, date, numerator, denominator);
    }

    private ResultActions send(String json) throws Exception {
        return mvc.perform(
                post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String created(String json) throws Exception {
        MvcResult result = send(json).andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    @Test
    void buyThenSellWithinHoldingsIsAccepted() throws Exception {
        created(trade(accountId, etfId, "BUY", "2026-01-05", "10", "100"));

        send(trade(accountId, etfId, "SELL", "2026-01-06", "4", "110"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("SELL"))
                .andExpect(jsonPath("$.quantity").value("4.00000000"))
                .andExpect(jsonPath("$.unitPrice").value("110.00000000"))
                .andExpect(jsonPath("$.accountName").value("Main"))
                .andExpect(jsonPath("$.symbol").value("VTI"));
    }

    @Test
    void sellBeyondHoldingsReturns422AndStoresNothing() throws Exception {
        created(trade(accountId, etfId, "BUY", "2026-01-05", "10", "100"));

        send(trade(accountId, etfId, "SELL", "2026-01-06", "15", "110"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(
                        jsonPath("$.detail")
                                .value(containsString("Sell of 15 exceeds the 10 held")))
                .andExpect(jsonPath("$.transactionId").isNotEmpty());

        assertThat(transactions.count()).isEqualTo(1);
    }

    @Test
    void deletingABuyThatALaterSellDependsOnReturns422NamingTheSellAndKeepsData() throws Exception {
        String buyId = created(trade(accountId, etfId, "BUY", "2026-01-05", "10", "100"));
        String sellId = created(trade(accountId, etfId, "SELL", "2026-01-06", "5", "110"));

        mvc.perform(delete("/api/transactions/" + buyId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.transactionId").value(sellId));

        assertThat(transactions.count()).isEqualTo(2);
    }

    @Test
    void editingASellAboveHoldingsReturns422AndLeavesItUnchanged() throws Exception {
        created(trade(accountId, etfId, "BUY", "2026-01-05", "10", "100"));
        String sellId = created(trade(accountId, etfId, "SELL", "2026-01-06", "5", "110"));

        mvc.perform(
                        put("/api/transactions/" + sellId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        trade(accountId, etfId, "SELL", "2026-01-06", "11", "110")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.transactionId").value(sellId));

        assertThat(transactions.findById(UUID.fromString(sellId)).orElseThrow().getQuantity())
                .isEqualByComparingTo("5");
    }

    @Test
    void reducingABuyBelowWhatWasSoldReturns422() throws Exception {
        String buyId = created(trade(accountId, etfId, "BUY", "2026-01-05", "10", "100"));
        created(trade(accountId, etfId, "SELL", "2026-01-06", "8", "110"));

        mvc.perform(
                        put("/api/transactions/" + buyId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(trade(accountId, etfId, "BUY", "2026-01-05", "5", "100")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void movingABuyToAnotherPositionValidatesTheOldPositionToo() throws Exception {
        String buyId = created(trade(accountId, etfId, "BUY", "2026-01-05", "10", "100"));
        created(trade(accountId, etfId, "SELL", "2026-01-06", "5", "110"));

        mvc.perform(
                        put("/api/transactions/" + buyId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        trade(
                                                otherAccountId,
                                                etfId,
                                                "BUY",
                                                "2026-01-05",
                                                "10",
                                                "100")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void sameDaySellAfterBuyIsAcceptedInInsertionOrder() throws Exception {
        created(trade(accountId, etfId, "BUY", "2026-01-05", "10", "100"));

        send(trade(accountId, etfId, "SELL", "2026-01-05", "10", "101"))
                .andExpect(status().isCreated());
    }

    @Test
    void sameDaySellEnteredBeforeItsBuyIsRejected() throws Exception {
        send(trade(accountId, etfId, "SELL", "2026-01-05", "10", "101"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void splitAdjustsThePositionAndNeedsBothRatioFields() throws Exception {
        created(trade(accountId, etfId, "BUY", "2026-01-05", "10", "100"));

        send(split("2026-02-01", "2", "1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.splitNumerator").value(2))
                .andExpect(jsonPath("$.quantity").doesNotExist());
        send("""
                        {"accountId":"%s","instrumentId":"%s","type":"SPLIT","tradeDate":"2026-02-01","splitNumerator":2}"""
                        .formatted(accountId, etfId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("splitDenominator"));
    }

    @Test
    void splitForbidsQuantityAndPrice() throws Exception {
        send("""
                        {"accountId":"%s","instrumentId":"%s","type":"SPLIT","tradeDate":"2026-02-01","splitNumerator":2,"splitDenominator":1,"quantity":"5"}"""
                        .formatted(accountId, etfId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("quantity"));
    }

    @Test
    void tradesForbidSplitFields() throws Exception {
        send("""
                        {"accountId":"%s","instrumentId":"%s","type":"BUY","tradeDate":"2026-02-01","quantity":"1","unitPrice":"1","splitNumerator":2}"""
                        .formatted(accountId, etfId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("splitNumerator"));
    }

    @Test
    void nonPositiveQuantityOrNegativePriceReturns400() throws Exception {
        send(trade(accountId, etfId, "BUY", "2026-01-05", "0", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("quantity"));
        send(trade(accountId, etfId, "BUY", "2026-01-05", "-1", "100"))
                .andExpect(status().isBadRequest());
        send(trade(accountId, etfId, "BUY", "2026-01-05", "1", "-5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("unitPrice"));
    }

    @Test
    void zeroPriceIsAllowed() throws Exception {
        send(trade(accountId, etfId, "REINVEST", "2026-01-05", "1", "0"))
                .andExpect(status().isCreated());
    }

    @Test
    void tradeWithoutQuantityReturns400() throws Exception {
        send("""
                        {"accountId":"%s","instrumentId":"%s","type":"BUY","tradeDate":"2026-01-05","unitPrice":"1"}"""
                        .formatted(accountId, etfId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("quantity"));
    }

    @Test
    void cashDividendIsNotATransactionType() throws Exception {
        send(trade(accountId, etfId, "DIVIDEND", "2026-01-05", "1", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownAccountOrInstrumentReturns400NamingTheField() throws Exception {
        send(trade(UUID.randomUUID(), etfId, "BUY", "2026-01-05", "1", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("accountId"));
        send(trade(accountId, UUID.randomUUID(), "BUY", "2026-01-05", "1", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("instrumentId"));
    }

    @Test
    void updateAndDeleteUnknownTransactionReturn404() throws Exception {
        mvc.perform(
                        put("/api/transactions/" + UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(trade(accountId, etfId, "BUY", "2026-01-05", "1", "1")))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/transactions/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteWithoutDependentsReturns204() throws Exception {
        String id = created(trade(accountId, etfId, "BUY", "2026-01-05", "10", "100"));

        mvc.perform(delete("/api/transactions/" + id)).andExpect(status().isNoContent());

        assertThat(transactions.count()).isZero();
    }

    @Test
    void replayForReturnsThePositionOfOneAccountAndInstrument() throws Exception {
        created(trade(accountId, etfId, "BUY", "2026-01-05", "10", "100"));
        created(trade(otherAccountId, etfId, "BUY", "2026-01-05", "99", "100"));

        assertThat(service.replayFor(accountId, etfId).position().quantity())
                .isEqualByComparingTo("10");
    }

    @Test
    void listFiltersByAccountInstrumentTypeAndDates() throws Exception {
        created(trade(accountId, etfId, "BUY", "2026-01-05", "10", "100"));
        created(trade(accountId, etfId, "SELL", "2026-02-05", "1", "100"));
        created(trade(otherAccountId, fundId, "BUY", "2026-03-05", "3", "50"));

        mvc.perform(get("/api/transactions").param("accountId", accountId.toString()))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.totalItems").value(2));
        mvc.perform(get("/api/transactions").param("instrumentId", fundId.toString()))
                .andExpect(jsonPath("$.items", hasSize(1)));
        mvc.perform(get("/api/transactions").param("type", "SELL"))
                .andExpect(jsonPath("$.items", hasSize(1)));
        mvc.perform(get("/api/transactions").param("from", "2026-02-01").param("to", "2026-02-28"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].type").value("SELL"));
    }

    @Test
    void listIsPagedNewestFirst() throws Exception {
        created(trade(accountId, etfId, "BUY", "2026-01-05", "1", "1"));
        created(trade(accountId, etfId, "BUY", "2026-01-06", "1", "1"));
        created(trade(accountId, etfId, "BUY", "2026-01-07", "1", "1"));

        mvc.perform(get("/api/transactions").param("page", "0").param("size", "2"))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].tradeDate").value("2026-01-07"))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2));
        mvc.perform(get("/api/transactions").param("page", "1").param("size", "2"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].tradeDate").value("2026-01-05"));
    }
}
