package org.bartram.myfeeder.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Gap discovery candidates (GAP-01, D-08): engaged, SCORED, unvoted, undismissed articles whose best noul
 * is below the near-miss threshold. This is the only class that reads {@code topic_suggestion_dismissal};
 * the ranking SQL in {@link InterestScoreQueries} never does, and the V7 migration test guards that. This
 * class only reads stored rows and never calls Jev.
 *
 * <p>Each engagement kind keeps its first {@code created_at} ({@code ON CONFLICT DO NOTHING}), so the
 * latest engagement is the most recent first engagement of any kind: re-opening an old article does not
 * bring it back into the window, but a new kind (a star, say) does.
 */
@Repository
@RequiredArgsConstructor
public class TopicSuggestionStore {

    /**
     * One row per article; {@code engaged_at} is its latest engagement. The best noul is the maximum over
     * every topic row whatever the topic's weight sign (D-10), and 0 when there are none (D-11).
     */
    static final String CANDIDATES = "SELECT a.id, a.title, f.title AS feed_title, MAX(g.created_at) AS engaged_at "
            + "FROM article_engagement g "
            + "JOIN article a ON a.id = g.article_id "
            + "JOIN feed f ON f.id = a.feed_id "
            + "JOIN article_score s ON s.article_id = a.id AND s.status = 'SCORED' "
            + "WHERE NOT EXISTS (SELECT 1 FROM article_feedback fb WHERE fb.article_id = a.id) "
            + "AND NOT EXISTS (SELECT 1 FROM topic_suggestion_dismissal d WHERE d.article_id = a.id) "
            + "AND COALESCE((SELECT MAX(ts.noul) FROM article_topic_score ts WHERE ts.article_id = a.id), 0) "
            + "< CAST(:nearMiss AS float8) "
            + "GROUP BY a.id, a.title, f.title "
            + "HAVING MAX(g.created_at) > :cutoff";

    private final JdbcClient jdbc;

    /** One candidate article with its feed's title and its latest engagement time. */
    public record Candidate(long articleId, String title, String feedTitle, Instant engagedAt) {
    }

    /** Every candidate whose latest engagement is strictly after {@code cutoff}, unordered. */
    public List<Candidate> candidates(Instant cutoff, double nearMiss) {
        return jdbc.sql(CANDIDATES)
                .param("cutoff", Timestamp.from(cutoff))
                .param("nearMiss", nearMiss)
                .query((rs, rowNum) -> new Candidate(rs.getLong("id"), rs.getString("title"),
                        rs.getString("feed_title"), rs.getTimestamp("engaged_at").toInstant()))
                .list();
    }
}
