package org.bartram.myfeeder.controller;

import com.jayway.jsonpath.JsonPath;
import org.bartram.myfeeder.TestcontainersConfiguration;
import org.bartram.myfeeder.integration.JevApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end: MockMvc → ArticleController → ArticleFeedbackService → ArticleFeedbackStore and
 * InterestScoreQueries → Testcontainers Postgres. The container is shared with other test classes, so
 * assertions only read topics and articles seeded here. Jev is mocked and must never be called.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class FeedbackApiIntegrationTest {

    private static final String FEEDBACK_FEED_URL = "https://example.test/feedback-it-feed.xml";
    private static final String FEEDBACK_TOPIC_PREFIX = "feedback-it-";

    @Autowired private WebApplicationContext wac;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private JevApiClient jevApiClient;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        // Articles, score rows, topic-score rows and feedback rows cascade from the feed
        jdbcTemplate.update("DELETE FROM feed WHERE url = ?", FEEDBACK_FEED_URL);
        jdbcTemplate.update("DELETE FROM interest_topic WHERE name LIKE ?", FEEDBACK_TOPIC_PREFIX + "%");
    }

    @Test
    void upVoteRaisesMatchedTopicsAndTheBadge() throws Exception {
        long feedId = insertFeed();
        long rust = insertTopic(FEEDBACK_TOPIC_PREFIX + "rust", 20);
        long a = insertArticle(feedId, "a", Instant.parse("2026-09-20T10:00:00Z"));
        long b = insertArticle(feedId, "b", Instant.parse("2026-09-20T11:00:00Z"));
        insertScored(a, 2.0, 4);
        insertTopicScore(a, rust, 0.95);
        insertScored(b, 2.0, 4);
        insertTopicScore(b, rust, 0.95);
        assertThat(badge(a)).isEqualTo(68);

        String body = putVote(a, "{\"vote\":1}");

        assertThat((Boolean) JsonPath.read(body, "$.scored")).isTrue();
        List<Map<String, Object>> effects = JsonPath.read(body, "$.effects");
        assertThat(effects).hasSize(1);
        assertThat(((Number) effects.get(0).get("topicId")).longValue()).isEqualTo(rust);
        assertThat(effects.get(0).get("name")).isEqualTo(FEEDBACK_TOPIC_PREFIX + "rust");
        assertThat(((Number) effects.get(0).get("before")).doubleValue()).isEqualTo(20.0);
        assertThat(((Number) effects.get(0).get("after")).doubleValue()).isEqualTo(21.8);
        assertThat(((Number) effects.get(0).get("baseWeight")).doubleValue()).isEqualTo(20.0);
        assertThat(((Number) effects.get(0).get("learned")).doubleValue()).isEqualTo(1.8);
        assertThat((Integer) JsonPath.read(body, "$.article.interestScore")).isEqualTo(70);

        assertThat(badge(a)).isEqualTo(70);
        assertThat(badge(b)).isEqualTo(70);
        assertThat(topicWeight(rust)).isEqualTo(20);
        assertThat(storedFeedback(a)).containsExactly("vote=1 narrowed=false");
        verify(jevApiClient, never()).judge(any(), any());
    }

    private String putVote(long articleId, String json) throws Exception {
        return mockMvc.perform(put("/api/articles/{id}/feedback", articleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private Integer badge(long articleId) throws Exception {
        String body = mockMvc.perform(get("/api/articles/{id}", articleId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.interestScore");
    }

    /** The article's stored feedback rows as "vote=V narrowed=N" strings (at most one by the primary key). */
    private List<String> storedFeedback(long articleId) {
        return jdbcTemplate.query("SELECT vote, topics_narrowed FROM article_feedback WHERE article_id = ?",
                (rs, rowNum) -> "vote=" + rs.getInt("vote") + " narrowed=" + rs.getBoolean("topics_narrowed"),
                articleId);
    }

    private int topicWeight(long topicId) {
        return jdbcTemplate.queryForObject("SELECT weight FROM interest_topic WHERE id = ?", Integer.class, topicId);
    }

    private long insertFeed() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO feed (url, title, feed_type) VALUES (?, ?, ?) RETURNING id",
                Long.class, FEEDBACK_FEED_URL, "Feedback Feed", "RSS");
    }

    private long insertArticle(long feedId, String guid, Instant publishedAt) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO article (feed_id, guid, title, url, summary, published_at, read) "
                        + "VALUES (?, ?, ?, ?, ?, ?, false) RETURNING id",
                Long.class, feedId, "feedback-" + guid, "Article " + guid, "https://example.test/feedback-" + guid,
                "Summary " + guid, Timestamp.from(publishedAt));
    }

    private long insertTopic(String name, int weight) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO interest_topic (name, description, weight) VALUES (?, ?, ?) RETURNING id",
                Long.class, name, "Feedback test", weight);
    }

    private void insertTopicScore(long articleId, long topicId, double noul) {
        jdbcTemplate.update(
                "INSERT INTO article_topic_score (article_id, topic_id, noul, topic_version) VALUES (?, ?, ?, 1)",
                articleId, topicId, noul);
    }

    private void insertScored(long articleId, double profileScore, int profileMaxLevel) {
        jdbcTemplate.update(
                "INSERT INTO article_score (article_id, status, profile_score, profile_max_level, attempts) "
                        + "VALUES (?, 'SCORED', ?, ?, 1)",
                articleId, profileScore, profileMaxLevel);
    }
}
