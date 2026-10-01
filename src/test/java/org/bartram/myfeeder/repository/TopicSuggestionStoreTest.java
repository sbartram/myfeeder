package org.bartram.myfeeder.repository;

import org.bartram.myfeeder.TestcontainersConfiguration;
import org.bartram.myfeeder.repository.TopicSuggestionStore.Candidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the D-08 gap predicate against real Postgres: SCORED only, no vote, no dismissal, best noul below
 * the near-miss (exclusive, over every topic, either weight sign), and a strict window on the latest
 * engagement. The fixture DELETEs run inside the rolled-back test transaction, so the fixture is the whole
 * table.
 */
@DataJdbcTest
@Import({TestcontainersConfiguration.class, TopicSuggestionStore.class})
class TopicSuggestionStoreTest {

    private static final double NEAR_MISS = 0.35;

    @Autowired private TopicSuggestionStore store;
    @Autowired private JdbcTemplate jdbc;

    private long feedId;
    private Instant now;
    private Instant cutoff;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM article_score");
        jdbc.update("DELETE FROM article");
        jdbc.update("DELETE FROM interest_topic");
        feedId = jdbc.queryForObject(
                "INSERT INTO feed (url, title, feed_type) VALUES (?, ?, ?) RETURNING id",
                Long.class, "https://example.com/suggestion-store-feed.xml", "Store Feed", "RSS");
        now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        cutoff = now.minus(Duration.ofDays(30));
    }

    @Test
    void engagedScoredUnmatchedArticleIsACandidate() {
        long a = article("a");
        scored(a, "SCORED");
        topicScore(a, topic("rust", 20), 0.2);
        engage(a, "OPEN_ORIGINAL", now.minus(Duration.ofDays(1)));

        List<Candidate> candidates = store.candidates(cutoff, NEAR_MISS);

        assertThat(ids(candidates)).containsExactly(a);
        assertThat(candidates.get(0).title()).isEqualTo("Article a");
    }

    @Test
    void anArticleWithNoTopicRowsIsACandidate() {
        long a = article("a");
        scored(a, "SCORED");
        engage(a, "STAR", now.minus(Duration.ofDays(1)));

        assertThat(ids(store.candidates(cutoff, NEAR_MISS))).containsExactly(a);
    }

    @Test
    void unscoredFailedAndSkippedArticlesAreNot() {
        long unscored = article("unscored");
        engage(unscored, "OPEN_ORIGINAL", now);
        long failed = article("failed");
        scored(failed, "FAILED");
        engage(failed, "OPEN_ORIGINAL", now);
        long skipped = article("skipped");
        scored(skipped, "SKIPPED");
        engage(skipped, "OPEN_ORIGINAL", now);

        assertThat(store.candidates(cutoff, NEAR_MISS)).isEmpty();
    }

    @Test
    void anyVoteExcludes() {
        long up = engagedScored("up");
        vote(up, 1);
        long down = engagedScored("down");
        vote(down, -1);
        long kept = engagedScored("kept");

        assertThat(ids(store.candidates(cutoff, NEAR_MISS))).containsExactly(kept);
    }

    @Test
    void anyDismissalExcludes() {
        long dismissed = engagedScored("dismissed");
        dismissal(dismissed, "DISMISSED");
        long created = engagedScored("created");
        dismissal(created, "TOPIC_CREATED");
        long kept = engagedScored("kept");

        assertThat(ids(store.candidates(cutoff, NEAR_MISS))).containsExactly(kept);
    }

    @Test
    void nearMissIsExclusiveAtTheThreshold() {
        long rust = topic("rust", 20);
        long at = engagedScored("at");
        topicScore(at, rust, 0.35);
        long below = engagedScored("below");
        topicScore(below, rust, 0.3499);

        assertThat(ids(store.candidates(cutoff, NEAR_MISS))).containsExactly(below);
    }

    @Test
    void aMatchedArticleIsNot() {
        long rust = topic("rust", 20);
        long matched = engagedScored("matched");
        topicScore(matched, rust, 0.6);
        long half = engagedScored("half");
        topicScore(half, rust, 0.5);

        assertThat(store.candidates(cutoff, NEAR_MISS)).isEmpty();
        assertThat(store.candidates(cutoff, 0.5)).isEmpty();
    }

    @Test
    void theBestNoulAcrossTopicsCounts() {
        long a = engagedScored("a");
        topicScore(a, topic("rust", 20), 0.1);
        topicScore(a, topic("go", 10), 0.36);

        assertThat(store.candidates(cutoff, NEAR_MISS)).isEmpty();
    }

    @Test
    void aNegativeWeightTopicCoversTheArticle() {
        long a = engagedScored("a");
        topicScore(a, topic("politics", -30), 0.4);

        assertThat(store.candidates(cutoff, NEAR_MISS)).isEmpty();
    }

    @Test
    void theWindowIsStrictOnTheLatestEngagement() {
        Instant t = now.minus(Duration.ofDays(10));
        long a = article("a");
        scored(a, "SCORED");
        engage(a, "OPEN_ORIGINAL", t);

        assertThat(store.candidates(t, NEAR_MISS)).isEmpty();
        assertThat(ids(store.candidates(t.minusSeconds(1), NEAR_MISS))).containsExactly(a);
    }

    @Test
    void aNewerKindBringsAnOldArticleBack() {
        Instant starred = now.minus(Duration.ofDays(2));
        long a = article("a");
        scored(a, "SCORED");
        engage(a, "OPEN_ORIGINAL", now.minus(Duration.ofDays(40)));
        engage(a, "STAR", starred);

        List<Candidate> candidates = store.candidates(cutoff, NEAR_MISS);

        assertThat(ids(candidates)).containsExactly(a);
        assertThat(candidates.get(0).engagedAt()).isEqualTo(starred);
    }

    @Test
    void anArticleEngagedOnlyBeforeTheWindowIsNot() {
        long a = article("a");
        scored(a, "SCORED");
        engage(a, "OPEN_ORIGINAL", now.minus(Duration.ofDays(31)));

        assertThat(store.candidates(cutoff, NEAR_MISS)).isEmpty();
    }

    @Test
    void oneCandidatePerArticleWithItsFeedTitle() {
        long a = article("a");
        scored(a, "SCORED");
        for (String kind : List.of("OPEN_ORIGINAL", "STAR", "BOARD", "RAINDROP")) {
            engage(a, kind, now.minus(Duration.ofHours(1)));
        }

        List<Candidate> candidates = store.candidates(cutoff, NEAR_MISS);

        assertThat(ids(candidates)).containsExactly(a);
        assertThat(candidates.get(0).feedTitle()).isEqualTo("Store Feed");
    }

    @Test
    void readStateAndPublicationDateDoNotMatter() {
        long a = article("a", true, now.minus(Duration.ofDays(60)));
        scored(a, "SCORED");
        engage(a, "BOARD", now.minus(Duration.ofMinutes(5)));

        assertThat(ids(store.candidates(cutoff, NEAR_MISS))).containsExactly(a);
    }

    /** A SCORED article with no topic rows and a fresh OPEN_ORIGINAL. */
    private long engagedScored(String guid) {
        long id = article(guid);
        scored(id, "SCORED");
        engage(id, "OPEN_ORIGINAL", now.minus(Duration.ofHours(1)));
        return id;
    }

    private long article(String guid) {
        return article(guid, false, now.minus(Duration.ofDays(1)));
    }

    private long article(String guid, boolean read, Instant publishedAt) {
        return jdbc.queryForObject(
                "INSERT INTO article (feed_id, guid, title, url, published_at, read) "
                        + "VALUES (?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class, feedId, "suggestion-store-" + guid, "Article " + guid,
                "https://example.com/suggestion-store-" + guid, Timestamp.from(publishedAt), read);
    }

    /** Profile score 0 of max level 4. */
    private void scored(long articleId, String status) {
        jdbc.update("INSERT INTO article_score (article_id, status, profile_score, profile_max_level, attempts) "
                + "VALUES (?, ?, 0, 4, 1)", articleId, status);
    }

    private long topic(String name, int weight) {
        return jdbc.queryForObject(
                "INSERT INTO interest_topic (name, description, weight) VALUES (?, ?, ?) RETURNING id",
                Long.class, name, "Store test", weight);
    }

    private void topicScore(long articleId, long topicId, double noul) {
        jdbc.update("INSERT INTO article_topic_score (article_id, topic_id, noul, topic_version) VALUES (?, ?, ?, 1)",
                articleId, topicId, noul);
    }

    private void engage(long articleId, String kind, Instant createdAt) {
        jdbc.update("INSERT INTO article_engagement (article_id, kind, created_at) VALUES (?, ?, ?)",
                articleId, kind, Timestamp.from(createdAt));
    }

    private void vote(long articleId, int vote) {
        jdbc.update("INSERT INTO article_feedback (article_id, vote) VALUES (?, ?)", articleId, vote);
    }

    private void dismissal(long articleId, String reason) {
        jdbc.update("INSERT INTO topic_suggestion_dismissal (article_id, reason) VALUES (?, ?)", articleId, reason);
    }

    private static List<Long> ids(List<Candidate> candidates) {
        return candidates.stream().map(Candidate::articleId).toList();
    }
}
