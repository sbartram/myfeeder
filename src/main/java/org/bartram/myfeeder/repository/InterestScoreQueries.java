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
import java.util.LinkedHashMap;
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
 * <p>The {@link #LEARNED_CTE} derives each topic's learned adjustment from the stored thumbs votes in
 * {@code article_feedback}, honoring {@code topics_narrowed} and its {@code article_feedback_topic} picks
 * (03 D-02), and from the engagement in {@code article_engagement} (LRN-01). The adjustment is derived
 * on every read and never stored, so removing or flipping a vote undoes it exactly. This class still
 * only reads and never calls Jev.
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

    /**
     * One judged topic of an article as the blend CTE saw it: effective weight, hinge and exact points,
     * plus the topic's base weight and the applied learned part ({@code weight - baseWeight}, after the
     * cap, the sign clamp and the +/-50 range), so {@code baseWeight + learnedWeight} equals
     * {@code weight} (D-11). Weights are rounded to 6 decimals. The applied learned part splits into
     * {@code thumbsWeight} (the votes, {@code thumbsEffective - base}) and {@code engagementWeight}
     * ({@code weight - thumbsEffective}), both computed in SQL by subtracting 6-decimal rounded values, so
     * {@code learnedWeight = thumbsWeight + engagementWeight} and
     * {@code baseWeight + thumbsWeight + engagementWeight = weight} hold exactly (D-09, D-10).
     */
    public record TopicContribution(long topicId, String name, double noul, double hinge, double weight,
                                    BigDecimal exact, double baseWeight, double learnedWeight,
                                    double thumbsWeight, double engagementWeight) {}

    /**
     * The learned model (R2, FDBK-03, LRN-01..04), shared by the blend and {@link #topicWeights(Collection)}.
     * {@code learned} sums {@code vote x max(0, (noul - 0.5) x 2)} per topic over votes on SCORED articles,
     * keeping a narrowed vote's picked topics only (03 D-02). {@code engaged} collapses
     * {@code article_engagement} to one MAX {@code strength} per article before any topic join (a save beats
     * an open, each article counts once) and drops every article that has any thumbs vote (the vote replaces
     * it; removing the vote restores it). {@code eng_learned} sums {@code strength x hinge} per topic over
     * SCORED articles only, with no age or read window (D-08). {@code eff} scales the thumbs sum by
     * {@code :learnRate} ({@code learned_raw}) and clamps it to {@code +/- :learnedCap} ({@code learned});
     * it scales the engagement sum the same way ({@code eng_raw}, D-06/D-07) and caps it at
     * {@code :engagementCap} with a zero floor ({@code eng}), so cap 0 disables it whatever the weights
     * (D-02). Both are 0 for a topic with a negative base (LRN-03). {@code eff2} applies the sign clamp (a
     * positive base never goes below 0, a negative base never above 0, a zero base moves either way) inside
     * -50..+50 once: {@code w_thumbs} to {@code base + learned} (the thumbs-only effective weight) and
     * {@code w} to {@code base + learned + eng} (D-09).
     * The constants are cast to float8 because an untyped unary minus is ambiguous in Postgres.
     */
    static final String LEARNED_CTE = "WITH learned AS (SELECT ts.topic_id, "
            + "SUM(f.vote * GREATEST(0, (ts.noul - 0.5) * 2)) AS vote_sum "
            + "FROM article_feedback f "
            + "JOIN article_score fs ON fs.article_id = f.article_id AND fs.status = 'SCORED' "
            + "JOIN article_topic_score ts ON ts.article_id = f.article_id "
            + "WHERE NOT f.topics_narrowed OR EXISTS (SELECT 1 FROM article_feedback_topic ft "
            + "WHERE ft.article_id = f.article_id AND ft.topic_id = ts.topic_id) "
            + "GROUP BY ts.topic_id), "
            + "engaged AS (SELECT g.article_id, "
            + "MAX(CASE WHEN g.kind = 'OPEN_ORIGINAL' THEN CAST(:engagementOpenWeight AS float8) "
            + "ELSE CAST(:engagementSaveWeight AS float8) END) AS strength "
            + "FROM article_engagement g "
            + "WHERE NOT EXISTS (SELECT 1 FROM article_feedback f WHERE f.article_id = g.article_id) "
            + "GROUP BY g.article_id), "
            + "eng_learned AS (SELECT ts.topic_id, SUM(g.strength * GREATEST(0, (ts.noul - 0.5) * 2)) AS eng_sum "
            + "FROM engaged g "
            + "JOIN article_score gs ON gs.article_id = g.article_id AND gs.status = 'SCORED' "
            + "JOIN article_topic_score ts ON ts.article_id = g.article_id "
            + "GROUP BY ts.topic_id), "
            + "eff AS (SELECT t.id, t.name, t.weight AS base, "
            + "CAST(:learnRate AS float8) * COALESCE(l.vote_sum, 0) AS learned_raw, "
            + "LEAST(CAST(:learnedCap AS float8), GREATEST(-CAST(:learnedCap AS float8), "
            + "CAST(:learnRate AS float8) * COALESCE(l.vote_sum, 0))) AS learned, "
            + "CASE WHEN t.weight < 0 THEN 0 ELSE CAST(:learnRate AS float8) * COALESCE(n.eng_sum, 0) END AS eng_raw, "
            + "CASE WHEN t.weight < 0 THEN 0 ELSE GREATEST(0, LEAST(CAST(:engagementCap AS float8), "
            + "CAST(:learnRate AS float8) * COALESCE(n.eng_sum, 0))) END AS eng "
            + "FROM interest_topic t LEFT JOIN learned l ON l.topic_id = t.id "
            + "LEFT JOIN eng_learned n ON n.topic_id = t.id), "
            + "eff2 AS (SELECT e.*, CASE WHEN e.base > 0 THEN GREATEST(0, LEAST(50, e.base + e.learned)) "
            + "WHEN e.base < 0 THEN LEAST(0, GREATEST(-50, e.base + e.learned)) "
            + "ELSE GREATEST(-50, LEAST(50, e.learned)) END AS w_thumbs, "
            + "CASE WHEN e.base > 0 THEN GREATEST(0, LEAST(50, e.base + e.learned + e.eng)) "
            + "WHEN e.base < 0 THEN LEAST(0, GREATEST(-50, e.base + e.learned + e.eng)) "
            + "ELSE GREATEST(-50, LEAST(50, e.learned + e.eng)) END AS w "
            + "FROM eff e)";

    /**
     * One topic's weights under the learned model, all rounded to 6 decimals. {@code learnedRaw} is the
     * uncapped thumbs points. {@code learned} is the thumbs capped plus the engagement capped, before the
     * clamp (D-15): the sum {@code LearnedLimit} judges. {@code effective} is the clamped
     * {@code base + learned}. {@code thumbsLearned} is the capped thumbs points, {@code engagementRaw} and
     * {@code engagementLearned} the uncapped and capped engagement points (0 for a negative base), and
     * {@code thumbsEffective} the thumbs-only effective weight ({@code w_thumbs}).
     */
    public record TopicWeight(long topicId, String name, double base, double learnedRaw, double learned,
                              double effective, double thumbsLearned, double engagementRaw,
                              double engagementLearned, double thumbsEffective) {}

    /** The Priority sort tuple of one row as served; {@code score} is {@code -Infinity} when unscored. */
    public record SortKey(double score, Instant date, long id) {}

    /** One Priority row: the article and the sort tuple it was served with. */
    public record PriorityRow(Article article, SortKey key) {}

    private final JdbcClient jdbc;
    private final MyfeederProperties properties;

    /** First Priority page: scored articles by blended score, then unscored ones by date. */
    public List<PriorityRow> priorityFirstPage(int limit) {
        return blendSql(blendCte(UNREAD_SCOPE) + keyed(UNREAD_SCOPE)
                        + " SELECT k.* FROM keyed k " + KEYED_ORDER + " LIMIT :limit")
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
        return blendSql(blendCte(UNREAD_SCOPE) + keyed(UNREAD_SCOPE)
                        + " SELECT k.* FROM keyed k WHERE (k.sort_score, k.sort_date, k.id)"
                        + " < (CAST(:cursorScore AS float8), CAST(:cursorDate AS timestamptz), CAST(:cursorId AS bigint)) "
                        + KEYED_ORDER + " LIMIT :limit")
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
        blendSql(blendCte(IDS_SCOPE) + " SELECT b.article_id, " + INTEREST_SCORE + " AS interest_score FROM blended b")
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
        Optional<BreakdownInputs> header = blendSql(blendCte(ARTICLE_SCOPE) + " SELECT b.raw_n, " + TOTAL + " AS total, "
                        + INTEREST_SCORE + " AS interest_score, s.profile_score, s.profile_max_level, "
                        + "CASE WHEN s.profile_score IS NULL THEN NULL ELSE ROUND((:profilePoints * "
                        + "COALESCE(s.profile_score / NULLIF(s.profile_max_level, 0), 0))::numeric, 6) END AS profile_exact "
                        + "FROM blended b JOIN article_score s ON s.article_id = b.article_id")
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
        List<TopicContribution> topics = blendSql(blendCte(ARTICLE_SCOPE) + " SELECT c.topic_id, t.name, c.noul, c.hinge, "
                        + "ROUND(c.w::numeric, 6) AS w, ROUND(c.points::numeric, 6) AS exact, "
                        + "ROUND(c.base::numeric, 6) AS base_w, "
                        + "ROUND(c.w::numeric, 6) - ROUND(c.base::numeric, 6) AS learned_w, "
                        + "ROUND(c.w_thumbs::numeric, 6) - ROUND(c.base::numeric, 6) AS thumbs_w, "
                        + "ROUND(c.w::numeric, 6) - ROUND(c.w_thumbs::numeric, 6) AS eng_w "
                        + "FROM contrib c JOIN interest_topic t ON t.id = c.topic_id WHERE c.article_id = :articleId")
                .param("articleId", articleId)
                .query((rs, rowNum) -> new TopicContribution(
                        rs.getLong("topic_id"),
                        rs.getString("name"),
                        rs.getDouble("noul"),
                        rs.getDouble("hinge"),
                        rs.getDouble("w"),
                        rs.getBigDecimal("exact"),
                        rs.getDouble("base_w"),
                        rs.getDouble("learned_w"),
                        rs.getDouble("thumbs_w"),
                        rs.getDouble("eng_w")))
                .list();
        BreakdownInputs h = header.get();
        return Optional.of(new BreakdownInputs(h.raw(), h.total(), h.display(), h.profileScore(),
                h.profileMaxLevel(), h.profileExact(), topics));
    }

    /**
     * The current weights of {@code topicIds} under the learned model, in ascending id order. Ids of
     * topics that no longer exist are absent; an empty collection returns an empty map without SQL.
     */
    public Map<Long, TopicWeight> topicWeights(Collection<Long> topicIds) {
        if (topicIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, TopicWeight> weights = new LinkedHashMap<>();
        learnedSql(TOPIC_WEIGHTS_SELECT + " WHERE e.id IN (:ids) ORDER BY e.id")
                .param("ids", topicIds)
                .query(rs -> {
                    TopicWeight w = topicWeight(rs);
                    weights.put(w.topicId(), w);
                });
        return weights;
    }

    /**
     * Every topic's weights under the learned model, in ascending id order (FDBK-07). Read-only; an
     * empty topic table returns an empty list.
     */
    public List<TopicWeight> allTopicWeights() {
        return learnedSql(TOPIC_WEIGHTS_SELECT + " ORDER BY e.id")
                .query((rs, rowNum) -> topicWeight(rs))
                .list();
    }

    /** The {@link TopicWeight} select over {@code eff2}, rounded to 6 decimals; callers add filter and order. */
    private static final String TOPIC_WEIGHTS_SELECT = LEARNED_CTE
            + " SELECT e.id, e.name, ROUND(e.base::numeric, 6) AS base, "
            + "ROUND(e.learned_raw::numeric, 6) AS learned_raw, "
            + "ROUND(e.learned::numeric, 6) + ROUND(e.eng::numeric, 6) AS learned, "
            + "ROUND(e.w::numeric, 6) AS w, ROUND(e.learned::numeric, 6) AS thumbs_learned, "
            + "ROUND(e.eng_raw::numeric, 6) AS eng_raw, ROUND(e.eng::numeric, 6) AS eng, "
            + "ROUND(e.w_thumbs::numeric, 6) AS w_thumbs FROM eff2 e";

    private static TopicWeight topicWeight(ResultSet rs) throws SQLException {
        return new TopicWeight(rs.getLong("id"), rs.getString("name"), rs.getDouble("base"),
                rs.getDouble("learned_raw"), rs.getDouble("learned"), rs.getDouble("w"),
                rs.getDouble("thumbs_learned"), rs.getDouble("eng_raw"), rs.getDouble("eng"),
                rs.getDouble("w_thumbs"));
    }

    /**
     * The topics an article matched: those with a stored noul above 0.5 (hinge above 0), and only when
     * the article has a SCORED row. Ascending topic id; empty for an unscored article.
     */
    public List<Long> matchedTopicIds(long articleId) {
        return jdbc.sql("SELECT ts.topic_id FROM article_topic_score ts "
                        + "JOIN article_score s ON s.article_id = ts.article_id AND s.status = 'SCORED' "
                        + "WHERE ts.article_id = :articleId AND GREATEST(0, (ts.noul - 0.5) * 2) > 0 "
                        + "ORDER BY ts.topic_id")
                .param("articleId", articleId)
                .query(Long.class)
                .list();
    }

    /** Whether the article has a SCORED score row (FAILED and SKIPPED rows are not scored). */
    public boolean isScored(long articleId) {
        return Boolean.TRUE.equals(jdbc.sql("SELECT EXISTS (SELECT 1 FROM article_score "
                        + "WHERE article_id = :articleId AND status = 'SCORED')")
                .param("articleId", articleId)
                .query(Boolean.class)
                .single());
    }

    /**
     * A statement that reads the learned model: binds every learned-model constant, under the same names
     * the calibration replay passes with psql -v.
     */
    private JdbcClient.StatementSpec learnedSql(String sql) {
        MyfeederProperties.Interest.Blend blend = properties.getInterest().getBlend();
        return jdbc.sql(sql)
                .param("learnRate", blend.getLearnRate())
                .param("learnedCap", blend.getLearnedCap())
                .param("engagementOpenWeight", blend.getEngagement().getOpenWeight())
                .param("engagementSaveWeight", blend.getEngagement().getSaveWeight())
                .param("engagementCap", blend.getEngagement().getCap());
    }

    /** A statement built from {@link #blendCte(String)}: binds every blend constant in one place. */
    private JdbcClient.StatementSpec blendSql(String sql) {
        return learnedSql(sql).param("profilePoints", properties.getInterest().getBlend().getProfilePoints());
    }

    /**
     * The blend: {@code raw = ROUND(profilePoints x profile_score / profile_max_level
     * + SUM(max(0, (noul - 0.5) x 2) x w), 6)} over SCORED rows, with {@code w} the effective weight from
     * {@link #LEARNED_CTE}. {@code contrib} also carries the topic's {@code base},
     * {@code learned_applied = w - base}, the learned part that actually applied, and {@code w_thumbs}, the
     * thumbs-only effective weight. Only this class's scope constants are ever passed as {@code scope}.
     */
    static String blendCte(String scope) {
        return LEARNED_CTE + ", "
                + "contrib AS (SELECT ts.article_id, ts.topic_id, ts.noul, "
                + "GREATEST(0, (ts.noul - 0.5) * 2) AS hinge, e.w, e.base, e.w - e.base AS learned_applied, e.w_thumbs, "
                + "GREATEST(0, (ts.noul - 0.5) * 2) * e.w AS points "
                + "FROM article_topic_score ts JOIN eff2 e ON e.id = ts.topic_id), "
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
