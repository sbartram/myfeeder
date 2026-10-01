package org.bartram.myfeeder.controller;

import com.jayway.jsonpath.JsonPath;
import org.bartram.myfeeder.TestcontainersConfiguration;
import org.bartram.myfeeder.integration.JevApiClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
 * End to end: MockMvc → TopicSuggestionController → TopicSuggestionService → TopicSuggestionStore and
 * InterestScoreQueries → Testcontainers Postgres. The test yaml leaves Jev unconfigured, and the list is
 * still served (D-03). The container is shared with other test classes, so assertions only read seeded ids;
 * seeded rows have badge 0 and a fresh engagement, so they sort first (Pitfall 3). Jev must never be called.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TopicSuggestionApiIntegrationTest {

    private static final String SUGGESTIONS_FEED_URL = "https://example.test/suggestions-it-feed.xml";
    private static final String SUGGESTIONS_FEED_TITLE = "Suggestions Feed";

    @Autowired private WebApplicationContext wac;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private JevApiClient jevApiClient;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        // Articles, engagement, scores, votes and dismissals cascade from the feed
        jdbcTemplate.update("DELETE FROM feed WHERE url = ?", SUGGESTIONS_FEED_URL);
        jdbcTemplate.update("DELETE FROM interest_topic WHERE name LIKE 'suggestions-it-%'");
    }

    @AfterEach
    void neverCallsJev() {
        verify(jevApiClient, never()).judge(any(), any());
    }

    @Test
    void listsAnEngagedScoredUnmatchedArticle() throws Exception {
        long a = insertArticle(insertFeed(), "a");
        insertScored(a);
        insertEngagement(a, "OPEN_ORIGINAL");

        String body = getSuggestions();

        List<Map<String, Object>> items = JsonPath.read(body, "$.items[?(@.articleId == " + a + ")]");
        assertThat(items).hasSize(1);
        Map<String, Object> item = items.get(0);
        assertThat(item).containsOnlyKeys("articleId", "title", "feedTitle", "interestScore");
        assertThat(item.get("title")).isEqualTo("Article a");
        assertThat(item.get("feedTitle")).isEqualTo(SUGGESTIONS_FEED_TITLE);
        assertThat(((Number) item.get("interestScore")).intValue()).isZero();
        assertThat(((Number) JsonPath.read(body, "$.total")).intValue()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void leavesOutUnscoredUnengagedAndMatchedArticles() throws Exception {
        long feed = insertFeed();
        long b = insertArticle(feed, "b");
        insertEngagement(b, "OPEN_ORIGINAL");
        long c = insertArticle(feed, "c");
        insertScored(c);
        long d = insertArticle(feed, "d");
        insertScored(d);
        insertEngagement(d, "STAR");
        insertTopicScore(d, insertTopic("suggestions-it-rust", 20), 0.8);

        assertThat(listedIds(getSuggestions())).doesNotContain(b, c, d);
    }

    @Test
    void anArticleEngagedAfterAListingAppearsOnTheNextOne() throws Exception {
        long e = insertArticle(insertFeed(), "e");
        insertScored(e);

        assertThat(listedIds(getSuggestions())).doesNotContain(e);

        mockMvc.perform(put("/api/articles/{id}/engagement/open", e))
                .andExpect(status().isNoContent());

        assertThat(listedIds(getSuggestions())).contains(e);
    }

    private String getSuggestions() throws Exception {
        return mockMvc.perform(get("/api/interest/suggestions"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static List<Long> listedIds(String body) {
        List<Number> ids = JsonPath.read(body, "$.items[*].articleId");
        return ids.stream().map(Number::longValue).toList();
    }

    private void insertEngagement(long articleId, String kind) {
        jdbcTemplate.update("INSERT INTO article_engagement (article_id, kind) VALUES (?, ?)", articleId, kind);
    }

    private long insertFeed() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO feed (url, title, feed_type) VALUES (?, ?, ?) RETURNING id",
                Long.class, SUGGESTIONS_FEED_URL, SUGGESTIONS_FEED_TITLE, "RSS");
    }

    private long insertArticle(long feedId, String guid) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO article (feed_id, guid, title, url, summary, published_at, read) "
                        + "VALUES (?, ?, ?, ?, ?, ?, false) RETURNING id",
                Long.class, feedId, "suggestions-it-" + guid, "Article " + guid,
                "https://example.test/suggestions-it-" + guid, "Summary " + guid,
                Timestamp.from(Instant.parse("2026-09-20T10:00:00Z")));
    }

    private long insertTopic(String name, int weight) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO interest_topic (name, description, weight) VALUES (?, ?, ?) RETURNING id",
                Long.class, name, "Suggestions test", weight);
    }

    /** SCORED with profile_score 0, so the badge is 0 and the row sorts first. */
    private void insertScored(long articleId) {
        jdbcTemplate.update(
                "INSERT INTO article_score (article_id, status, profile_score, profile_max_level, attempts) "
                        + "VALUES (?, 'SCORED', 0, 4, 1)",
                articleId);
    }

    private void insertTopicScore(long articleId, long topicId, double noul) {
        jdbcTemplate.update(
                "INSERT INTO article_topic_score (article_id, topic_id, noul, topic_version) VALUES (?, ?, ?, 1)",
                articleId, topicId, noul);
    }
}
