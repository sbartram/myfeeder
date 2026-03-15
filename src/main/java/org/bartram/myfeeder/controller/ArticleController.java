package org.bartram.myfeeder.controller;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.integration.RaindropService;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.service.ArticleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;
    private final RaindropService raindropService;

    @GetMapping
    public PaginatedResponse<Article> listArticles(
            @RequestParam(required = false) Long feedId,
            @RequestParam(required = false) Boolean read,
            @RequestParam(required = false) Boolean starred,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) Long before) {
        List<Article> articles = articleService.findFiltered(feedId, read, starred, before, limit + 1);
        boolean hasMore = articles.size() > limit;
        if (hasMore) {
            articles = articles.subList(0, limit);
        }
        Long nextCursor = hasMore ? articles.getLast().getId() : null;
        return new PaginatedResponse<>(articles, nextCursor);
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
        articleService.markRead(request.getArticleIds(), request.getFeedId());
    }

    @PostMapping("/{id}/raindrop")
    public ResponseEntity<Void> saveToRaindrop(@PathVariable Long id) {
        Article article = articleService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Article not found: " + id));
        raindropService.saveToRaindrop(article);
        return ResponseEntity.ok().build();
    }
}
