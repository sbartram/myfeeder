package org.bartram.myfeeder.controller;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.service.InterestRescoreService;
import org.bartram.myfeeder.service.RescoreCount;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /** JSON-only, so a cross-site "simple" POST (no body, form or text) is refused with 415 before any reset. */
    @PostMapping(value = "/rescore", consumes = MediaType.APPLICATION_JSON_VALUE)
    public RescoreCount rescore(@RequestBody RescoreRequest request) {
        if (!request.confirm()) {
            throw new IllegalArgumentException("confirm must be true");
        }
        return service.rescore();
    }
}
