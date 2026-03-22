package org.bartram.myfeeder.repository;

import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.model.UnreadCount;
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
    @Query("UPDATE article SET read = true WHERE feed_id = :feedId AND published_at < :cutoff AND read = false")
    void markReadByFeedIdOlderThan(Long feedId, Instant cutoff);

    @Modifying
    @Query("UPDATE article SET content = NULL WHERE fetched_at < :cutoff AND content IS NOT NULL")
    void clearContentOlderThan(Instant cutoff);

    @Query("SELECT EXISTS(SELECT 1 FROM article WHERE feed_id = :feedId AND guid = :guid)")
    boolean existsByFeedIdAndGuid(Long feedId, String guid);

    @Query("SELECT * FROM article WHERE (:feedId IS NULL OR feed_id = :feedId) AND (:read IS NULL OR \"read\" = :read) AND (:starred IS NULL OR starred = :starred) ORDER BY id DESC LIMIT :limit")
    List<Article> findFiltered(Long feedId, Boolean read, Boolean starred, int limit);

    @Query("SELECT * FROM article WHERE (:feedId IS NULL OR feed_id = :feedId) AND (:read IS NULL OR \"read\" = :read) AND (:starred IS NULL OR starred = :starred) AND id < :before ORDER BY id DESC LIMIT :limit")
    List<Article> findFilteredBefore(Long feedId, Boolean read, Boolean starred, Long before, int limit);

    @Query("SELECT feed_id, COUNT(*) AS count FROM article WHERE \"read\" = false GROUP BY feed_id")
    List<UnreadCount> countUnreadByFeed();
}
