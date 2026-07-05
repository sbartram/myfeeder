package org.bartram.myfeeder.controller;
import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.model.Board;
import org.bartram.myfeeder.service.BoardService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/boards")
@RequiredArgsConstructor
public class BoardController {
    private final BoardService boardService;

    @GetMapping
    public List<Board> listBoards() { return boardService.findAll(); }

    @PostMapping("/by-name")
    public Board getOrCreateByName(@RequestBody BoardByNameRequest request) {
        return boardService.getOrCreateByName(request.name());
    }

    @PostMapping
    public ResponseEntity<Board> createBoard(@RequestBody CreateBoardRequest request) {
        Board board = boardService.create(request.name(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(board);
    }

    @PutMapping("/{id}")
    public Board updateBoard(@PathVariable Long id, @RequestBody UpdateBoardRequest request) {
        return boardService.update(id, request.name(), request.description());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBoard(@PathVariable Long id) { boardService.delete(id); }

    @GetMapping("/{id}/articles")
    public PaginatedResponse<Article> listBoardArticles(
            @PathVariable Long id,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) Long before) {
        return PaginatedResponse.of(boardService.findArticles(id, before, limit + 1), limit, Article::getId);
    }

    @PostMapping("/{id}/articles")
    @ResponseStatus(HttpStatus.CREATED)
    public void addArticleToBoard(@PathVariable Long id, @RequestBody AddArticleToBoardRequest request) {
        boardService.addArticle(id, request.articleId());
    }

    @DeleteMapping("/{boardId}/articles/{articleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeArticleFromBoard(@PathVariable Long boardId, @PathVariable Long articleId) {
        boardService.removeArticle(boardId, articleId);
    }
}
