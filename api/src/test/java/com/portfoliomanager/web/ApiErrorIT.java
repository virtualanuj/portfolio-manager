package com.portfoliomanager.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portfoliomanager.AbstractIntegrationTest;
import com.portfoliomanager.application.ConflictException;
import com.portfoliomanager.application.NotFoundException;
import com.portfoliomanager.application.OversellException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Import(ApiErrorIT.ProbeController.class)
class ApiErrorIT extends AbstractIntegrationTest {

    private static final MediaType PROBLEM = MediaType.APPLICATION_PROBLEM_JSON;

    record Probe(@NotBlank String name, @NotNull BigDecimal quantity, LocalDate date) {}

    @RestController
    @RequestMapping("/api/probe")
    static class ProbeController {

        static final UUID TRANSACTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000042");

        @PostMapping("/echo")
        Probe echo(@Valid @RequestBody Probe probe) {
            return probe;
        }

        @GetMapping("/not-found")
        void notFound() {
            throw new NotFoundException("Account 7 does not exist");
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new ConflictException("An account named Main already exists");
        }

        @GetMapping("/oversell")
        void oversell() {
            throw new OversellException(
                    "Sell of 15 exceeds the 10 held on 2026-01-11", TRANSACTION_ID);
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("secret internal detail");
        }

        @GetMapping("/tiny")
        Probe tiny() {
            return new Probe("tiny", new BigDecimal("0.00000001"), LocalDate.parse("2026-09-26"));
        }
    }

    @Autowired private MockMvc mvc;

    @Test
    void validationFailureIsProblemJsonWithFieldErrors() throws Exception {
        mvc.perform(
                        post("/api/probe/echo")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"\",\"quantity\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[*].field", hasItem("name")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("quantity")))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    }

    @Test
    void malformedJsonIsProblemJson400() throws Exception {
        mvc.perform(
                        post("/api/probe/echo")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{nope"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM));
    }

    @Test
    void notFoundMapsTo404() throws Exception {
        mvc.perform(get("/api/probe/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.detail").value("Account 7 does not exist"));
    }

    @Test
    void conflictMapsTo409() throws Exception {
        mvc.perform(get("/api/probe/conflict"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.detail").value("An account named Main already exists"));
    }

    @Test
    void oversellMapsTo422NamingTheTransaction() throws Exception {
        mvc.perform(get("/api/probe/oversell"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(jsonPath("$.detail").value(containsString("exceeds the 10 held")))
                .andExpect(
                        jsonPath("$.transactionId")
                                .value(ProbeController.TRANSACTION_ID.toString()));
    }

    @Test
    void unexpectedErrorIsGeneric500WithoutInternals() throws Exception {
        mvc.perform(get("/api/probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM))
                .andExpect(
                        jsonPath("$.detail")
                                .value(org.hamcrest.Matchers.not(containsString("secret"))))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void decimalsDeserializeFromStringsAndSerializeAsPlainStrings() throws Exception {
        mvc.perform(
                        post("/api/probe/echo")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"a\",\"quantity\":\"12.50000000\",\"date\":\"2026-09-26\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value("12.50000000"))
                .andExpect(jsonPath("$.date").value("2026-09-26"));
    }

    @Test
    void tinyDecimalsAreNeverInScientificNotation() throws Exception {
        mvc.perform(get("/api/probe/tiny"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value("0.00000001"))
                .andExpect(jsonPath("$.date").value("2026-09-26"));
    }
}
