package org.bartram.myfeeder.repository;

import org.bartram.myfeeder.TestcontainersConfiguration;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.repository.InterestScoreQueries.PriorityRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves D-05 (LRN-05): with engagement set to zero, or absent, every raw, badge, effective weight and the
 * Priority order equal the v0.2.1 SQL exactly. The v0.2.1 side runs the unread blend frozen from the
 * {@code v0.2.1} tag ({@code interest/v021-unread-blend.sql}), so the proof does not depend on the new
 * code's own text.
 *
 * <p>Topics: Rust +20, WebAssembly +14, Politics -30, Zero 0, Gardening +10. Eight SCORED unread articles
 * (a1 and a3 tie on raw and date), one read SCORED article, an un-narrowed up vote (a4), a narrowed down
 * vote with a pick (a6 on WebAssembly), and engagement on saved, opened, voted, Politics-matched, FAILED
 * and unscored articles. The constants are bound here (D-04): learnRate 2, learned-cap 20, profile-points 100.
 */
@DataJdbcTest
@Import(TestcontainersConfiguration.class)
@EnableConfigurationProperties(MyfeederProperties.class)
class InterestScoreQueriesZeroEngagementTest {

    private static final double LEARN_RATE = 2.0;
    private static final int LEARNED_CAP = 20;
    private static final int PROFILE_POINTS = 100;

    @Autowired private JdbcClient jdbcClient;
    @Autowired private JdbcTemplate jdbc;

    private String frozen;
    private long feedId;
    private Instant now;

    private long rust, webAssembly, politics, zero, gardening;
    private long a1, a2, a3, a4, a5, a6, a7, a8, r1, u1, u2;

    @BeforeEach
    void setUp() throws IOException {
        frozen = new ClassPathResource("interest/v021-unread-blend.sql")
                .getContentAsString(StandardCharsets.UTF_8).stripTrailing();
        assertThat(frozen).startsWith("WITH learned AS").doesNotContain("article_engagement");

        // Inside the rolled-back test transaction, so the fixture is the whole table.
        jdbc.update("DELETE FROM article_score");
        jdbc.update("DELETE FROM article");
        jdbc.update("DELETE FROM interest_topic");
        feedId = jdbc.queryForObject(
                "INSERT INTO feed (url, title, feed_type) VALUES (?, ?, ?) RETURNING id",
                Long.class, "https://example.com/zero-engagement-feed.xml", "Zero Engagement Feed", "RSS");
        now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        seedFixture();
    }

    @Test
    void capZeroWithEngagementRowsEqualsV021() {
        assertThat(count("SELECT count(*) FROM article_engagement")).isGreaterThan(0);

        assertEqualsV021(0.25, 0.5, 0);
    }

    @Test
    void noEngagementRowsAtCapEightEqualsV021() {
        jdbc.update("DELETE FROM article_engagement");

        assertEqualsV021(0.25, 0.5, 8);
    }

    @Test
    void capZeroWithNegativeWeightsEqualsV021() {
        assertThat(count("SELECT count(*) FROM article_engagement")).isGreaterThan(0);

        assertEqualsV021(-5, -1, 0);
    }

    @Test
    void engagementIsActiveAtCapEight() {
        Map<Long, BigDecimal> v021 = frozenRaws();
        Map<Long, BigDecimal> capEight = newRaws(0.25, 0.5, 8);

        assertThat(capEight.keySet()).isEqualTo(v021.keySet());
        assertThat(capEight.keySet()).anySatisfy(id ->
                assertThat(capEight.get(id).compareTo(v021.get(id))).as("raw of %d", id).isNotZero());
    }

    @Test
    void switchingTheCapBackRestoresExactly() {
        long engagementRows = count("SELECT count(*) FROM article_engagement");
        long weightSum = count("SELECT COALESCE(SUM(weight), 0) FROM interest_topic");

        Map<Long, BigDecimal> first = newRaws(0.25, 0.5, 8);
        assertRawsEqual(newRaws(0.25, 0.5, 0), frozenRaws());
        Map<Long, BigDecimal> again = newRaws(0.25, 0.5, 8);

        assertRawsEqual(again, first);
        assertThat(count("SELECT count(*) FROM article_engagement")).isEqualTo(engagementRows);
        assertThat(count("SELECT COALESCE(SUM(weight), 0) FROM interest_topic")).isEqualTo(weightSum);
    }

    /** Raws (numeric compareTo), badges, eff2 w (bit-identical) and Priority ids all equal v0.2.1. */
    private void assertEqualsV021(double open, double save, double cap) {
        assertRawsEqual(newRaws(open, save, cap), frozenRaws());
        assertThat(newBadges(open, save, cap)).as("badges").isEqualTo(frozenBadges());
        assertThat(newWeights(open, save, cap)).as("eff2 w").isEqualTo(frozenWeights());
        assertThat(ids(queries(open, save, cap).priorityFirstPage(1000))).as("Priority order")
                .isEqualTo(frozenPriorityIds());
    }

