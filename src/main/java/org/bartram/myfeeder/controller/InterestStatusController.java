package org.bartram.myfeeder.controller;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.service.InterestStatus;
import org.bartram.myfeeder.service.InterestStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interest")
@RequiredArgsConstructor
public class InterestStatusController {

    private final InterestStatusService statusService;

    @GetMapping("/status")
    public InterestStatus status() {
        return statusService.status();
    }
}
