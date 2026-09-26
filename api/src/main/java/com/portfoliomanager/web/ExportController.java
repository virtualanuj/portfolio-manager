package com.portfoliomanager.web;

import com.portfoliomanager.application.ExportService;
import com.portfoliomanager.application.FullExport;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Downloads: the transactions CSV can be re-imported to rebuild positions. */
@RestController
@RequestMapping("/api/export")
public class ExportController {

    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final ExportService service;
    private final Clock clock;

    public ExportController(ExportService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping("/transactions.csv")
    public ResponseEntity<String> transactions() {
        return download(CSV, "transactions-" + today() + ".csv", service.transactionsCsv());
    }

    @GetMapping("/snapshots.csv")
    public ResponseEntity<String> snapshots() {
        return download(CSV, "snapshots-" + today() + ".csv", service.snapshotsCsv());
    }

    @GetMapping("/all.json")
    public ResponseEntity<FullExport> all() {
        return download(
                MediaType.APPLICATION_JSON, "portfolio-" + today() + ".json", service.fullExport());
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static <T> ResponseEntity<T> download(MediaType type, String filename, T body) {
        return ResponseEntity.ok()
                .contentType(type)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(body);
    }
}
