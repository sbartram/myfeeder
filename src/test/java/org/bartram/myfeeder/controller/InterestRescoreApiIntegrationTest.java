package org.bartram.myfeeder.controller;

import com.jayway.jsonpath.JsonPath;
import org.bartram.myfeeder.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end, keyless: MockMvc → InterestRescoreController → InterestRescoreService → ArticleScoreStore
 * → Testcontainers Postgres. The test YAML has no TypeSafe key, so the reset is always refused here;
 * the configured reset is covered by InterestRescoreServiceTest.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class InterestRescoreApiIntegrationTest {

    private static final String RESCORE_FEED_URL = "https://example.test/interest-rescore-feed.xml";
    private static final String RESCORE_TOPIC_NAME = "rescore-it-topic";

    @Autowired private WebApplicationContext wac;
    @Autowired private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        // Articles, score rows and topic-score rows cascade from the feed
        jdbcTemplate.update("DELETE FROM feed WHERE url = ?", RESCORE_FEED_URL);
        jdbcTemplate.update("DELETE FROM interest_topic WHERE name = ?", RESCORE_TOPIC_NAME);
    }

    @Test
    void rescoreCountIsServedAndKeylessResetIs409() throws Exception {
        // Deltas over a baseline: the Spring context and Postgres are shared with other test classes.
        String before = mockMvc.perform(get("/api/interest/rescore"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long baseline = ((Number) JsonPath.read(before, "$.count")).longValue();

        Long feedId = jdbcTemplate.queryForObject(
                "INSERT INTO feed (url, title, feed_type) VALUES (?, ?, ?) RETURNING id",
                Long.class, RESCORE_FEED_URL, "Rescore Feed", "RSS");
        Instant now = Instant.now();
        long scored = insertArticle(feedId, "scored", now.minus(Duration.ofHours(1)), false);
        long failed = insertArticle(feedId, "failed", now.minus(Duration.ofHours(2)), false);
        long skipped = insertArticle(feedId, "skipped", now.minus(Duration.ofHours(3)), false);
        long readScored = insertArticle(feedId, "read", now.minus(Duration.ofHours(1)), true);
        List<Long> seeded = List.of(scored, failed, skipped, readScored);

        // Every article_score row before any article_topic_score row (FK)
        insertScore(scored, "SCORED", 1);
        insertScore(failed, "FAILED", 3); // exhausted FAILED is in scope (D-03)
        insertScore(skipped, "SKIPPED", 1); // never touched (D-03)
        insertScore(readScored, "SCORED", 1); // read: out of scope
        Long topicId = jdbcTemplate.queryForObject(
                "INSERT INTO interest_topic (name, description, weight) VALUES (?, ?, ?) RETURNING id",
                Long.class, RESCORE_TOPIC_NAME, "Rescore test", 20);
        jdbcTemplate.update(
                "INSERT INTO article_topic_score (article_id, topic_id, noul, topic_version) VALUES (?, ?, ?, ?)",
                scored, topicId, 0.5, 1);

        mockMvc.perform(get("/api/interest/rescore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(baseline + 2))
                .andExpect(jsonPath("$.windowDays").value(14));

        mockMvc.perform(post("/api/interest/rescore")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Configuration error"))
                .andExpect(jsonPath("$.detail")
                        .value("Re-score needs a TypeSafe API key and a profile or at least one topic"));

        // The refused reset deleted nothing and changed no read flag
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM article_score WHERE article_id IN (?, ?, ?, ?)",
                Long.class, seeded.toArray())).isEqualTo(4L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM article_topic_score WHERE article_id = ?", Long.class, scored))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForList(
                "SELECT read FROM article WHERE id IN (?, ?, ?, ?) ORDER BY id", Boolean.class, seeded.toArray()))
                .containsExactly(false, false, false, true);
    }

    private long insertArticle(long feedId, String guid, Instant publishedAt, boolean read) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO article (feed_id, guid, title, url, summary, published_at, read) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class, feedId, "rescore-" + guid, "Article " + guid, "https://example.test/rescore-" + guid,
                "Summary " + guid, Timestamp.from(publishedAt), read);
    }

    private void insertScore(long articleId, String status, int attempts) {
        jdbcTemplate.update("INSERT INTO article_score (article_id, status, attempts) VALUES (?, ?, ?)",
                articleId, status, attempts);
    }
}
