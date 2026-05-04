package org.bartram.myfeeder.controller;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.service.FeedPollingService;
import org.bartram.myfeeder.service.FeedService;
import org.bartram.myfeeder.service.FolderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/feeds")
@RequiredArgsConstructor
public class FeedController {

    private final FeedService feedService;
    private final FeedPollingService feedPollingService;
    private final FolderService folderService;

    @GetMapping
    public List<Feed> listFeeds() {
        return feedService.findAll();
    }

    @PostMapping
    public ResponseEntity<Feed> subscribe(@RequestBody SubscribeRequest request) {
        Feed feed = feedService.subscribe(request.getUrl(), request.getFolderId());
        return ResponseEntity.status(HttpStatus.CREATED).body(feed);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Feed> getFeed(@PathVariable Long id) {
        return feedService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Feed> updateFeed(@PathVariable Long id, @RequestBody Feed updates) {
        return ResponseEntity.ok(feedService.update(id, updates));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFeed(@PathVariable Long id) {
        feedService.delete(id);
    }

    @PostMapping("/{id}/poll")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void triggerPoll(@PathVariable Long id) {
        feedPollingService.pollFeed(id);
    }

    @PutMapping("/{id}/folder")
    public Feed moveFeedToFolder(@PathVariable Long id, @RequestBody Map<String, Long> request) {
        return folderService.moveFeedToFolder(id, request.get("folderId"));
    }
}
