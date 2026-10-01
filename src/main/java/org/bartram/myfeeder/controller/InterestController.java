package org.bartram.myfeeder.controller;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.model.InterestProfile;
import org.bartram.myfeeder.model.InterestTopic;
import org.bartram.myfeeder.service.ArticleFeedbackService;
import org.bartram.myfeeder.service.InterestService;
import org.bartram.myfeeder.service.TopicLearned;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/interest")
@RequiredArgsConstructor
public class InterestController {
    private final InterestService interestService;
    private final ArticleFeedbackService articleFeedbackService;

    @GetMapping("/profile")
    public InterestProfile getProfile() { return interestService.getProfile(); }

    @PutMapping("/profile")
    public InterestProfile updateProfile(@RequestBody ProfileUpdateRequest request) {
        return interestService.updateProfile(request.profileText());
    }

    @GetMapping("/topics")
    public List<InterestTopic> listTopics() { return interestService.listTopics(); }

    /** Each topic's base, learned and effective weight (FDBK-07); /topics/{id} maps only PUT and DELETE. */
    @GetMapping("/topics/learned")
    public List<TopicLearned> learnedTopics() { return articleFeedbackService.learnedTopics(); }

    @PostMapping("/topics")
    public ResponseEntity<InterestTopic> createTopic(@RequestBody TopicRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(interestService.createTopic(request.name(), request.description(), request.weight(),
                        request.sourceArticleId()));
    }

    @PutMapping("/topics/{id}")
    public InterestTopic updateTopic(@PathVariable Long id, @RequestBody TopicRequest request) {
        return interestService.updateTopic(id, request.name(), request.description(), request.weight());
    }

    @DeleteMapping("/topics/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTopic(@PathVariable Long id) { interestService.deleteTopic(id); }
}
