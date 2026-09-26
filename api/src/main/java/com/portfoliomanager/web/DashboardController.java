package com.portfoliomanager.web;

import com.portfoliomanager.application.PortfolioService;
import com.portfoliomanager.application.RefreshService;
import com.portfoliomanager.web.dto.DashboardResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final PortfolioService portfolio;
    private final RefreshService refreshes;

    public DashboardController(PortfolioService portfolio, RefreshService refreshes) {
        this.portfolio = portfolio;
        this.refreshes = refreshes;
    }

    @GetMapping
    public DashboardResponse dashboard() {
        DashboardResponse.LastRefresh lastRefresh =
                refreshes
                        .latest()
                        .map(
                                run ->
                                        new DashboardResponse.LastRefresh(
                                                run.id(), run.status().name(), run.finishedAt()))
                        .orElse(null);
        return DashboardResponse.from(portfolio.totals(), lastRefresh);
    }
}
