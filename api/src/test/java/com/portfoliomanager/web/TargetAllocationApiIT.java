package com.portfoliomanager.web;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.portfoliomanager.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

class TargetAllocationApiIT extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;

    private ResultActions putTargets(String json) throws Exception {
        return mvc.perform(
                put("/api/allocation/targets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json));
    }

    @Test
    void targetsSummingToOneHundredAreStoredAndReturned() throws Exception {
        putTargets(
                        """
                        [{"assetType":"ETF","targetPct":"60"},{"assetType":"CRYPTO","targetPct":"40.00"}]""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mvc.perform(get("/api/allocation/targets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].assetType").value("ETF"))
                .andExpect(jsonPath("$[0].targetPct").value("60.00"))
                .andExpect(jsonPath("$[1].assetType").value("CRYPTO"))
                .andExpect(jsonPath("$[1].targetPct").value("40.00"));
    }

    @Test
    void sumOtherThanOneHundredReturns400() throws Exception {
        putTargets(
                        """
                        [{"assetType":"ETF","targetPct":"60"},{"assetType":"CRYPTO","targetPct":"30"}]""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("targets"))
                .andExpect(
                        jsonPath("$.errors[0].message")
                                .value(org.hamcrest.Matchers.containsString("100.00")));
    }

    @Test
    void negativeOrOversizedTargetReturns400() throws Exception {
        putTargets(
                        """
                        [{"assetType":"ETF","targetPct":"110"},{"assetType":"CRYPTO","targetPct":"-10"}]""")
                .andExpect(status().isBadRequest());
        putTargets(
                        """
                [{"assetType":"ETF","targetPct":"100.01"}]""")
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateAssetTypeReturns400() throws Exception {
        putTargets(
                        """
                        [{"assetType":"ETF","targetPct":"50"},{"assetType":"ETF","targetPct":"50"}]""")
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownAssetTypeOrMissingPercentReturns400() throws Exception {
        putTargets(
                        """
                [{"assetType":"BONDS","targetPct":"100"}]""")
                .andExpect(status().isBadRequest());
        putTargets(
                        """
                [{"assetType":"ETF"}]""")
                .andExpect(status().isBadRequest());
    }

    @Test
    void emptyListClearsTargets() throws Exception {
        putTargets(
                        """
                [{"assetType":"ETF","targetPct":"100"}]""")
                .andExpect(status().isOk());

        putTargets("[]").andExpect(status().isOk());

        mvc.perform(get("/api/allocation/targets")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void putReplacesThePreviousSet() throws Exception {
        putTargets(
                        """
                        [{"assetType":"ETF","targetPct":"60"},{"assetType":"CRYPTO","targetPct":"40"}]""")
                .andExpect(status().isOk());

        putTargets(
                        """
                [{"assetType":"STOCK","targetPct":"100"}]""")
                .andExpect(status().isOk());

        mvc.perform(get("/api/allocation/targets"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].assetType").value("STOCK"));
    }

    @Test
    void invalidPutLeavesStoredTargetsUntouched() throws Exception {
        putTargets(
                        """
                [{"assetType":"ETF","targetPct":"100"}]""")
                .andExpect(status().isOk());

        putTargets(
                        """
                [{"assetType":"ETF","targetPct":"70"}]""")
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/allocation/targets"))
                .andExpect(jsonPath("$[0].targetPct").value("100.00"));
    }
}
