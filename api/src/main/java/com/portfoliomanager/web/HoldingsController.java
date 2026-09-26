package com.portfoliomanager.web;

import com.portfoliomanager.application.PortfolioService;
import com.portfoliomanager.domain.AssetType;
import com.portfoliomanager.web.dto.HoldingRow;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/holdings")
public class HoldingsController {

    private final PortfolioService portfolio;

    public HoldingsController(PortfolioService portfolio) {
        this.portfolio = portfolio;
    }

    /** {@code sort} is a column name, prefixed with {@code -} for descending. */
    @GetMapping
    public List<HoldingRow> holdings(
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) AssetType assetType,
            @RequestParam(required = false) String sort) {
        return portfolio.holdings(accountId, assetType, sort).stream()
                .map(HoldingRow::from)
                .toList();
    }
}
