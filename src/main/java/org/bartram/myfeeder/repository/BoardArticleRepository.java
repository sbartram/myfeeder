package org.bartram.myfeeder.repository;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.model.BoardArticle;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import java.util.List;

public interface BoardArticleRepository extends ListCrudRepository<BoardArticle, Long> {
    @Query("SELECT a.* FROM article a JOIN board_article ba ON a.id = ba.article_id WHERE ba.board_id = :boardId ORDER BY a.id DESC LIMIT :limit")
    List<Article> findArticlesByBoardId(Long boardId, int limit);

    @Query("SELECT a.* FROM article a JOIN board_article ba ON a.id = ba.article_id WHERE ba.board_id = :boardId AND a.id < :before ORDER BY a.id DESC LIMIT :limit")
    List<Article> findArticlesByBoardIdBefore(Long boardId, Long before, int limit);

    @Modifying
    @Query("DELETE FROM board_article WHERE board_id = :boardId AND article_id = :articleId")
    void removeArticleFromBoard(Long boardId, Long articleId);

    @Query("SELECT EXISTS(SELECT 1 FROM board_article WHERE board_id = :boardId AND article_id = :articleId)")
    boolean existsByBoardIdAndArticleId(Long boardId, Long articleId);
}
