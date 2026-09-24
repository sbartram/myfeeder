package org.bartram.myfeeder.repository;

import org.bartram.myfeeder.TestcontainersConfiguration;
import org.bartram.myfeeder.repository.ArticleScoreStore.Candidate;
import org.bartram.myfeeder.repository.ArticleScoreStore.ScoredRow;
import org.bartram.myfeeder.repository.ArticleScoreStore.TopicNoul;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the scoring queue's SQL against real Postgres.
 * Postgres aborts the test transaction after a constraint error, so each method
 * triggers at most one expected violation, as its last statement.
 */
@DataJdbcTest
@Import({TestcontainersConfiguration.class, ArticleScoreStore.class})
class ArticleScoreStoreTest {

    @Autowired private ArticleScoreStore store;
    @Autowired private JdbcTemplate jdbc;

    private long feedId;
    private Instant now;
    private Instant cutoff;

    @BeforeEach
    void setUp() {
        // Inside the rolled-back test transaction, so counts are exact.
        jdbc.update("DELETE FROM article_score");
        jdbc.update("DELETE FROM article");
        feedId = jdbc.queryForObject(
                "INSERT INTO feed (url, title, feed_type) VALUES (?, ?, ?) RETURNING id",
                Long.class, "https://example.com/feed.xml", "Test Feed", "RSS");
        // Whole seconds, so Postgres microsecond rounding can never move a boundary row across the cutoff.
        now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        cutoff = now.minus(Duration.ofDays(14));
    }

    @Test
    void selectsEligibleUnscoredNewestFirst() {
        long oneHour = insertArticle("g-1h", now.minus(Duration.ofHours(1)));
        long threeHours = insertArticle("g-3h", now.minus(Duration.ofHours(3)));
        long undatedTwoHours = insertArticle("g-undated", false, null, now.minus(Duration.ofHours(2)));
        long fiveHoursFirst = insertArticle("g-5h-a", now.minus(Duration.ofHours(5)));
        long fiveHoursSecond = insertArticle("g-5h-b", now.minus(Duration.ofHours(5)));

        assertThat(store.findNeedingScoring(cutoff, 10))
                .containsExactly(oneHour, undatedTwoHours, threeHours, fiveHoursSecond, fiveHoursFirst);
    }

    @Test
    void excludesReadAndOutOfWindowArticles() {
        insertArticle("g-read", true, now.minus(Duration.ofHours(1)), now);
        insertArticle("g-old", now.minus(Duration.ofDays(20)));
        insertArticle("g-at-cutoff", cutoff);
        long justInside = insertArticle("g-inside", cutoff.plusSeconds(1));

        assertThat(store.findNeedingScoring(cutoff, 10)).containsExactly(justInside);
    }

    @Test
    void limitCapsTheSelection() {
        long newest = insertArticle("g-1", now.minus(Duration.ofHours(1)));
        long second = insertArticle("g-2", now.minus(Duration.ofHours(2)));
        insertArticle("g-3", now.minus(Duration.ofHours(3)));
        insertArticle("g-4", now.minus(Duration.ofHours(4)));
        insertArticle("g-5", now.minus(Duration.ofHours(5)));

        assertThat(store.findNeedingScoring(cutoff, 2)).containsExactly(newest, second);
    }

    @Test
    void scoredArticleIsStoredAndNoLongerSelected() {
        long articleId = insertArticle("g-scored", now.minus(Duration.ofHours(1)));
        long topicA = insertTopic("Java");
        long topicB = insertTopic("Postgres");

        assertThat(store.writeScored(articleId, scoreRow(topicA, topicB))).isTrue();

        Map<String, Object> score = scoreRowFor(articleId);
        assertThat(score.get("status")).isEqualTo("SCORED");
        assertThat(score.get("profile_score")).isEqualTo(3.0);
        assertThat(score.get("profile_max_level")).isEqualTo(4);
        assertThat(score.get("profile_confidence")).isEqualTo(0.8);
        assertThat(score.get("profile_version")).isEqualTo(2);
        assertThat(score.get("model")).isEqualTo("jev-1.13.0");
        assertThat(score.get("request_id")).isEqualTo("req-1");
        assertThat(score.get("attempts")).isEqualTo(1);
        assertThat(score.get("last_error")).isNull();

        List<Map<String, Object>> topics = jdbc.queryForList(
                "SELECT topic_id, noul, topic_version FROM article_topic_score WHERE article_id = ? ORDER BY topic_id",
                articleId);
        assertThat(topics).hasSize(2);
        assertThat(topics).allSatisfy(t -> {
            assertThat(t.get("noul")).isEqualTo(0.9);
            assertThat(t.get("topic_version")).isEqualTo(1);
        });
        assertThat(topics).extracting(t -> ((Number) t.get("topic_id")).longValue())
                .containsExactly(topicA, topicB);

        assertThat(store.findNeedingScoring(cutoff, 10)).doesNotContain(articleId);
        assertThat(store.loadCandidate(articleId, cutoff)).isEmpty();
    }

    @Test
    void loadCandidateCarriesArticleTextAndFeedTitle() {
        long articleId = insertArticle("g-text", now.minus(Duration.ofHours(1)));
        long readId = insertArticle("g-read", true, now.minus(Duration.ofHours(1)), now);

        Optional<Candidate> candidate = store.loadCandidate(articleId, cutoff);

        assertThat(candidate).isPresent();
        assertThat(candidate.get().feedTitle()).isEqualTo("Test Feed");
        assertThat(candidate.get().article().getId()).isEqualTo(articleId);
        assertThat(candidate.get().article().getFeedId()).isEqualTo(feedId);
        assertThat(candidate.get().article().getGuid()).isEqualTo("g-text");
        assertThat(candidate.get().article().getTitle()).isEqualTo("Title g-text");
        assertThat(candidate.get().article().getSummary()).isEqualTo("Summary g-text");
        assertThat(candidate.get().article().getContent()).isEqualTo("Content g-text");
        assertThat(candidate.get().article().getPublishedAt()).isEqualTo(now.minus(Duration.ofHours(1)));
        assertThat(store.loadCandidate(readId, cutoff)).isEmpty();
    }

    private long insertArticle(String guid, Instant publishedAt) {
        return insertArticle(guid, false, publishedAt, now);
    }

    private long insertArticle(String guid, boolean read, Instant publishedAt, Instant fetchedAt) {
        return jdbc.queryForObject(
                "INSERT INTO article (feed_id, guid, title, url, summary, content, published_at, fetched_at, \"read\") "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class, feedId, guid, "Title " + guid, "https://example.com/" + guid,
                "Summary " + guid, "Content " + guid,
                publishedAt == null ? null : Timestamp.from(publishedAt), Timestamp.from(fetchedAt), read);
    }

    private long insertTopic(String name) {
        return jdbc.queryForObject(
                "INSERT INTO interest_topic (name, description) VALUES (?, ?) RETURNING id",
                Long.class, name, "Articles about " + name);
    }

    private ScoredRow scoreRow(Long... topicIds) {
        List<TopicNoul> topics = Arrays.stream(topicIds).map(id -> new TopicNoul(id, 0.9, 1)).toList();
        return new ScoredRow(3.0, 4, 0.8, 2, "jev-1.13.0", "req-1", topics);
    }

    private Map<String, Object> scoreRowFor(long articleId) {
        return jdbc.queryForMap("SELECT * FROM article_score WHERE article_id = ?", articleId);
    }
}
