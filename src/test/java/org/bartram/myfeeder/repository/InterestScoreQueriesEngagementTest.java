package org.bartram.myfeeder.repository;

import org.bartram.myfeeder.TestcontainersConfiguration;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.repository.InterestScoreQueries.TopicWeight;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Proves the engagement part of the learned model (LRN-01..03, D-06..D-08) against real Postgres.
 *
 * <p>The constants are bound here, not read from the test yaml (D-04): learnRate 2, learned-cap 20,
 * profile-points 100, open 0.25, save 0.5, cap 8. At noul 0.95 the hinge is 0.9, so one open adds
 * 2 x 0.25 x 0.9 = 0.45 and one save adds 2 x 0.5 x 0.9 = 0.9.
 *
 * <p>Topics: rust +20, go +10, zero 0, politics -10. Only effective weights, badges and whole-map
 * equality are asserted, never the split learned components.
 */
@DataJdbcTest
@Import(TestcontainersConfiguration.class)
@EnableConfigurationProperties(MyfeederProperties.class)
class InterestScoreQueriesEngagementTest {

    @Autowired private JdbcClient jdbcClient;
    @Autowired private JdbcTemplate jdbc;

    private InterestScoreQueries queries;
    private long feedId;
    private Instant now;
    private int guidSeq;

    private long rust, go, zero, politics;

    @BeforeEach
    void setUp() {
        // Inside the rolled-back test transaction, so the fixture is the whole table.
        jdbc.update("DELETE FROM article_score");
        jdbc.update("DELETE FROM article");
        jdbc.update("DELETE FROM interest_topic");
        feedId = jdbc.queryForObject(
                "INSERT INTO feed (url, title, feed_type) VALUES (?, ?, ?) RETURNING id",
                Long.class, "https://example.com/engagement-feed.xml", "Engagement Feed", "RSS");
        now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        MyfeederProperties props = new MyfeederProperties();
        MyfeederProperties.Interest.Blend blend = props.getInterest().getBlend();
        blend.setLearnRate(2);
        blend.setLearnedCap(20);
        blend.setProfilePoints(100);
        blend.getEngagement().setOpenWeight(0.25);
        blend.getEngagement().setSaveWeight(0.5);
        blend.getEngagement().setCap(8);
        queries = new InterestScoreQueries(jdbcClient, props);

        rust = insertTopic("rust", 20);
        go = insertTopic("go", 10);
        zero = insertTopic("zero", 0);
        politics = insertTopic("politics", -10);
    }

    @Test
    void anOpenAddsOpenWeightTimesHinge() {
        insertEngagement(matching(rust, 0.95), "OPEN_ORIGINAL");

        assertThat(effective(rust)).isCloseTo(20.45, within(1e-6));
    }

    @Test
    void aSaveCountsMoreThanAnOpen() {
        long opened = matching(go, 0.95);
        insertEngagement(opened, "OPEN_ORIGINAL");
        double openValue = effective(go);

        insertEngagement(matching(rust, 0.95), "STAR");

        assertThat(effective(rust)).isCloseTo(20.9, within(1e-6));
        assertThat(effective(rust) - 20).isGreaterThan(openValue - 10);
    }

    @Test
    void everyKindOnOneArticleCountsOnceAsASave() {
        long article = matching(rust, 0.95);
        for (String kind : List.of("OPEN_ORIGINAL", "STAR", "BOARD", "RAINDROP")) {
            insertEngagement(article, kind);
        }

        assertThat(effective(rust)).isCloseTo(20.9, within(1e-6));
    }

    @Test
    void eachEngagedArticleCountsOnce() {
        insertEngagement(matching(rust, 0.95), "STAR");
        insertEngagement(matching(rust, 0.95), "BOARD");

        assertThat(effective(rust)).isCloseTo(21.8, within(1e-6));
    }

    @Test
    void onlyScoredArticlesCount() {
        long failed = insertArticle(false, now.minus(Duration.ofHours(1)));
        insertStatus(failed, "FAILED");
        insertTopicScore(failed, rust, 0.95);
        insertEngagement(failed, "STAR");

        long skipped = insertArticle(false, now.minus(Duration.ofHours(1)));
        insertStatus(skipped, "SKIPPED");
        insertTopicScore(skipped, rust, 0.95);
        insertEngagement(skipped, "STAR");

        long unscored = insertArticle(false, now.minus(Duration.ofHours(1)));
        insertEngagement(unscored, "STAR");

        assertThat(effective(rust)).isCloseTo(20.0, within(1e-6));
    }

    @Test
    void anEngagedArticleWithoutTopicScoresAddsNothing() {
        Map<Long, TopicWeight> before = allWeights();
        long article = insertArticle(false, now.minus(Duration.ofHours(1)));
        insertScored(article, 3.0, 4);

        insertEngagement(article, "STAR");
        insertEngagement(article, "OPEN_ORIGINAL");

        assertThat(allWeights()).isEqualTo(before);
    }

    @Test
    void zeroBaseQualifies() {
        insertEngagement(matching(zero, 0.95), "RAINDROP");

        assertThat(effective(zero)).isCloseTo(0.9, within(1e-6));
    }

    @Test
    void negativeBaseIsNeverNudged() {
        for (int i = 0; i < 3; i++) {
            insertEngagement(matching(politics, 0.95), "STAR");
        }

        assertThat(effective(politics)).isCloseTo(-10.0, within(1e-6));
    }

    @Test
    void readAndOldArticlesStillCount() {
        long old = insertArticle(true, now.minus(Duration.ofDays(400)));
        insertScored(old, null, null);
        insertTopicScore(old, rust, 0.95);
        insertEngagement(old, "BOARD");

        assertThat(effective(rust)).isCloseTo(20.9, within(1e-6));
    }

