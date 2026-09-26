package com.portfoliomanager.web;

import com.portfoliomanager.application.NotFoundException;
import com.portfoliomanager.application.RefreshService;
import com.portfoliomanager.web.dto.RefreshRunResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/refresh")
public class RefreshController {

    private final RefreshService service;

    public RefreshController(RefreshService service) {
        this.service = service;
    }

    /** Starts a background refresh; 409 with the running run's id if one is already running. */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, UUID> start() {
        return Map.of("runId", service.start());
    }

    @GetMapping("/latest")
    public RefreshRunResponse latest() {
        return service.latest()
                .map(RefreshRunResponse::from)
                .orElseThrow(() -> new NotFoundException("No refresh has run yet"));
    }

    @GetMapping("/{id}")
    public RefreshRunResponse get(@PathVariable UUID id) {
        return RefreshRunResponse.from(service.find(id));
    }
}
