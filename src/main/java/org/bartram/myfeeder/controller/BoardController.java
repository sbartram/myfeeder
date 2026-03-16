package org.bartram.myfeeder.controller;
import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.model.Board;
import org.bartram.myfeeder.service.BoardService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/boards")
@RequiredArgsConstructor
public class BoardController {
    private final BoardService boardService;

    @GetMapping
    public List<Board> listBoards() { return boardService.findAll(); }

    @PostMapping("/by-name")
    public Board getOrCreateByName(@RequestBody Map<String, String> body) {
        return boardService.getOrCreateByName(body.get("name"));
    }

    @PostMapping
    public ResponseEntity<Board> createBoard(@RequestBody Map<String, String> request) {
        Board board = boardService.create(request.get("name"), request.get("description"));
        return ResponseEntity.status(HttpStatus.CREATED).body(board);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Board> updateBoard(@PathVariable Long id, @RequestBody Map<String, String> request) {
        return boardService.findById(id)
                .map(b -> ResponseEntity.ok(boardService.update(id, request.get("name"), request.get("description"))))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBoard(@PathVariable Long id) { boardService.delete(id); }

    @GetMapping("/{id}/articles")
    public PaginatedResponse<Article> listBoardArticles(
            @PathVariable Long id,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) Long before) {
        List<Article> articles = boardService.findArticles(id, before, limit + 1);
        boolean hasMore = articles.size() > limit;
        if (hasMore) articles = articles.subList(0, limit);
        Long nextCursor = hasMore ? articles.getLast().getId() : null;
        return new PaginatedResponse<>(articles, nextCursor);
    }

    @PostMapping("/{id}/articles")
    @ResponseStatus(HttpStatus.CREATED)
    public void addArticleToBoard(@PathVariable Long id, @RequestBody Map<String, Long> request) {
        boardService.addArticle(id, request.get("articleId"));
    }

    @DeleteMapping("/{boardId}/articles/{articleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeArticleFromBoard(@PathVariable Long boardId, @PathVariable Long articleId) {
        boardService.removeArticle(boardId, articleId);
    }
}
