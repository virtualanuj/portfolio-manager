package com.portfoliomanager.web;

import com.portfoliomanager.application.TargetAllocationCommand;
import com.portfoliomanager.application.TargetAllocationService;
import com.portfoliomanager.domain.AssetType;
import com.portfoliomanager.web.dto.TargetAllocationDto;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/allocation/targets")
public class TargetAllocationController {

    private final TargetAllocationService service;

    public TargetAllocationController(TargetAllocationService service) {
        this.service = service;
    }

    @GetMapping
    public List<TargetAllocationDto> get() {
        return toDtos(service.current());
    }

    @PutMapping
    public List<TargetAllocationDto> replace(
            @RequestBody List<@Valid TargetAllocationDto> request) {
        return toDtos(
                service.replace(
                        request.stream()
                                .map(t -> new TargetAllocationCommand(t.assetType(), t.targetPct()))
                                .toList()));
    }

    private static List<TargetAllocationDto> toDtos(Map<AssetType, BigDecimal> targets) {
        return targets.entrySet().stream()
                .map(e -> new TargetAllocationDto(e.getKey(), e.getValue()))
                .toList();
    }
}
