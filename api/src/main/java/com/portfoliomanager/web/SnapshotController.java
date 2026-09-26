package com.portfoliomanager.web;

import com.portfoliomanager.application.SnapshotPoint;
import com.portfoliomanager.application.SnapshotService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/snapshots")
public class SnapshotController {

    private static final LocalDate EARLIEST = LocalDate.of(1900, 1, 1);
    private static final LocalDate LATEST = LocalDate.of(9999, 12, 31);

    private final SnapshotService service;

    public SnapshotController(SnapshotService service) {
        this.service = service;
    }

    /** Only days that have a snapshot appear; gaps are real and never filled in. */
    @GetMapping
    public List<SnapshotPoint> history(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to) {
        return service.history(from == null ? EARLIEST : from, to == null ? LATEST : to);
    }
}
