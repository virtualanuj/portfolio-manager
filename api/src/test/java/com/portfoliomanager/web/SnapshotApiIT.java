package com.portfoliomanager.web;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portfoliomanager.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class SnapshotApiIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    private void snapshot(String date, String value, String basis) {
        jdbc.update(
                "INSERT INTO snapshot (snap_date, total_value, total_cost_basis) VALUES (?::date, ?::numeric, ?::numeric)",
                date,
                value,
                basis);
    }

    @BeforeEach
    void seed() {
        snapshot("2026-03-01", "1000", "900");
        snapshot("2026-01-01", "500.5", "500");
        snapshot("2026-02-01", "750.25", "700");
    }

    @Test
    void returnsPointsInAscendingDateOrderWithDecimalStrings() throws Exception {
        mvc.perform(get("/api/snapshots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(
                        jsonPath("$[*].date", contains("2026-01-01", "2026-02-01", "2026-03-01")))
                .andExpect(jsonPath("$[0].totalValue").value("500.5000"))
                .andExpect(jsonPath("$[0].totalCostBasis").value("500.0000"));
    }

    @Test
    void filtersByInclusiveDateRange() throws Exception {
        mvc.perform(get("/api/snapshots").param("from", "2026-02-01").param("to", "2026-02-28"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].date").value("2026-02-01"));
        mvc.perform(get("/api/snapshots").param("from", "2026-02-01"))
                .andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/snapshots").param("to", "2026-01-31"))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void noSnapshotsReturnsAnEmptyList() throws Exception {
        jdbc.update("DELETE FROM snapshot");

        mvc.perform(get("/api/snapshots")).andExpect(jsonPath("$", hasSize(0)));
    }
}
