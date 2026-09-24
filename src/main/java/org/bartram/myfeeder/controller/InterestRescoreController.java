package org.bartram.myfeeder.controller;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.service.InterestRescoreService;
import org.bartram.myfeeder.service.RescoreCount;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interest")
@RequiredArgsConstructor
public class InterestRescoreController {

    private final InterestRescoreService service;

    @GetMapping("/rescore")
    public RescoreCount rescoreCount() {
        return service.count();
    }

    @PostMapping("/rescore")
    public RescoreCount rescore() {
        return service.rescore();
    }
}
