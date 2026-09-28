package org.bartram.myfeeder.repository;

import org.bartram.myfeeder.TestcontainersConfiguration;
import org.bartram.myfeeder.model.InterestProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the V6 schema shape, constraints and cascades against real Postgres.
 * Postgres aborts the test transaction after a constraint error, so each method
 * triggers at most one expected violation, as its last statement.
 */
@DataJdbcTest
@Import(TestcontainersConfiguration.class)
class V6InterestScoringMigrationTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private InterestProfileRepository profileRepository;

    private long feedId;

    @BeforeEach
    void setUp() {
        feedId = jdbc.queryForObject(
                "INSERT INTO feed (url, title, feed_type) VALUES (?, ?, ?) RETURNING id",
                Long.class, "https://example.com/feed.xml", "Test Feed", "RSS");
    }

    @Test
    void seededProfileRoundTripsThroughRepository() {
        InterestProfile profile = profileRepository.findById(1).orElseThrow();
        assertThat(profile.getProfileText()).isEmpty();
        assertThat(profile.getVersion()).isEqualTo(1);

        profile.setProfileText("Rust and Postgres");
        profileRepository.save(profile);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM interest_profile", Integer.class)).isEqualTo(1);
        assertThat(profileRepository.findById(1).orElseThrow().getProfileText()).isEqualTo("Rust and Postgres");
    }

    @Test
    void rejectsSecondProfileRow() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO interest_profile (id) VALUES (2)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void topicDefaultsAndWeightBoundsAccepted() {
        long id = jdbc.queryForObject(
                "INSERT INTO interest_topic (name, description) VALUES (?, ?) RETURNING id",
                Long.class, "Rust", "Articles about the Rust language");
        assertThat(jdbc.queryForObject("SELECT weight FROM interest_topic WHERE id = ?", Integer.class, id))
                .isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT version FROM interest_topic WHERE id = ?", Integer.class, id))
                .isEqualTo(1);

        assertThat(insertTopicWithWeight(50)).isPositive();
        assertThat(insertTopicWithWeight(-50)).isPositive();
    }

    @Test
    void rejectsTopicWeightAbove50() {
        assertThatThrownBy(() -> insertTopicWithWeight(51))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsTopicWeightBelowMinus50() {
        assertThatThrownBy(() -> insertTopicWithWeight(-51))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsTopicWithoutName() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO interest_topic (description) VALUES (?)", "No name given"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void scoreStatusAcceptsScoredFailedAndSkipped() {
        insertScore(insertArticle("g-scored"), "SCORED");
        insertScore(insertArticle("g-failed"), "FAILED");
        insertScore(insertArticle("g-skipped"), "SKIPPED");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM article_score", Integer.class)).isEqualTo(3);
    }

    @Test
    void rejectsUnknownScoreStatus() {
        long articleId = insertArticle("g-pending");
        assertThatThrownBy(() -> insertScore(articleId, "PENDING"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsTopicScoreWithoutParentScoreRow() {
        long articleId = insertArticle("g-orphan");
        long topicId = insertTopic("Rust");
        assertThatThrownBy(() -> insertTopicScore(articleId, topicId, 0.5))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsNoulOutsideZeroToOne() {
        long articleId = insertArticle("g-noul");
        insertScore(articleId, "SCORED");
        long topicId = insertTopic("Rust");
        assertThatThrownBy(() -> insertTopicScore(articleId, topicId, 1.5))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingScoreRowCascadesTopicScores() {
        long articleId = insertArticle("g-rescore");
        insertScore(articleId, "SCORED");
        insertTopicScore(articleId, insertTopic("Rust"), 0.8);
        insertTopicScore(articleId, insertTopic("Postgres"), 0.2);

        jdbc.update("DELETE FROM article_score WHERE article_id = ?", articleId);

        assertThat(countWhere("article_topic_score", "article_id", articleId)).isZero();
        assertThat(countWhere("article", "id", articleId)).isEqualTo(1);
    }

    @Test
    void deletingTopicCascadesTopicScoresAndFeedbackPicks() {
        long articleId = insertArticle("g-topic-delete");
        long topicId = insertTopic("Rust");
        long keptTopicId = insertTopic("Postgres");
        insertScore(articleId, "SCORED");
        insertTopicScore(articleId, topicId, 0.7);
        insertTopicScore(articleId, keptTopicId, 0.3);
        insertFeedback(articleId, -1, true);
        insertFeedbackTopic(articleId, topicId);

        jdbc.update("DELETE FROM interest_topic WHERE id = ?", topicId);

        assertThat(countWhere("article_topic_score", "topic_id", topicId)).isZero();
        assertThat(countWhere("article_feedback_topic", "topic_id", topicId)).isZero();
        assertThat(countWhere("article_topic_score", "topic_id", keptTopicId)).isEqualTo(1);
        assertThat(countWhere("article_score", "article_id", articleId)).isEqualTo(1);
        assertThat(countWhere("article_feedback", "article_id", articleId)).isEqualTo(1);
    }

    @Test
    void deletingArticleCascadesScoresFeedbackAndChildren() {
        long articleId = insertArticle("g-article-delete");
        long topicId = insertTopic("Rust");
        insertScore(articleId, "SCORED");
        insertTopicScore(articleId, topicId, 0.9);
        insertFeedback(articleId, -1, true);
        insertFeedbackTopic(articleId, topicId);

        jdbc.update("DELETE FROM article WHERE id = ?", articleId);

        assertThat(countWhere("article_score", "article_id", articleId)).isZero();
        assertThat(countWhere("article_topic_score", "article_id", articleId)).isZero();
        assertThat(countWhere("article_feedback", "article_id", articleId)).isZero();
        assertThat(countWhere("article_feedback_topic", "article_id", articleId)).isZero();
        assertThat(countWhere("interest_topic", "id", topicId)).isEqualTo(1);
    }

    @Test
    void feedbackDefaultsTopicsNarrowedFalse() {
        long articleId = insertArticle("g-feedback");
        jdbc.update("INSERT INTO article_feedback (article_id, vote) VALUES (?, ?)", articleId, 1);

        assertThat(jdbc.queryForObject(
                "SELECT topics_narrowed FROM article_feedback WHERE article_id = ?", Boolean.class, articleId))
                .isFalse();
    }

    @Test
    void narrowedUpVoteWithPickIsAccepted() {
        long articleId = insertArticle("g-upvote-narrowed");
        long topicId = insertTopic("Rust");
        insertFeedback(articleId, 1, true);
        insertFeedbackTopic(articleId, topicId);

        assertThat(countWhere("article_feedback_topic", "article_id", articleId)).isEqualTo(1);
    }

    @Test
    void rejectsZeroVote() {
        long articleId = insertArticle("g-zero-vote");
        assertThatThrownBy(() -> insertFeedback(articleId, 0, false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long insertArticle(String guid) {
        return jdbc.queryForObject(
                "INSERT INTO article (feed_id, guid, title, url) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, feedId, guid, "Title " + guid, "https://example.com/" + guid);
    }

    private long insertTopic(String name) {
        return jdbc.queryForObject(
                "INSERT INTO interest_topic (name, description) VALUES (?, ?) RETURNING id",
                Long.class, name, "Articles about " + name);
    }

    private long insertTopicWithWeight(int weight) {
        return jdbc.queryForObject(
                "INSERT INTO interest_topic (name, description, weight) VALUES (?, ?, ?) RETURNING id",
                Long.class, "Weighted", "Weight " + weight, weight);
    }

    private void insertScore(long articleId, String status) {
        jdbc.update("INSERT INTO article_score (article_id, status) VALUES (?, ?)", articleId, status);
    }

    private void insertTopicScore(long articleId, long topicId, double noul) {
        jdbc.update(
                "INSERT INTO article_topic_score (article_id, topic_id, noul, topic_version) VALUES (?, ?, ?, 1)",
                articleId, topicId, noul);
    }

    private void insertFeedback(long articleId, int vote, boolean narrowed) {
        jdbc.update(
                "INSERT INTO article_feedback (article_id, vote, topics_narrowed) VALUES (?, ?, ?)",
                articleId, vote, narrowed);
    }

    private void insertFeedbackTopic(long articleId, long topicId) {
        jdbc.update("INSERT INTO article_feedback_topic (article_id, topic_id) VALUES (?, ?)", articleId, topicId);
    }

    private int countWhere(String table, String column, long value) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, value);
    }
}
