package org.bartram.myfeeder.service;
import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.model.Board;
import org.bartram.myfeeder.model.BoardArticle;
import org.bartram.myfeeder.repository.BoardArticleRepository;
import org.bartram.myfeeder.repository.BoardRepository;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BoardService {
    private final BoardRepository boardRepository;
    private final BoardArticleRepository boardArticleRepository;

    public List<Board> findAll() { return boardRepository.findAll(); }
    public Optional<Board> findById(Long id) { return boardRepository.findById(id); }

    public Board create(String name, String description) {
        Board board = new Board();
        board.setName(name);
        board.setDescription(description);
        board.setCreatedAt(Instant.now());
        return boardRepository.save(board);
    }

    public Board getOrCreateByName(String name) {
        return boardRepository.findByNameIgnoreCase(name)
            .orElseGet(() -> create(name, null));
    }

    public Board update(Long id, String name, String description) {
        Board board = boardRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Board not found: " + id));
        board.setName(name);
        if (description != null) board.setDescription(description);
        return boardRepository.save(board);
    }

    public void delete(Long id) { boardRepository.deleteById(id); }

    public List<Article> findArticles(Long boardId, Long before, int limit) {
        if (before != null) return boardArticleRepository.findArticlesByBoardIdBefore(boardId, before, limit);
        return boardArticleRepository.findArticlesByBoardId(boardId, limit);
    }

    public void addArticle(Long boardId, Long articleId) {
        if (boardArticleRepository.existsByBoardIdAndArticleId(boardId, articleId)) return;
        BoardArticle ba = new BoardArticle();
        ba.setBoardId(boardId);
        ba.setArticleId(articleId);
        ba.setAddedAt(Instant.now());
        boardArticleRepository.save(ba);
    }

    public void removeArticle(Long boardId, Long articleId) {
        boardArticleRepository.removeArticleFromBoard(boardId, articleId);
    }
}
