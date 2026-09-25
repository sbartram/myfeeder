package org.bartram.myfeeder.repository;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.model.Article;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The single source of truth for the Priority sort and the interest badge (and, from plan 05-03,
 * the "Why N?" breakdown). Every query is built from {@link #blendCte(String)}, so the order and the
 * badge can never disagree.
 *
 * <p>Scope fragments are compile-time constants chosen in this class; every request value (limit,
 * cursor id, blend constants) is a named parameter. This class only reads: it never changes read or
 * starred state and never writes a row. It reads stored Jev outputs only and never calls Jev.
 *
 * <p>The blended raw score is {@code numeric} rounded to 6 decimals, so {@code ROUND} breaks ties away
 * from zero; only the sort key is cast to {@code float8}, with {@code '-Infinity'} for unscored rows so
 * they sort after every scored row, negative ones included.
 *
 * <p>The {@code learned} CTE returns 0 for every topic until Phase 6. Its replacement body must honor
 * {@code article_feedback.topics_narrowed} (03 D-02).
 */
@Repository
@RequiredArgsConstructor
public class InterestScoreQueries {

    /** Priority first page: unread articles. Alias {@code a} = article. */
    static final String UNREAD_SCOPE = "a.\"read\" = false";

    /** Priority page after a cursor: unread articles plus the cursor article, read or not (R4). */
    static final String UNREAD_OR_CURSOR_SCOPE = "a.\"read\" = false OR a.id = :cursorId";

    /** Badge enrichment: exactly the given article ids, read or unread (D-18). */
    static final String IDS_SCOPE = "a.id IN (:ids)";

    /**
     * The 0..100 badge, null for an unscored article. GREATEST/LEAST ignore NULL, so the CASE guard is
     * what keeps an unscored row null rather than 0 (Pitfall 1). Alias {@code b} = blended.
     */
    static final String INTEREST_SCORE =
            "CASE WHEN b.raw_n IS NULL THEN NULL ELSE LEAST(100, GREATEST(0, ROUND(b.raw_n)))::int END";

    /** Sort key: the raw score as float8, {@code '-Infinity'} for unscored rows. */
    static final String SORT_SCORE = "COALESCE(b.raw_n::float8, '-Infinity'::float8)";

    /** Date tiebreak, matching the chronological list. */
    static final String SORT_DATE = "COALESCE(a.published_at, a.fetched_at)";

    /** Explicit article columns: the reader-view cache column never ships in a list page. */
    static final String ARTICLE_COLUMNS = "a.id, a.feed_id, a.guid, a.title, a.url, a.author, a.content, "
            + "a.summary, a.image_url, a.published_at, a.fetched_at, a.\"read\", a.starred";

    /** Score, then date, then id, all descending. Alias {@code k} = keyed. */
    static final String KEYED_ORDER = "ORDER BY k.sort_score DESC, k.sort_date DESC, k.id DESC";

    /**
     * Raw breakdown inputs for one SCORED article, read from the blend CTE; the profile fields are null
     * when no profile question was asked.
     */
    public record BreakdownInputs(BigDecimal raw, int total, int display, Double profileScore,
                                  Integer profileMaxLevel, BigDecimal profileExact,
                                  List<TopicContribution> topics) {}

    /** One judged topic of an article as the blend CTE saw it: effective weight, hinge and exact points. */
    public record TopicContribution(long topicId, String name, double noul, double hinge, double weight,
                                    BigDecimal exact) {}

    private final JdbcClient jdbc;
    private final MyfeederProperties properties;

    /** First Priority page: scored articles by blended score, then unscored ones by date. */
    public List<Article> priorityFirstPage(int limit) {
        return jdbc.sql(blendCte(UNREAD_SCOPE) + keyed(UNREAD_SCOPE)
                        + " SELECT k.* FROM keyed k " + KEYED_ORDER + " LIMIT :limit")
                .param("profilePoints", properties.getInterest().getBlend().getProfilePoints())
                .param("limit", limit)
                .query((rs, rowNum) -> mapArticle(rs))
                .list();
    }

    /**
     * The Priority page after {@code cursorId}. The cursor row is resolved in the same statement and is
     * not unread-scoped, so a cursor article marked read between pages still continues exactly (R4).
     * A cursor id that matches no article returns an empty list; the caller checks existence first.
     */
    public List<Article> priorityPageAfter(long cursorId, int limit) {
        return jdbc.sql(blendCte(UNREAD_OR_CURSOR_SCOPE) + keyed(UNREAD_OR_CURSOR_SCOPE)
                        + " SELECT k.* FROM keyed k WHERE k.\"read\" = false"
                        + " AND (k.sort_score, k.sort_date, k.id)"
                        + " < (SELECT c.sort_score, c.sort_date, c.id FROM keyed c WHERE c.id = :cursorId) "
                        + KEYED_ORDER + " LIMIT :limit")
                .param("profilePoints", properties.getInterest().getBlend().getProfilePoints())
                .param("cursorId", cursorId)
                .param("limit", limit)
                .query((rs, rowNum) -> mapArticle(rs))
                .list();
    }

    /**
     * The badge for each of {@code ids}, from the same blend as the Priority sort. Id-scoped, not
     * unread-scoped (D-18): a read article keeps its badge. Only SCORED ids appear in the map; an empty
     * collection returns an empty map without running SQL.
     */
    public Map<Long, Integer> displayScores(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> scores = new HashMap<>();
        jdbc.sql(blendCte(IDS_SCOPE) + " SELECT b.article_id, " + INTEREST_SCORE + " AS interest_score FROM blended b")
                .param("profilePoints", properties.getInterest().getBlend().getProfilePoints())
                .param("ids", ids)
                .query(rs -> {
                    scores.put(rs.getLong("article_id"), rs.getObject("interest_score", Integer.class));
                });
        return scores;
    }

    /**
     * The blend: {@code raw = ROUND(profilePoints x profile_score / profile_max_level
     * + SUM(max(0, (noul - 0.5) x 2) x w), 6)} over SCORED rows, with {@code w = weight + learned}.
     * Only this class's scope constants are ever passed as {@code scope}.
     */
    private static String blendCte(String scope) {
        return "WITH learned AS (SELECT t.id AS topic_id, 0::double precision AS delta FROM interest_topic t), "
                + "eff AS (SELECT t.id, t.weight + COALESCE(l.delta, 0) AS w "
                + "FROM interest_topic t LEFT JOIN learned l ON l.topic_id = t.id), "
                + "contrib AS (SELECT ts.article_id, ts.topic_id, ts.noul, "
                + "GREATEST(0, (ts.noul - 0.5) * 2) AS hinge, e.w, "
                + "GREATEST(0, (ts.noul - 0.5) * 2) * e.w AS points "
                + "FROM article_topic_score ts JOIN eff e ON e.id = ts.topic_id), "
                + "blended AS (SELECT s.article_id, "
                + "ROUND((:profilePoints * COALESCE(s.profile_score / NULLIF(s.profile_max_level, 0), 0) "
                + "+ COALESCE(SUM(c.points), 0))::numeric, 6) AS raw_n "
                + "FROM article_score s JOIN article a ON a.id = s.article_id AND (" + scope + ") "
                + "LEFT JOIN contrib c ON c.article_id = s.article_id "
                + "WHERE s.status = 'SCORED' "
                + "GROUP BY s.article_id, s.profile_score, s.profile_max_level)";
    }

    /** Every in-scope article with its sort key, date key and badge. */
    private static String keyed(String scope) {
        return ", keyed AS (SELECT " + ARTICLE_COLUMNS + ", "
                + SORT_SCORE + " AS sort_score, "
                + SORT_DATE + " AS sort_date, "
                + INTEREST_SCORE + " AS interest_score "
                + "FROM article a LEFT JOIN blended b ON b.article_id = a.id WHERE " + scope + ")";
    }

    private static Article mapArticle(ResultSet rs) throws SQLException {
        Article article = new Article();
        article.setId(rs.getLong("id"));
        article.setFeedId(rs.getLong("feed_id"));
        article.setGuid(rs.getString("guid"));
        article.setTitle(rs.getString("title"));
        article.setUrl(rs.getString("url"));
        article.setAuthor(rs.getString("author"));
        article.setContent(rs.getString("content"));
        article.setSummary(rs.getString("summary"));
        article.setImageUrl(rs.getString("image_url"));
        article.setPublishedAt(toInstant(rs.getTimestamp("published_at")));
        article.setFetchedAt(toInstant(rs.getTimestamp("fetched_at")));
        article.setRead(rs.getBoolean("read"));
        article.setStarred(rs.getBoolean("starred"));
        article.setInterestScore(rs.getObject("interest_score", Integer.class));
        return article;
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
