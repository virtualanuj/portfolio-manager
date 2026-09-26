package com.portfoliomanager.application;

import com.portfoliomanager.domain.Allocation;
import com.portfoliomanager.domain.AssetType;
import com.portfoliomanager.persistence.TargetAllocationEntity;
import com.portfoliomanager.persistence.TargetAllocationRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stores the user's target allocation by asset type. An empty set means no targets. */
@Service
public class TargetAllocationService {

    private static final int PERCENT_SCALE = 2;

    private final TargetAllocationRepository targets;

    public TargetAllocationService(TargetAllocationRepository targets) {
        this.targets = targets;
    }

    /** Targets in asset type order. */
    @Transactional(readOnly = true)
    public Map<AssetType, BigDecimal> current() {
        Map<AssetType, BigDecimal> result = new EnumMap<>(AssetType.class);
        targets.findAll().stream()
                .sorted(Comparator.comparing(TargetAllocationEntity::getAssetType))
                .forEach(t -> result.put(t.getAssetType(), t.getTargetPct()));
        return result;
    }

    /** Replaces all targets; invalid input changes nothing. */
    @Transactional
    public Map<AssetType, BigDecimal> replace(List<TargetAllocationCommand> commands) {
        Map<AssetType, BigDecimal> replacement = new EnumMap<>(AssetType.class);
        for (TargetAllocationCommand command : commands) {
            BigDecimal pct = command.targetPct().setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
            if (replacement.put(command.assetType(), pct) != null) {
                throw new ValidationException(
                        "targets",
                        "Asset type %s appears more than once".formatted(command.assetType()));
            }
        }
        try {
            Allocation.validateTargets(replacement);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("targets", e.getMessage());
        }

        targets.deleteAllInBatch();
        replacement.forEach((type, pct) -> targets.save(new TargetAllocationEntity(type, pct)));
        targets.flush();
        return current();
    }
}