    private static void assertRawsEqual(Map<Long, BigDecimal> actual, Map<Long, BigDecimal> expected) {
        assertThat(actual.keySet()).as("scored unread ids").isEqualTo(expected.keySet());
        for (Map.Entry<Long, BigDecimal> e : expected.entrySet()) {
            assertThat(actual.get(e.getKey()).compareTo(e.getValue()))
                    .as("raw of %d: %s vs %s", e.getKey(), actual.get(e.getKey()), e.getValue()).isZero();
        }
    }

    // --- frozen v0.2.1 side: only the three v0.2.1 parameters are bound ---

    private JdbcClient.StatementSpec frozenSql(String sql) {
        return jdbcClient.sql(sql)
                .param("learnRate", LEARN_RATE)
                .param("learnedCap", LEARNED_CAP)
                .param("profilePoints", PROFILE_POINTS);
    }

    private Map<Long, BigDecimal> frozenRaws() {
        return raws(frozenSql(frozen + " SELECT b.article_id, b.raw_n FROM blended b"));
    }

    private Map<Long, Integer> frozenBadges() {
        return badges(frozenSql(frozen + " SELECT b.article_id, " + InterestScoreQueries.INTEREST_SCORE
                + " AS s FROM blended b"));
    }

    private Map<Long, Double> frozenWeights() {
        return weights(frozenSql(frozen + " SELECT e.id, e.w FROM eff2 e"));
    }

    private List<Long> frozenPriorityIds() {
        return frozenSql(frozen + ", keyed AS (SELECT a.id, " + InterestScoreQueries.SORT_SCORE + " AS sort_score, "
                + InterestScoreQueries.SORT_DATE + " AS sort_date FROM article a LEFT JOIN blended b "
                + "ON b.article_id = a.id WHERE " + InterestScoreQueries.UNREAD_SCOPE + ") SELECT k.id FROM keyed k "
                + InterestScoreQueries.KEYED_ORDER)
                .query(Long.class).list();
    }

    // --- new side: the Phase 9 SQL with all six parameters bound ---

    private JdbcClient.StatementSpec newSql(String sql, double open, double save, double cap) {
        return frozenSql(sql)
                .param("engagementOpenWeight", open)
                .param("engagementSaveWeight", save)
                .param("engagementCap", cap);
    }

    private Map<Long, BigDecimal> newRaws(double open, double save, double cap) {
        return raws(newSql(InterestScoreQueries.blendCte(InterestScoreQueries.UNREAD_SCOPE)
                + " SELECT b.article_id, b.raw_n FROM blended b", open, save, cap));
    }

    private Map<Long, Integer> newBadges(double open, double save, double cap) {
        return badges(newSql(InterestScoreQueries.blendCte(InterestScoreQueries.UNREAD_SCOPE)
                + " SELECT b.article_id, " + InterestScoreQueries.INTEREST_SCORE + " AS s FROM blended b",
                open, save, cap));
    }

    private Map<Long, Double> newWeights(double open, double save, double cap) {
        return weights(newSql(InterestScoreQueries.LEARNED_CTE + " SELECT e.id, e.w FROM eff2 e", open, save, cap));
    }

    private InterestScoreQueries queries(double open, double save, double cap) {
        MyfeederProperties props = new MyfeederProperties();
        MyfeederProperties.Interest.Blend blend = props.getInterest().getBlend();
        blend.setLearnRate(LEARN_RATE);
        blend.setLearnedCap(LEARNED_CAP);
        blend.setProfilePoints(PROFILE_POINTS);
        blend.getEngagement().setOpenWeight(open);
        blend.getEngagement().setSaveWeight(save);
        blend.getEngagement().setCap(cap);
        return new InterestScoreQueries(jdbcClient, props);
    }

    private static Map<Long, BigDecimal> raws(JdbcClient.StatementSpec spec) {
        Map<Long, BigDecimal> raws = new HashMap<>();
        spec.query(rs -> {
            raws.put(rs.getLong("article_id"), rs.getBigDecimal("raw_n"));
        });
        return raws;
    }

    private static Map<Long, Integer> badges(JdbcClient.StatementSpec spec) {
        Map<Long, Integer> badges = new HashMap<>();
        spec.query(rs -> {
            badges.put(rs.getLong("article_id"), rs.getObject("s", Integer.class));
        });
        return badges;
    }

    private static Map<Long, Double> weights(JdbcClient.StatementSpec spec) {
        Map<Long, Double> weights = new HashMap<>();
        spec.query(rs -> {
            weights.put(rs.getLong("id"), rs.getDouble("w"));
        });
        return weights;
    }

