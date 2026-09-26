package com.portfoliomanager.web;

import com.portfoliomanager.application.InstrumentService;
import com.portfoliomanager.web.dto.InstrumentRequest;
import com.portfoliomanager.web.dto.InstrumentResponse;
import com.portfoliomanager.web.dto.ManualPriceRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/instruments")
public class InstrumentController {

    private final InstrumentService service;

    public InstrumentController(InstrumentService service) {
        this.service = service;
    }

    @GetMapping
    public List<InstrumentResponse> list() {
        return service.list().stream().map(InstrumentResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InstrumentResponse create(@Valid @RequestBody InstrumentRequest request) {
        return InstrumentResponse.from(service.create(request.toCommand()));
    }

    @PutMapping("/{id}")
    public InstrumentResponse update(
            @PathVariable UUID id, @Valid @RequestBody InstrumentRequest request) {
        return InstrumentResponse.from(service.update(id, request.toCommand()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @PutMapping("/{id}/manual-price")
    public InstrumentResponse setManualPrice(
            @PathVariable UUID id, @Valid @RequestBody ManualPriceRequest request) {
        return InstrumentResponse.from(service.setManualPrice(id, request.price(), request.asOf()));
    }

    @DeleteMapping("/{id}/manual-price")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearManualPrice(@PathVariable UUID id) {
        service.clearManualPrice(id);
    }
}
