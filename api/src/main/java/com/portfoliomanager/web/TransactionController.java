package com.portfoliomanager.web;

import com.portfoliomanager.application.TransactionFilter;
import com.portfoliomanager.application.TransactionService;
import com.portfoliomanager.application.TransactionView;
import com.portfoliomanager.domain.TxnType;
import com.portfoliomanager.web.dto.TransactionPage;
import com.portfoliomanager.web.dto.TransactionRequest;
import com.portfoliomanager.web.dto.TransactionResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService service;

    public TransactionController(TransactionService service) {
        this.service = service;
    }

    @GetMapping
    public TransactionPage list(
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) UUID instrumentId,
            @RequestParam(required = false) TxnType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<TransactionView> result =
                service.list(
                        new TransactionFilter(accountId, instrumentId, type, from, to), page, size);
        return new TransactionPage(
                result.map(TransactionResponse::from).getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse create(@Valid @RequestBody TransactionRequest request) {
        return TransactionResponse.from(service.create(request.toCommand()));
    }

    @PutMapping("/{id}")
    public TransactionResponse update(
            @PathVariable UUID id, @Valid @RequestBody TransactionRequest request) {
        return TransactionResponse.from(service.update(id, request.toCommand()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
