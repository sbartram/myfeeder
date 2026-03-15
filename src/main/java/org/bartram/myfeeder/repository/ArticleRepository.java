package org.bartram.myfeeder.repository;

import org.bartram.myfeeder.model.Article;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ArticleRepository extends ListCrudRepository<Article, Long> {

    List<Article> findByFeedId(Long feedId);

    Optional<Article> findByFeedIdAndGuid(Long feedId, String guid);

    List<Article> findByStarredTrue();

    List<Article> findByReadFalse();

    @Modifying
    @Query("UPDATE article SET read = true WHERE id IN (:ids)")
    void markReadByIds(List<Long> ids);

    @Modifying
    @Query("UPDATE article SET read = true WHERE feed_id = :feedId")
    void markAllReadByFeedId(Long feedId);

    @Modifying
    @Query("UPDATE article SET content = NULL WHERE fetched_at < :cutoff AND content IS NOT NULL")
    void clearContentOlderThan(Instant cutoff);

    @Query("SELECT EXISTS(SELECT 1 FROM article WHERE feed_id = :feedId AND guid = :guid)")
    boolean existsByFeedIdAndGuid(Long feedId, String guid);
}
