package com.portfoliomanager.web;

import com.portfoliomanager.application.PortfolioService;
import com.portfoliomanager.web.dto.DashboardResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final PortfolioService portfolio;

    public DashboardController(PortfolioService portfolio) {
        this.portfolio = portfolio;
    }

    @GetMapping
    public DashboardResponse dashboard() {
        // TODO(M3-T6): fill lastRefresh from the latest refresh run.
        return DashboardResponse.from(portfolio.totals(), null);
    }
}
