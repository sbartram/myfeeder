package org.bartram.myfeeder.controller;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.integration.RaindropService;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.service.ArticleService;
import org.bartram.myfeeder.service.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
public class ArticleController {

    /** Upper bound on page size; clamps caller-supplied {@code limit} to bound reads and JSON payloads. */
    private static final int MAX_LIMIT = 100;

    private final ArticleService articleService;
    private final RaindropService raindropService;

    @GetMapping
    public PaginatedResponse<Article> listArticles(
            @RequestParam(required = false) Long feedId,
            @RequestParam(required = false) Boolean read,
            @RequestParam(required = false) Boolean starred,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) Long before,
            @RequestParam(defaultValue = "desc") String sort) {
        boolean ascending = "asc".equalsIgnoreCase(sort);
        // Clamp to [1, MAX_LIMIT] so an out-of-range limit is well-defined rather than an error,
        // and so limit + 1 (the pagination look-ahead) can never overflow.
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        List<Article> fetched = articleService.findFiltered(feedId, read, starred, before, safeLimit + 1, ascending);
        return PaginatedResponse.of(fetched, safeLimit, Article::getId);
    }

    @GetMapping("/counts")
    public Map<Long, Long> unreadCounts() {
        return articleService.countUnreadByFeed();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Article> getArticle(@PathVariable Long id) {
        return articleService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Article> updateState(@PathVariable Long id, @RequestBody ArticleStateRequest request) {
        Article updated = articleService.updateState(id, request.getRead(), request.getStarred());
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/mark-read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@RequestBody MarkReadRequest request) {
        articleService.markRead(request.getArticleIds(), request.getFeedId(), request.getOlderThanDays());
    }

    @PostMapping("/{id}/raindrop")
    public ResponseEntity<Void> saveToRaindrop(@PathVariable Long id) {
        Article article = articleService.findById(id)
                .orElseThrow(() -> new NotFoundException("Article not found: " + id));
        raindropService.saveToRaindrop(article);
        return ResponseEntity.ok().build();
    }
}
