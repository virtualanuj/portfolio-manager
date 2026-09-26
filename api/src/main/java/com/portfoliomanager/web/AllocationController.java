package com.portfoliomanager.web;

import com.portfoliomanager.application.AllocationService;
import com.portfoliomanager.application.AllocationView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/allocation")
public class AllocationController {

    private final AllocationService service;

    public AllocationController(AllocationService service) {
        this.service = service;
    }

    @GetMapping
    public AllocationView allocation() {
        return service.allocation();
    }
}
