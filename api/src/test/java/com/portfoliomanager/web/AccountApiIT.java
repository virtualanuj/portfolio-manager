package com.portfoliomanager.web;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.portfoliomanager.AbstractIntegrationTest;
import com.portfoliomanager.domain.AssetType;
import com.portfoliomanager.domain.TxnType;
import com.portfoliomanager.persistence.InstrumentEntity;
import com.portfoliomanager.persistence.InstrumentRepository;
import com.portfoliomanager.persistence.TransactionEntity;
import com.portfoliomanager.persistence.TransactionRepository;
import com.portfoliomanager.pricing.PriceSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class AccountApiIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private InstrumentRepository instruments;
    @Autowired private TransactionRepository transactions;

    private String create(String name, String type) throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/accounts")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"name\":\"%s\",\"type\":\"%s\"}"
                                                        .formatted(name, type)))
                        .andExpect(status().isCreated())
                        .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    @Test
    void createReturns201WithTheNewAccount() throws Exception {
        mvc.perform(
                        post("/api/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Fidelity 401k\",\"type\":\"RETIREMENT\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Fidelity 401k"))
                .andExpect(jsonPath("$.type").value("RETIREMENT"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void duplicateNameReturns409() throws Exception {
        create("Main", "BROKERAGE");

        mvc.perform(
                        post("/api/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Main\",\"type\":\"OTHER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("An account named Main already exists"));
    }

    @Test
    void blankNameOrMissingTypeReturns400() throws Exception {
        mvc.perform(
                        post("/api/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.errors[*].field")
                                .value(org.hamcrest.Matchers.hasItems("name", "type")));
    }

    @Test
    void listReturnsAccountsByName() throws Exception {
        create("Zeta", "OTHER");
        create("Alpha", "CRYPTO");

        mvc.perform(get("/api/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Alpha"))
                .andExpect(jsonPath("$[1].name").value("Zeta"));
    }

    @Test
    void updateChangesNameAndType() throws Exception {
        String id = create("Old", "OTHER");

        mvc.perform(
                        put("/api/accounts/" + id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"New\",\"type\":\"BROKERAGE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New"))
                .andExpect(jsonPath("$.type").value("BROKERAGE"));
    }

    @Test
    void updateKeepingTheSameNameIsNotAConflict() throws Exception {
        String id = create("Same", "OTHER");

        mvc.perform(
                        put("/api/accounts/" + id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Same\",\"type\":\"CRYPTO\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void updateToAnotherAccountsNameReturns409() throws Exception {
        create("Taken", "OTHER");
        String id = create("Mine", "OTHER");

        mvc.perform(
                        put("/api/accounts/" + id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Taken\",\"type\":\"OTHER\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void updateUnknownAccountReturns404() throws Exception {
        mvc.perform(
                        put("/api/accounts/" + UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"X\",\"type\":\"OTHER\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteEmptyAccountReturns204() throws Exception {
        String id = create("Empty", "OTHER");

        mvc.perform(delete("/api/accounts/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/accounts")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void deleteAccountWithTransactionsReturns409() throws Exception {
        String id = create("Busy", "BROKERAGE");
        InstrumentEntity etf =
                instruments.save(
                        new InstrumentEntity(
                                "VTI", "VTI", AssetType.ETF, PriceSource.YAHOO, "VTI"));
        transactions.save(
                TransactionEntity.trade(
                        UUID.fromString(id),
                        etf.getId(),
                        TxnType.BUY,
                        LocalDate.parse("2026-01-05"),
                        new BigDecimal("1"),
                        new BigDecimal("10"),
                        null));

        mvc.perform(delete("/api/accounts/" + id))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.detail")
                                .value("Account Busy has transactions and cannot be deleted"));
    }

    @Test
    void deleteUnknownAccountReturns404() throws Exception {
        mvc.perform(delete("/api/accounts/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }
}
