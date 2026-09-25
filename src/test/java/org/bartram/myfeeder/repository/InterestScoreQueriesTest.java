package org.bartram.myfeeder.repository;

import org.bartram.myfeeder.TestcontainersConfiguration;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.model.Article;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the blend CTE and the Priority keyset pages against real Postgres on a fixed fixture.
 *
 * <p>Topics: Rust +20, WebAssembly +14, Politics -30, Zero 0, Gardening +10. Expected raws: a4 133.32;
 * a1, a2, a3 82.2 (tied); a8 75; a6 28.5; a7 10; a5 -30. Unscored: u1 (no row), u2 (FAILED), u3
 * (SKIPPED), u4 (no row, outside the window), u5 (no row, undated). r1 and r2 are read.
 */
@DataJdbcTest
@Import({TestcontainersConfiguration.class, InterestScoreQueries.class})
@EnableConfigurationProperties(MyfeederProperties.class)
class InterestScoreQueriesTest {

    @Autowired private InterestScoreQueries queries;
    @Autowired private JdbcTemplate jdbc;

    private long feedId;
    private Instant now;

    private long rust, webAssembly, politics, zero, gardening;
    private long a1, a2, a3, a4, a5, a6, a7, a8, r1;
    private long u1, u2, u3, u4, u5, r2;

    @BeforeEach
    void setUp() {
        // Inside the rolled-back test transaction, so the fixture is the whole table.
        jdbc.update("DELETE FROM article_score");
        jdbc.update("DELETE FROM article");
        jdbc.update("DELETE FROM interest_topic");
        feedId = jdbc.queryForObject(
                "INSERT INTO feed (url, title, feed_type) VALUES (?, ?, ?) RETURNING id",
                Long.class, "https://example.com/priority-feed.xml", "Priority Feed", "RSS");
        now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        seedFixture();
    }

    @Test
    void fullOrderIsScoreThenDateThenId() {
        assertThat(ids(queries.priorityFirstPage(100)))
                .containsExactly(a4, a3, a1, a2, a8, a6, a7, a5, u1, u3, u5, u2, u4);
    }

    @Test
    void pagedWalksMatchTheUnpagedOrder() {
        List<Long> full = ids(queries.priorityFirstPage(100));
        for (int n : new int[] {3, 5}) {
            assertThat(walk(n)).as("walk in pages of %d", n).containsExactlyElementsOf(full);
        }
    }

    @Test
    void cursorReadBetweenPagesStillContinues() {
        List<Article> page1 = queries.priorityFirstPage(3);
        List<Article> page2 = queries.priorityPageAfter(page1.getLast().getId(), 3);
        assertThat(ids(page2)).containsExactly(a2, a8, a6);

        markRead(a6); // scored cursor
        List<Article> page3 = queries.priorityPageAfter(a6, 3);
        assertThat(ids(page3)).containsExactly(a7, a5, u1);

        markRead(u1); // unscored cursor
        assertThat(ids(queries.priorityPageAfter(u1, 3))).containsExactly(u3, u5, u2);
    }

    @Test
    void unscoredRowsHaveNullInterestScore() {
        Map<Long, Integer> scores = scores(queries.priorityFirstPage(100));
        for (long unscored : List.of(u1, u2, u3, u4, u5)) {
            assertThat(scores).containsKey(unscored);
            assertThat(scores.get(unscored)).as("interestScore of %d", unscored).isNull();
        }
    }

    @Test
    void displayIsClampedAndRoundsHalfAwayFromZero() {
        Map<Long, Integer> scores = scores(queries.priorityFirstPage(100));
        assertThat(scores.get(a4)).isEqualTo(100);
        assertThat(scores.get(a5)).isEqualTo(0);
        assertThat(scores.get(a6)).isEqualTo(29);
        assertThat(scores.get(a1)).isEqualTo(82);
        assertThat(scores.get(a7)).isEqualTo(10);
        assertThat(scores.get(a8)).isEqualTo(75);
    }

    @Test
    void readArticlesAreExcluded() {
        assertThat(ids(queries.priorityFirstPage(100))).doesNotContain(r1, r2);
        assertThat(ids(queries.priorityPageAfter(a4, 100))).doesNotContain(r1, r2);
    }

    @Test
    void weightChangeReordersWithoutRescoring() {
        jdbc.update("UPDATE interest_topic SET weight = 30 WHERE name = 'Politics'");

        List<Article> page = queries.priorityFirstPage(100);
        Map<Long, Integer> scores = scores(page);
        assertThat(scores.get(a1)).isEqualTo(94);
        assertThat(scores.get(a2)).isEqualTo(94);
        assertThat(scores.get(a3)).isEqualTo(94);
        assertThat(scores.get(a5)).isEqualTo(30);
        assertThat(ids(page)).containsExactly(a4, a3, a1, a2, a8, a5, a6, a7, u1, u3, u5, u2, u4);
    }

    @Test
    void topicAddedAfterScoringContributesNothing() {
        List<Long> before = ids(queries.priorityFirstPage(100));
        insertTopic("Later", 50);

        List<Article> page = queries.priorityFirstPage(100);
        assertThat(scores(page).get(a1)).isEqualTo(82);
        assertThat(ids(page)).containsExactlyElementsOf(before);
    }

    @Test
    void deletedTopicContributionCascadesAway() {
        jdbc.update("DELETE FROM interest_topic WHERE name = 'WebAssembly'");

        Map<Long, Integer> scores = scores(queries.priorityFirstPage(100));
        assertThat(scores.get(a1)).isEqualTo(75);
        assertThat(scores.get(a6)).isEqualTo(25);
        assertThat(scores.get(a4)).isEqualTo(100);
    }

    @Test
    void emptyWhenNoUnreadArticles() {
        jdbc.update("UPDATE article SET \"read\" = true");

        assertThat(queries.priorityFirstPage(100)).isEmpty();
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
        u3 = insertArticle("u3", false, now.minus(Duration.ofHours(2)), now);
        u4 = insertArticle("u4", false, now.minus(Duration.ofDays(20)), now);
        u5 = insertArticle("u5", false, null, now.minus(Duration.ofHours(3)));
        r2 = insertArticle("r2", true, now.minus(Duration.ofHours(1)), now);

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
        insertStatus(u3, "SKIPPED");

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
    }

    private List<Long> walk(int n) {
        List<Long> walked = new ArrayList<>();
        List<Article> page = queries.priorityFirstPage(n);
        while (true) {
            walked.addAll(ids(page));
            if (page.size() < n) {
                return walked;
            }
            page = queries.priorityPageAfter(page.getLast().getId(), n);
        }
    }

    private void markRead(long articleId) {
        jdbc.update("UPDATE article SET \"read\" = true WHERE id = ?", articleId);
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

    private static List<Long> ids(List<Article> articles) {
        return articles.stream().map(Article::getId).toList();
    }

    /** id to interestScore; a HashMap because unscored rows carry null values. */
    private static Map<Long, Integer> scores(List<Article> articles) {
        Map<Long, Integer> scores = new HashMap<>();
        articles.forEach(a -> scores.put(a.getId(), a.getInterestScore()));
        return scores;
    }
}
