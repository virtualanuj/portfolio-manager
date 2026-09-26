package com.portfoliomanager.application;

import com.portfoliomanager.domain.Allocation;
import com.portfoliomanager.domain.AllocationRow;
import com.portfoliomanager.domain.AssetType;
import com.portfoliomanager.domain.PositionValue;
import com.portfoliomanager.domain.Valuation;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Actual allocation by asset type, compared with the user's targets when they set any. */
@Service
public class AllocationService {

    private final PortfolioService portfolio;
    private final TargetAllocationService targetService;

    public AllocationService(PortfolioService portfolio, TargetAllocationService targetService) {
        this.portfolio = portfolio;
        this.targetService = targetService;
    }

    @Transactional(readOnly = true)
    public AllocationView allocation() {
        List<PositionValue> positions =
                portfolio.allHoldings().stream().map(HoldingView::position).toList();
        Map<AssetType, BigDecimal> targets = targetService.current();
        List<AllocationRow> rows = Allocation.compute(positions, targets);
        return new AllocationView(
                Valuation.totals(positions).totalValue(), !targets.isEmpty(), rows);
    }
}
