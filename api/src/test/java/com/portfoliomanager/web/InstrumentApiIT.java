package com.portfoliomanager.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.portfoliomanager.AbstractIntegrationTest;
import com.portfoliomanager.domain.AccountType;
import com.portfoliomanager.domain.TxnType;
import com.portfoliomanager.persistence.AccountEntity;
import com.portfoliomanager.persistence.AccountRepository;
import com.portfoliomanager.persistence.TransactionEntity;
import com.portfoliomanager.persistence.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class InstrumentApiIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private AccountRepository accounts;
    @Autowired private TransactionRepository transactions;

    private String createdId(String json) throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/instruments")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json))
                        .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    @Test
    void stockEtfAndFundDefaultToYahooWithSymbolAsSourceId() throws Exception {
        for (String type : new String[] {"STOCK", "ETF", "MUTUAL_FUND"}) {
            mvc.perform(
                            post("/api/instruments")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            "{\"symbol\":\"S"
                                                    + type
                                                    + "\",\"assetType\":\""
                                                    + type
                                                    + "\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.priceSource").value("YAHOO"))
                    .andExpect(jsonPath("$.sourceId").value("S" + type));
        }
    }

    @Test
    void cryptoDefaultsToCoinGeckoAndKeepsItsSourceId() throws Exception {
        mvc.perform(
                        post("/api/instruments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"symbol\":\"BTC\",\"assetType\":\"CRYPTO\",\"sourceId\":\"bitcoin\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priceSource").value("COINGECKO"))
                .andExpect(jsonPath("$.sourceId").value("bitcoin"));
    }

    @Test
    void cryptoWithoutSourceIdReturns400() throws Exception {
        mvc.perform(
                        post("/api/instruments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"symbol\":\"BTC\",\"assetType\":\"CRYPTO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sourceId"));
    }

    @Test
    void manualCryptoDoesNotNeedASourceId() throws Exception {
        mvc.perform(
                        post("/api/instruments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"symbol\":\"OBSCURE\",\"assetType\":\"CRYPTO\",\"priceSource\":\"MANUAL\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void priceSourceMustMatchTheAssetType() throws Exception {
        mvc.perform(
                        post("/api/instruments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"symbol\":\"VTI\",\"assetType\":\"ETF\",\"priceSource\":\"COINGECKO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("priceSource"));
    }

    @Test
    void duplicateSymbolAndAssetTypeReturns409ButOtherTypesAreAllowed() throws Exception {
        createdId("{\"symbol\":\"ABC\",\"assetType\":\"STOCK\"}");

        mvc.perform(
                        post("/api/instruments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"symbol\":\"ABC\",\"assetType\":\"STOCK\"}"))
                .andExpect(status().isConflict());
        mvc.perform(
                        post("/api/instruments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"symbol\":\"ABC\",\"assetType\":\"ETF\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void manualPriceCanBeSetAndCleared() throws Exception {
        String id =
                createdId(
                        "{\"symbol\":\"VTSAX\",\"assetType\":\"MUTUAL_FUND\",\"priceSource\":\"MANUAL\"}");

        mvc.perform(
                        put("/api/instruments/" + id + "/manual-price")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"price\":\"118.4\",\"asOf\":\"2026-09-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manualPrice").value("118.40000000"))
                .andExpect(jsonPath("$.manualAsOf").value("2026-09-01"));
        mvc.perform(get("/api/instruments"))
                .andExpect(jsonPath("$[0].manualPrice").value("118.40000000"));

        mvc.perform(delete("/api/instruments/" + id + "/manual-price"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/instruments"))
                .andExpect(jsonPath("$[0].manualPrice").value(nullValue()))
                .andExpect(jsonPath("$[0].manualAsOf").value(nullValue()));
    }

    @Test
    void manualPriceNeedsPriceAndDate() throws Exception {
        String id = createdId("{\"symbol\":\"VTSAX\",\"assetType\":\"MUTUAL_FUND\"}");

        mvc.perform(
                        put("/api/instruments/" + id + "/manual-price")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"price\":\"-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItems("price", "asOf")));
    }

    @Test
    void manualPriceForUnknownInstrumentReturns404() throws Exception {
        mvc.perform(
                        put("/api/instruments/" + UUID.randomUUID() + "/manual-price")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"price\":\"1\",\"asOf\":\"2026-09-01\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void feedInstrumentCanBeSwitchedToManual() throws Exception {
        String id = createdId("{\"symbol\":\"VTI\",\"assetType\":\"ETF\"}");

        mvc.perform(
                        put("/api/instruments/" + id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"symbol\":\"VTI\",\"assetType\":\"ETF\",\"priceSource\":\"MANUAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceSource").value("MANUAL"));
    }

    @Test
    void updateToAnotherInstrumentsSymbolReturns409() throws Exception {
        createdId("{\"symbol\":\"AAA\",\"assetType\":\"STOCK\"}");
        String id = createdId("{\"symbol\":\"BBB\",\"assetType\":\"STOCK\"}");

        mvc.perform(
                        put("/api/instruments/" + id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"symbol\":\"AAA\",\"assetType\":\"STOCK\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void listReturnsInstrumentsBySymbol() throws Exception {
        createdId("{\"symbol\":\"ZZZ\",\"assetType\":\"STOCK\"}");
        createdId("{\"symbol\":\"AAA\",\"assetType\":\"STOCK\"}");

        mvc.perform(get("/api/instruments"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].symbol").value("AAA"));
    }

    @Test
    void deleteUnreferencedInstrumentReturns204() throws Exception {
        String id = createdId("{\"symbol\":\"AAA\",\"assetType\":\"STOCK\"}");

        mvc.perform(delete("/api/instruments/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/instruments")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void deleteInstrumentWithTransactionsReturns409() throws Exception {
        String id = createdId("{\"symbol\":\"AAA\",\"assetType\":\"STOCK\"}");
        AccountEntity account = accounts.save(new AccountEntity("Main", AccountType.BROKERAGE));
        transactions.save(
                TransactionEntity.trade(
                        account.getId(),
                        UUID.fromString(id),
                        TxnType.BUY,
                        LocalDate.parse("2026-01-05"),
                        new BigDecimal("1"),
                        new BigDecimal("10"),
                        null));

        mvc.perform(delete("/api/instruments/" + id)).andExpect(status().isConflict());
    }

    @Test
    void deleteUnknownInstrumentReturns404() throws Exception {
        mvc.perform(delete("/api/instruments/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
