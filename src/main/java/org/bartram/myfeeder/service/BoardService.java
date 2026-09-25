package org.bartram.myfeeder.service;
import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.model.Board;
import org.bartram.myfeeder.model.BoardArticle;
import org.bartram.myfeeder.repository.BoardArticleRepository;
import org.bartram.myfeeder.repository.BoardRepository;
import org.bartram.myfeeder.repository.InterestScoreQueries;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BoardService {
    private final BoardRepository boardRepository;
    private final BoardArticleRepository boardArticleRepository;
    private final InterestScoreQueries interestScoreQueries;

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
                .orElseThrow(() -> new NotFoundException("Board not found: " + id));
        board.setName(name);
        if (description != null) board.setDescription(description);
        return boardRepository.save(board);
    }

    public void delete(Long id) { boardRepository.deleteById(id); }

    public List<Article> findArticles(Long boardId, Long before, int limit) {
        if (before != null) return withScores(boardArticleRepository.findArticlesByBoardIdBefore(boardId, before, limit));
        return withScores(boardArticleRepository.findArticlesByBoardId(boardId, limit));
    }

    /** Sets each article's interest badge (D-18); same list, same order; an empty list runs no query. */
    private List<Article> withScores(List<Article> articles) {
        if (articles.isEmpty()) return articles;
        Map<Long, Integer> scores = interestScoreQueries.displayScores(
                articles.stream().map(Article::getId).toList());
        articles.forEach(a -> a.setInterestScore(scores.get(a.getId())));
        return articles;
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
