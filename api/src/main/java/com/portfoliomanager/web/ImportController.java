package com.portfoliomanager.web;

import com.portfoliomanager.application.ImportService;
import com.portfoliomanager.application.ImportViews.CommitResult;
import com.portfoliomanager.application.ImportViews.Preview;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/imports")
public class ImportController {

    private final ImportService service;

    public ImportController(ImportService service) {
        this.service = service;
    }

    /** Validates and stages the uploaded CSV; nothing is written to real tables. */
    @PostMapping(consumes = "multipart/form-data")
    public Preview upload(@RequestParam("file") MultipartFile file) throws IOException {
        try (InputStream content = file.getInputStream()) {
            return service.stage(file.getOriginalFilename(), content);
        }
    }

    @GetMapping("/{id}")
    public Preview get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/commit")
    public CommitResult commit(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean includeDuplicates) {
        return service.commit(id, includeDuplicates);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discard(@PathVariable UUID id) {
        service.discard(id);
    }
}