    private static List<Long> ids(List<PriorityRow> rows) {
        return rows.stream().map(row -> row.article().getId()).toList();
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private void seedFixture() {
        rust = insertTopic("Rust", 20);
        webAssembly = insertTopic("WebAssembly", 14);
        politics = insertTopic("Politics", -30);
        zero = insertTopic("Zero", 0);
        gardening = insertTopic("Gardening", 10);

        a1 = insertArticle("a1", false, now.minus(Duration.ofHours(1)), now);
        a2 = insertArticle("a2", false, now.minus(Duration.ofHours(2)), now);
        a3 = insertArticle("a3", false, now.minus(Duration.ofHours(1)), now);
        a4 = insertArticle("a4", false, now.minus(Duration.ofHours(5)), now);
        a5 = insertArticle("a5", false, now.minus(Duration.ofHours(1)), now);
        a6 = insertArticle("a6", false, now.minus(Duration.ofHours(3)), now);
        a7 = insertArticle("a7", false, now.minus(Duration.ofHours(2)), now);
        a8 = insertArticle("a8", false, now.minus(Duration.ofHours(4)), now);
        r1 = insertArticle("r1", true, now.minus(Duration.ofHours(1)), now);
        u1 = insertArticle("u1", false, now.minus(Duration.ofMinutes(30)), now);
        u2 = insertArticle("u2", false, now.minus(Duration.ofHours(4)), now);

        // Every article_score row before any article_topic_score row (FK)
        for (long tied : List.of(a1, a2, a3)) {
            insertScored(tied, 2.56, 4);
        }
        insertScored(a4, 4.0, 4);
        insertScored(a5, 0.0, 4);
        insertScored(a6, 1.0, 4);
        insertScored(a7, null, null);
        insertScored(a8, 3.0, 4);
        insertScored(r1, 4.0, 4);
        insertStatus(u2, "FAILED");

        for (long tied : List.of(a1, a2, a3)) {
            insertTopicScore(tied, rust, 0.93);
            insertTopicScore(tied, webAssembly, 0.75);
            insertTopicScore(tied, politics, 0.60);
            insertTopicScore(tied, gardening, 0.20);
        }
        insertTopicScore(a4, rust, 0.99);
        insertTopicScore(a4, webAssembly, 0.99);
        insertTopicScore(a5, politics, 1.0);
        insertTopicScore(a6, webAssembly, 0.625);
        insertTopicScore(a7, rust, 0.75);
        insertTopicScore(a8, zero, 0.9);
        insertTopicScore(r1, gardening, 0.8);
        insertTopicScore(u2, rust, 1.0);

        insertFeedback(a4, 1, false);
        insertFeedback(a6, -1, true);
        insertPick(a6, webAssembly);

        insertEngagement(a1, "STAR");
        insertEngagement(a1, "OPEN_ORIGINAL");
        insertEngagement(a2, "OPEN_ORIGINAL");
        insertEngagement(a5, "BOARD");
        insertEngagement(a4, "RAINDROP");
        insertEngagement(u2, "STAR");
        insertEngagement(u1, "OPEN_ORIGINAL");
    }

    private long insertArticle(String guid, boolean read, Instant publishedAt, Instant fetchedAt) {
        return jdbc.queryForObject(
                "INSERT INTO article (feed_id, guid, title, url, summary, content, published_at, fetched_at, \"read\") "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class, feedId, guid, "Title " + guid, "https://example.com/" + guid,
                "Summary " + guid, "Content " + guid,
                publishedAt == null ? null : Timestamp.from(publishedAt), Timestamp.from(fetchedAt), read);
    }

    private long insertTopic(String name, int weight) {
        return jdbc.queryForObject(
                "INSERT INTO interest_topic (name, description, weight) VALUES (?, ?, ?) RETURNING id",
                Long.class, name, "Articles about " + name, weight);
    }

    private void insertScored(long articleId, Double profileScore, Integer profileMaxLevel) {
        jdbc.update("INSERT INTO article_score (article_id, status, profile_score, profile_max_level, attempts) "
                + "VALUES (?, 'SCORED', ?, ?, 1)", articleId, profileScore, profileMaxLevel);
    }

    private void insertStatus(long articleId, String status) {
        jdbc.update("INSERT INTO article_score (article_id, status, attempts) VALUES (?, ?, 1)", articleId, status);
    }

    private void insertTopicScore(long articleId, long topicId, double noul) {
        jdbc.update("INSERT INTO article_topic_score (article_id, topic_id, noul, topic_version) VALUES (?, ?, ?, 1)",
                articleId, topicId, noul);
    }

    private void insertFeedback(long articleId, int vote, boolean narrowed) {
        jdbc.update("INSERT INTO article_feedback (article_id, vote, topics_narrowed) VALUES (?, ?, ?)",
                articleId, vote, narrowed);
    }

    private void insertPick(long articleId, long topicId) {
        jdbc.update("INSERT INTO article_feedback_topic (article_id, topic_id) VALUES (?, ?)", articleId, topicId);
    }

    private void insertEngagement(long articleId, String kind) {
        jdbc.update("INSERT INTO article_engagement (article_id, kind) VALUES (?, ?)", articleId, kind);
    }
}
