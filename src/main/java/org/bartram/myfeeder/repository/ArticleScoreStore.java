package org.bartram.myfeeder.repository;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.model.Article;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The database side of the scoring queue. The database is the queue's source of truth:
 * an article needs scoring when it has no {@code article_score} row, or a FAILED row with
 * fewer than {@link #MAX_ATTEMPTS} attempts, and it is eligible.
 *
 * <p>Every query is built from the single {@link #ELIGIBLE} predicate, so the sweep, the
 * dispatch recheck, the status counts and Re-score always agree on which articles are in
 * scope (D-02, D-12).
 *
 * <p>This store writes and deletes only {@code article_score} and {@code article_topic_score}
 * rows. It never changes an article's read or starred state and never deletes articles.
 */
@Repository
@RequiredArgsConstructor
public class ArticleScoreStore {

    /** SCOR-07: a FAILED article is retried until it has this many attempts. */
    public static final int MAX_ATTEMPTS = 3;

    /** The one eligibility predicate (SCOR-04): unread and inside the window. Alias {@code a} = article. */
    static final String ELIGIBLE =
            "a.\"read\" = false AND COALESCE(a.published_at, a.fetched_at) > :cutoff";

    /** No score row yet, or a FAILED row still being retried. Alias {@code s} = article_score. */
    static final String NEEDS_SCORING =
            "(s.article_id IS NULL OR (s.status = 'FAILED' AND s.attempts < " + MAX_ATTEMPTS + "))";

    /** Newest first, with the id as a stable tiebreak for equal timestamps. */
    static final String NEWEST_FIRST = "ORDER BY COALESCE(a.published_at, a.fetched_at) DESC, a.id DESC";

    /**
     * Re-score scope (INT-05, D-03): eligible articles with a SCORED or FAILED row, exhausted FAILED
     * included, SKIPPED excluded. Alias {@code s} = article_score.
     */
    static final String RESCORE_SCOPE = ELIGIBLE + " AND s.status IN ('SCORED', 'FAILED')";

    private static final String NEEDING_SCORING_FROM =
            "FROM article a LEFT JOIN article_score s ON s.article_id = a.id WHERE "
                    + ELIGIBLE + " AND " + NEEDS_SCORING;

    private final JdbcClient jdbc;

    /** An eligible article that needs scoring, with its feed's title. */
    public record Candidate(Article article, String feedTitle) {
    }

    /** One topic's judgment of an article, stamped with the topic version it was judged against. */
    public record TopicNoul(long topicId, double noul, int topicVersion) {
    }

    /** A successful score. The profile fields are null when no profile question was sent. */
    public record ScoredRow(Double profileScore, Integer profileMaxLevel, Double profileConfidence,
                            Integer profileVersion, String model, String requestId, List<TopicNoul> topics) {
    }

    /**
     * Status counts over eligible articles only (D-11, D-12). {@code eligibleUnscored} includes FAILED
     * rows still being retried; {@code failed} is exhausted FAILED rows only.
     */
    public record ScoreCounts(long eligibleUnscored, long failed) {
    }

    /** Ids of eligible articles that need scoring, newest first, at most {@code limit}. */
    public List<Long> findNeedingScoring(Instant cutoff, int limit) {
        return jdbc.sql("SELECT a.id " + NEEDING_SCORING_FROM + " " + NEWEST_FIRST + " LIMIT :limit")
                .param("cutoff", Timestamp.from(cutoff))
                .param("limit", limit)
                .query(Long.class)
                .list();
    }

    /**
     * Dispatch recheck: the article with its feed title, or empty unless it is eligible AND still
     * needs scoring.
     */
    public Optional<Candidate> loadCandidate(long articleId, Instant cutoff) {
        return jdbc.sql("SELECT a.id, a.feed_id, a.guid, a.title, a.summary, a.content, a.published_at, "
                        + "a.fetched_at, f.title AS feed_title "
                        + "FROM article a JOIN feed f ON f.id = a.feed_id "
                        + "LEFT JOIN article_score s ON s.article_id = a.id "
                        + "WHERE a.id = :articleId AND " + ELIGIBLE + " AND " + NEEDS_SCORING)
                .param("articleId", articleId)
                .param("cutoff", Timestamp.from(cutoff))
                .query((rs, rowNum) -> new Candidate(mapArticle(rs), rs.getString("feed_title")))
                .optional();
    }

    /**
     * Stores a successful score and its topic nouls in one transaction, parent row first.
     * SCORED is write-once: it overwrites a FAILED row (keeping its attempts, clearing last_error)
     * but never a SCORED or SKIPPED row. A missing article is a silent no-op, and a topic deleted
     * mid-call is left out instead of failing the write.
     *
     * @return true when the SCORED row was written
     */
    @Transactional
    public boolean writeScored(long articleId, ScoredRow row) {
        int written = jdbc.sql("INSERT INTO article_score (article_id, status, profile_score, profile_max_level, "
                        + "profile_confidence, profile_version, model, request_id) "
                        + "SELECT a.id, 'SCORED', :profileScore, :profileMaxLevel, :profileConfidence, "
                        + ":profileVersion, :model, :requestId "
                        + "FROM article a WHERE a.id = :articleId "
                        + "ON CONFLICT (article_id) DO UPDATE SET status = 'SCORED', "
                        + "profile_score = EXCLUDED.profile_score, profile_max_level = EXCLUDED.profile_max_level, "
                        + "profile_confidence = EXCLUDED.profile_confidence, "
                        + "profile_version = EXCLUDED.profile_version, model = EXCLUDED.model, "
                        + "request_id = EXCLUDED.request_id, last_error = NULL, scored_at = NOW() "
                        + "WHERE article_score.status = 'FAILED'")
                .param("articleId", articleId)
                .param("profileScore", row.profileScore())
                .param("profileMaxLevel", row.profileMaxLevel())
                .param("profileConfidence", row.profileConfidence())
                .param("profileVersion", row.profileVersion())
                .param("model", row.model())
                .param("requestId", row.requestId())
                .update();
        if (written != 1) {
            return false;
        }
        for (TopicNoul topic : row.topics()) {
            jdbc.sql("INSERT INTO article_topic_score (article_id, topic_id, noul, topic_version) "
                            + "SELECT :articleId, t.id, :noul, :topicVersion FROM interest_topic t "
                            + "WHERE t.id = :topicId ON CONFLICT (article_id, topic_id) DO NOTHING")
                    .param("articleId", articleId)
                    .param("topicId", topic.topicId())
                    .param("noul", topic.noul())
                    .param("topicVersion", topic.topicVersion())
                    .update();
        }
        return true;
    }

    /**
     * Records a failed attempt (SCOR-07): inserts a FAILED row with attempts 1, then increments
     * attempts only while the row is still FAILED. It never overwrites a SCORED or SKIPPED row, and
     * a missing article is a silent no-op.
     *
     * @param lastError fixed text only (exception class name, HTTP status, request id), never an
     *                  exception message or a response body
     */
    public void writeFailed(long articleId, String lastError) {
        jdbc.sql("INSERT INTO article_score (article_id, status, attempts, last_error) "
                        + "SELECT a.id, 'FAILED', 1, :lastError FROM article a WHERE a.id = :articleId "
                        + "ON CONFLICT (article_id) DO UPDATE SET attempts = article_score.attempts + 1, "
                        + "last_error = EXCLUDED.last_error, scored_at = NOW() "
                        + "WHERE article_score.status = 'FAILED'")
                .param("articleId", articleId)
                .param("lastError", lastError)
                .update();
    }

    /**
     * Marks an article as never to be scored (SCOR-08, e.g. no GUID or no text). SKIPPED is terminal
     * and the first writer wins; a missing article is a silent no-op.
     */
    public void writeSkipped(long articleId, String reason) {
        jdbc.sql("INSERT INTO article_score (article_id, status, last_error) "
                        + "SELECT a.id, 'SKIPPED', :reason FROM article a WHERE a.id = :articleId "
                        + "ON CONFLICT (article_id) DO NOTHING")
                .param("articleId", articleId)
                .param("reason", reason)
                .update();
    }

    /**
     * The subset of {@code ids} that are eligible and need scoring, newest first. This is the
     * enqueue-time filter for freshly ingested articles, so an old back-catalogue flood never
     * enters the queue.
     */
    public List<Long> filterNeedingScoring(Collection<Long> ids, Instant cutoff) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT a.id " + NEEDING_SCORING_FROM + " AND a.id IN (:ids) " + NEWEST_FIRST)
                .param("cutoff", Timestamp.from(cutoff))
                .param("ids", ids)
                .query(Long.class)
                .list();
    }

    /**
     * Status counts over eligible articles only (JEV-05, D-11, D-12), from one statement so the two
     * numbers are a consistent snapshot of the same moment.
     */
    public ScoreCounts counts(Instant cutoff) {
        return jdbc.sql("SELECT COUNT(*) FILTER (WHERE " + NEEDS_SCORING + ") AS eligible_unscored, "
                        + "COUNT(*) FILTER (WHERE s.status = 'FAILED' AND s.attempts >= " + MAX_ATTEMPTS
                        + ") AS failed "
                        + "FROM article a LEFT JOIN article_score s ON s.article_id = a.id WHERE " + ELIGIBLE)
                .param("cutoff", Timestamp.from(cutoff))
                .query((rs, rowNum) -> new ScoreCounts(rs.getLong("eligible_unscored"), rs.getLong("failed")))
                .single();
    }

    /**
     * How many score rows Re-score would reset. Shares {@link #RESCORE_SCOPE} with
     * {@link #deleteRescoreScope(Instant)} by construction, which is what makes the confirmation
     * count exact (D-02).
     */
    public long countRescoreScope(Instant cutoff) {
        return jdbc.sql("SELECT COUNT(*) FROM article_score s JOIN article a ON a.id = s.article_id WHERE "
                        + RESCORE_SCOPE)
                .param("cutoff", Timestamp.from(cutoff))
                .query(Long.class)
                .single();
    }

    /**
     * Resets the Re-score scope in one atomic statement; the V6 cascade removes the topic rows.
     * Shares {@link #RESCORE_SCOPE} with {@link #countRescoreScope(Instant)} by construction, so the
     * count shown is exactly the rows deleted (D-02).
     *
     * @return the number of score rows deleted
     */
    public int deleteRescoreScope(Instant cutoff) {
        return jdbc.sql("DELETE FROM article_score s USING article a WHERE a.id = s.article_id AND "
                        + RESCORE_SCOPE)
                .param("cutoff", Timestamp.from(cutoff))
                .update();
    }

    private static Article mapArticle(ResultSet rs) throws SQLException {
        Article article = new Article();
        article.setId(rs.getLong("id"));
        article.setFeedId(rs.getLong("feed_id"));
        article.setGuid(rs.getString("guid"));
        article.setTitle(rs.getString("title"));
        article.setSummary(rs.getString("summary"));
        article.setContent(rs.getString("content"));
        article.setPublishedAt(toInstant(rs.getTimestamp("published_at")));
        article.setFetchedAt(toInstant(rs.getTimestamp("fetched_at")));
        article.setRead(false);
        return article;
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
