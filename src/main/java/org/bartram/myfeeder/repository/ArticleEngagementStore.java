package org.bartram.myfeeder.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bartram.myfeeder.model.EngagementKind;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * The recorded engagement: one sticky row per article and {@link EngagementKind}. This store writes only
 * {@code article_engagement}, and the ranking SQL does not read it in this phase. No method here opens a
 * transaction on purpose: each statement autocommits on its own, so a failed engagement insert can never
 * abort the user's save that triggered it (D-07).
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ArticleEngagementStore {

    private final JdbcClient jdbc;

    /** Idempotent: true when a row was inserted, false when the (article, kind) row already existed. */
    public boolean record(long articleId, EngagementKind kind) {
        return jdbc.sql("INSERT INTO article_engagement (article_id, kind) VALUES (:articleId, :kind) "
                        + "ON CONFLICT (article_id, kind) DO NOTHING")
                .param("articleId", articleId)
                .param("kind", kind.name())
                .update() == 1;
    }

    /**
     * Best-effort capture for the save paths (D-07): never throws. A failure is logged at WARN with the kind,
     * the article id and the exception's simple class name only, never its message or cause.
     */
    public void recordQuietly(long articleId, EngagementKind kind) {
        try {
            record(articleId, kind);
        } catch (DataAccessException e) {
            log.warn("Engagement {} not recorded for article {}: {}", kind, articleId, e.getClass().getSimpleName());
        }
    }

    /** The article's engagement kinds in enum declaration order; empty when there are none. */
    public List<EngagementKind> kinds(long articleId) {
        return jdbc.sql("SELECT kind FROM article_engagement WHERE article_id = :articleId")
                .param("articleId", articleId)
                .query((rs, rowNum) -> EngagementKind.valueOf(rs.getString("kind")))
                .list()
                .stream()
                .sorted()
                .toList();
    }

    /** Forget: removes every engagement row of the article. Returns the rows deleted (0 when there were none). */
    public int deleteAll(long articleId) {
        return jdbc.sql("DELETE FROM article_engagement WHERE article_id = :articleId")
                .param("articleId", articleId)
                .update();
    }
}
