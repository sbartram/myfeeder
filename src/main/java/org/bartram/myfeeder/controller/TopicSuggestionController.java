package org.bartram.myfeeder.controller;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.service.TopicSuggestionService;
import org.bartram.myfeeder.service.TopicSuggestions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Suggested topics (GAP-01). A separate controller, so the InterestController slice needs no new bean. */
@RestController
@RequestMapping("/api/interest")
@RequiredArgsConstructor
public class TopicSuggestionController {

    private final TopicSuggestionService service;

    @GetMapping("/suggestions")
    public TopicSuggestions suggestions() {
        return service.list();
    }
}
