package org.bartram.myfeeder.controller;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.model.InterestProfile;
import org.bartram.myfeeder.service.InterestService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/interest")
@RequiredArgsConstructor
public class InterestController {
    private final InterestService interestService;

    @GetMapping("/profile")
    public InterestProfile getProfile() { return interestService.getProfile(); }

    @PutMapping("/profile")
    public InterestProfile updateProfile(@RequestBody ProfileUpdateRequest request) {
        return interestService.updateProfile(request.profileText());
    }
}