    @Test
    void anyVoteReplacesTheArticlesEngagementOnEveryTopic() {
        long article = insertArticle(false, now.minus(Duration.ofHours(1)));
        insertScored(article, null, null);
        insertTopicScore(article, rust, 0.95);
        insertTopicScore(article, go, 0.95);
        insertEngagement(article, "STAR");
        assertWeights(20.9, 10.9);

        insertFeedback(article, 1, false);
        assertWeights(21.8, 11.8);

        jdbc.update("UPDATE article_feedback SET vote = -1 WHERE article_id = ?", article);
        assertWeights(18.2, 8.2);

        jdbc.update("UPDATE article_feedback SET vote = 1, topics_narrowed = true WHERE article_id = ?", article);
        insertPick(article, rust);
        assertWeights(21.8, 10.0);

        jdbc.update("DELETE FROM article_feedback_topic WHERE article_id = ?", article);
        assertWeights(20.0, 10.0);
    }

    @Test
    void removingTheVoteRestoresTheEngagementExactly() {
        long article = insertArticle(false, now.minus(Duration.ofHours(1)));
        insertScored(article, null, null);
        insertTopicScore(article, rust, 0.95);
        insertTopicScore(article, go, 0.8);
        insertEngagement(article, "OPEN_ORIGINAL");
        insertEngagement(article, "STAR");
        Map<Long, TopicWeight> before = allWeights();

        insertFeedback(article, -1, false);
        assertThat(allWeights()).isNotEqualTo(before);
        jdbc.update("DELETE FROM article_feedback WHERE article_id = ?", article);

        assertThat(allWeights()).isEqualTo(before);
    }

    @Test
    void forgettingEngagementRestoresThePreEngagementWeights() {
        long other = matching(go, 0.9);
        insertFeedback(other, 1, false);
        long article = insertArticle(false, now.minus(Duration.ofHours(1)));
        insertScored(article, 2.0, 4);
        insertTopicScore(article, rust, 0.95);
        insertTopicScore(article, zero, 0.7);
        Map<Long, TopicWeight> before = allWeights();

        insertEngagement(article, "OPEN_ORIGINAL");
        insertEngagement(article, "RAINDROP");
        assertThat(allWeights()).isNotEqualTo(before);
        jdbc.update("DELETE FROM article_engagement WHERE article_id = ?", article);

        assertThat(allWeights()).isEqualTo(before);
    }

    @Test
    void thumbsAndEngagementAddWithinTheirOwnCaps() {
        for (int i = 0; i < 12; i++) {
            insertFeedback(matching(go, 1.0), 1, false);
        }
        for (int i = 0; i < 10; i++) {
            insertEngagement(matching(go, 1.0), "STAR");
        }

        assertThat(effective(go)).isCloseTo(38.0, within(1e-6));
    }

    @Test
    void savingOneArticleRaisesAnotherMatchingArticlesBadge() {
        long unengaged = insertArticle(false, now.minus(Duration.ofHours(1)));
        insertScored(unengaged, 2.0, 4);
        insertTopicScore(unengaged, rust, 0.95);
        assertThat(queries.displayScores(List.of(unengaged))).containsEntry(unengaged, 68);

        insertEngagement(matching(rust, 0.95), "STAR");

        assertThat(queries.displayScores(List.of(unengaged))).containsEntry(unengaged, 69);
    }

    @Test
    void readingTwiceWritesNothing() {
        long saved = matching(rust, 0.95);
        insertEngagement(saved, "STAR");
        insertEngagement(saved, "OPEN_ORIGINAL");
        insertEngagement(matching(go, 0.8), "OPEN_ORIGINAL");
        insertFeedback(matching(go, 0.95), 1, false);
        long engagementRows = count("SELECT count(*) FROM article_engagement");
        long weightSum = count("SELECT COALESCE(SUM(weight), 0) FROM interest_topic");
        long scoreRows = count("SELECT count(*) FROM article_score");

        List<TopicWeight> first = queries.allTopicWeights();
        List<TopicWeight> second = queries.allTopicWeights();

        assertThat(second).isEqualTo(first);
        assertThat(count("SELECT count(*) FROM article_engagement")).isEqualTo(engagementRows);
        assertThat(count("SELECT COALESCE(SUM(weight), 0) FROM interest_topic")).isEqualTo(weightSum);
        assertThat(count("SELECT count(*) FROM article_score")).isEqualTo(scoreRows);
    }

    private void assertWeights(double rustWeight, double goWeight) {
        Map<Long, TopicWeight> w = queries.topicWeights(List.of(rust, go));
        assertThat(w.get(rust).effective()).as("rust").isCloseTo(rustWeight, within(1e-6));
        assertThat(w.get(go).effective()).as("go").isCloseTo(goWeight, within(1e-6));
    }

    private double effective(long topicId) {
        return queries.topicWeights(List.of(topicId)).get(topicId).effective();
    }

    private Map<Long, TopicWeight> allWeights() {
        return queries.topicWeights(List.of(rust, go, zero, politics));
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    /** One unread SCORED article (no profile) that judged only {@code topicId}, at {@code noul}. */
    private long matching(long topicId, double noul) {
        long id = insertArticle(false, now.minus(Duration.ofHours(1)));
        insertScored(id, null, null);
        insertTopicScore(id, topicId, noul);
        return id;
    }

    private long insertArticle(boolean read, Instant publishedAt) {
        String guid = "e" + (guidSeq++);
        return jdbc.queryForObject(
                "INSERT INTO article (feed_id, guid, title, url, summary, content, published_at, fetched_at, \"read\") "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class, feedId, guid, "Title " + guid, "https://example.com/" + guid,
                "Summary " + guid, "Content " + guid, Timestamp.from(publishedAt), Timestamp.from(now), read);
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
