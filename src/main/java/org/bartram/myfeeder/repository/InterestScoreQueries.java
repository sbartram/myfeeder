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
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The single source of truth for the Priority sort and the interest badge (and, from plan 05-03,
 * the "Why N?" breakdown). Every query is built from {@link #blendCte(String)}, so the order and the
 * badge can never disagree.
 *
 * <p>Scope fragments are compile-time constants chosen in this class; every request value (limit,
 * cursor tuple, blend constants) is a named parameter. This class only reads: it never changes read or
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

    /** Priority pages: unread articles. Alias {@code a} = article. */
    static final String UNREAD_SCOPE = "a.\"read\" = false";

    /** Badge enrichment: exactly the given article ids, read or unread (D-18). */
    static final String IDS_SCOPE = "a.id IN (:ids)";

    /** One article's breakdown (PRIO-04). */
    static final String ARTICLE_SCOPE = "a.id = :articleId";

    /** The unclamped integer total, SQL {@code ROUND} of the numeric raw (half away from zero). */
    static final String TOTAL = "CASE WHEN b.raw_n IS NULL THEN NULL ELSE ROUND(b.raw_n)::int END";

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

    /** The Priority sort tuple of one row as served; {@code score} is {@code -Infinity} when unscored. */
    public record SortKey(double score, Instant date, long id) {}

    /** One Priority row: the article and the sort tuple it was served with. */
    public record PriorityRow(Article article, SortKey key) {}

    private final JdbcClient jdbc;
    private final MyfeederProperties properties;

    /** First Priority page: scored articles by blended score, then unscored ones by date. */
    public List<PriorityRow> priorityFirstPage(int limit) {
        return jdbc.sql(blendCte(UNREAD_SCOPE) + keyed(UNREAD_SCOPE)
                        + " SELECT k.* FROM keyed k " + KEYED_ORDER + " LIMIT :limit")
                .param("profilePoints", properties.getInterest().getBlend().getProfilePoints())
                .param("limit", limit)
                .query((rs, rowNum) -> mapPriorityRow(rs))
                .list();
    }

    /**
     * The Priority page after the row served with {@code after}. It compares against the literal tuple
     * the client got back and never reads the cursor article's live score, so a score change between
     * pages cannot skip rows (WR-02). A row whose own score changed may cross the boundary and be listed
     * twice; the client dedupes it by id. The statement does not read the cursor article, so a cursor
     * that was marked read still continues exactly (R4); the caller checks that it still exists.
     *
     * <p>The date is bound as an {@link java.time.OffsetDateTime} in UTC, which PgJDBC sends as a
     * timestamptz with an explicit offset, so it cannot shift with the JVM or session time zone.
     */
    public List<PriorityRow> priorityPageAfter(SortKey after, int limit) {
        return jdbc.sql(blendCte(UNREAD_SCOPE) + keyed(UNREAD_SCOPE)
                        + " SELECT k.* FROM keyed k WHERE (k.sort_score, k.sort_date, k.id)"
                        + " < (CAST(:cursorScore AS float8), CAST(:cursorDate AS timestamptz), CAST(:cursorId AS bigint)) "
                        + KEYED_ORDER + " LIMIT :limit")
                .param("profilePoints", properties.getInterest().getBlend().getProfilePoints())
                .param("cursorScore", after.score())
                .param("cursorDate", after.date().atOffset(ZoneOffset.UTC))
                .param("cursorId", after.id())
                .param("limit", limit)
                .query((rs, rowNum) -> mapPriorityRow(rs))
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
     * The raw inputs of one article's "Why N?" breakdown, from the same blend CTE as the badge and the
     * Priority sort: the numeric raw, the SQL total ({@code ROUND(raw)}, unclamped), the clamped badge,
     * the profile inputs and every judged topic with its effective weight, hinge and exact points (6
     * decimals). Empty when the article has no SCORED row. Topics created after scoring have no stored
     * noul and are not listed (R5).
     */
    public Optional<BreakdownInputs> breakdownInputs(long articleId) {
        int profilePoints = properties.getInterest().getBlend().getProfilePoints();
        Optional<BreakdownInputs> header = jdbc.sql(blendCte(ARTICLE_SCOPE) + " SELECT b.raw_n, " + TOTAL + " AS total, "
                        + INTEREST_SCORE + " AS interest_score, s.profile_score, s.profile_max_level, "
                        + "CASE WHEN s.profile_score IS NULL THEN NULL ELSE ROUND((:profilePoints * "
                        + "COALESCE(s.profile_score / NULLIF(s.profile_max_level, 0), 0))::numeric, 6) END AS profile_exact "
                        + "FROM blended b JOIN article_score s ON s.article_id = b.article_id")
                .param("profilePoints", profilePoints)
                .param("articleId", articleId)
                .query((rs, rowNum) -> new BreakdownInputs(
                        rs.getBigDecimal("raw_n"),
                        rs.getInt("total"),
                        rs.getInt("interest_score"),
                        rs.getObject("profile_score", Double.class),
                        rs.getObject("profile_max_level", Integer.class),
                        rs.getBigDecimal("profile_exact"),
                        List.of()))
                .optional();
        if (header.isEmpty()) {
            return Optional.empty();
        }
        List<TopicContribution> topics = jdbc.sql(blendCte(ARTICLE_SCOPE) + " SELECT c.topic_id, t.name, c.noul, c.hinge, c.w, "
                        + "ROUND(c.points::numeric, 6) AS exact "
                        + "FROM contrib c JOIN interest_topic t ON t.id = c.topic_id WHERE c.article_id = :articleId")
                .param("profilePoints", profilePoints)
                .param("articleId", articleId)
                .query((rs, rowNum) -> new TopicContribution(
                        rs.getLong("topic_id"),
                        rs.getString("name"),
                        rs.getDouble("noul"),
                        rs.getDouble("hinge"),
                        rs.getDouble("w"),
                        rs.getBigDecimal("exact")))
                .list();
        BreakdownInputs h = header.get();
        return Optional.of(new BreakdownInputs(h.raw(), h.total(), h.display(), h.profileScore(),
                h.profileMaxLevel(), h.profileExact(), topics));
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

    private static PriorityRow mapPriorityRow(ResultSet rs) throws SQLException {
        return new PriorityRow(mapArticle(rs), new SortKey(
                rs.getDouble("sort_score"), rs.getTimestamp("sort_date").toInstant(), rs.getLong("id")));
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
