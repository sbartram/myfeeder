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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end: MockMvc → InterestController → InterestService → Testcontainers Postgres. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class InterestApiIntegrationTest {

    @Autowired private WebApplicationContext wac;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final String PREVIEW_FEED_URL = "https://example.test/interest-preview-feed.xml";
    private static final String COUNTS_FEED_URL = "https://example.test/interest-counts-feed.xml";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        jdbcTemplate.update("DELETE FROM interest_topic");
        jdbcTemplate.update("UPDATE interest_profile SET profile_text = '', version = 1 WHERE id = 1");
        jdbcTemplate.update("DELETE FROM feed WHERE url = ?", PREVIEW_FEED_URL); // the article cascades
        jdbcTemplate.update("DELETE FROM feed WHERE url = ?", COUNTS_FEED_URL); // articles and scores cascade
    }

    @Test
    void profilePutThenGetPersists() throws Exception {
        mockMvc.perform(put("/api/interest/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileText\":\"Rust and Postgres\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileText").value("Rust and Postgres"))
                .andExpect(jsonPath("$.version").value(2));

        mockMvc.perform(get("/api/interest/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.profileText").value("Rust and Postgres"))
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void savingTheSameProfileTextKeepsTheVersion() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(put("/api/interest/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"profileText\":\"Rust and Postgres\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.version").value(2));
        }
    }

    @Test
    void topicCrudRoundTripsThroughTheDatabase() throws Exception {
        String created = mockMvc.perform(post("/api/interest/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Rust\",\"description\":\"The Rust programming language\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.weight").value(20))
                .andExpect(jsonPath("$.version").value(1))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(created, "$.id")).longValue();

        mockMvc.perform(get("/api/interest/topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].name").value("Rust"));

        mockMvc.perform(put("/api/interest/topics/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Rust\",\"description\":\"Rust systems programming\",\"weight\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.version").value(2));

        mockMvc.perform(put("/api/interest/topics/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Rust\",\"description\":\"Rust systems programming\",\"weight\":-15}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.weight").value(-15))
                .andExpect(jsonPath("$.version").value(2));

        mockMvc.perform(delete("/api/interest/topics/" + id))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/interest/topics/" + id))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/interest/topics"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void statusReportsKeylessClosedAndColdStart() throws Exception {
        mockMvc.perform(get("/api/interest/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.breakerState").value("CLOSED"))
                .andExpect(jsonPath("$.coldStart").value(true));

        mockMvc.perform(put("/api/interest/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileText\":\"Rust\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/interest/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coldStart").value(false));
    }

    @Test
    void statusCountsEligibleUnscoredAndExhaustedFailures() throws Exception {
        // Deltas over a baseline: the Spring context and Postgres are shared with other test classes.
        String before = mockMvc.perform(get("/api/interest/status"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long baseUnscored = ((Number) JsonPath.read(before, "$.eligibleUnscored")).longValue();
        long baseFailed = ((Number) JsonPath.read(before, "$.failed")).longValue();

        Long feedId = jdbcTemplate.queryForObject(
                "INSERT INTO feed (url, title, feed_type) VALUES (?, ?, ?) RETURNING id",
                Long.class, COUNTS_FEED_URL, "Counts Feed", "RSS");
        Instant now = Instant.now();
        insertArticle(feedId, "a", now.minus(Duration.ofHours(1)), false); // no row: unscored
        long b = insertArticle(feedId, "b", now.minus(Duration.ofHours(2)), false);
        long c = insertArticle(feedId, "c", now.minus(Duration.ofHours(3)), false);
        long d = insertArticle(feedId, "d", now.minus(Duration.ofHours(4)), false);
        insertArticle(feedId, "e", now.minus(Duration.ofHours(5)), true);
        insertArticle(feedId, "f", now.minus(Duration.ofDays(20)), false);
        insertScore(b, "FAILED", 1); // still being retried: counts as unscored (D-11)
        insertScore(c, "FAILED", 3); // exhausted: counts as failed (D-11)
        insertScore(d, "SCORED", 1);
        // e (read) and f (outside the 14-day window) drop out of both counts (D-12)

        mockMvc.perform(get("/api/interest/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligibleUnscored").value(baseUnscored + 2))
                .andExpect(jsonPath("$.failed").value(baseFailed + 1));
    }

    private long insertArticle(long feedId, String guid, Instant publishedAt, boolean read) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO article (feed_id, guid, title, url, summary, published_at, read) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class, feedId, "counts-" + guid, "Article " + guid, "https://example.test/counts-" + guid,
                "Summary " + guid, Timestamp.from(publishedAt), read);
    }

    private void insertScore(long articleId, String status, int attempts) {
        jdbcTemplate.update("INSERT INTO article_score (article_id, status, attempts) VALUES (?, ?, ?)",
                articleId, status, attempts);
    }

    @Test
    void keylessPreviewIs503ThroughTheFullStack() throws Exception {
        Long feedId = jdbcTemplate.queryForObject(
                "INSERT INTO feed (url, title, feed_type) VALUES (?, ?, ?) RETURNING id",
                Long.class, PREVIEW_FEED_URL, "Preview Feed", "RSS");
        Long articleId = jdbcTemplate.queryForObject(
                "INSERT INTO article (feed_id, guid, title, url, summary) VALUES (?, ?, ?, ?, ?) RETURNING id",
                Long.class, feedId, "preview-1", "Rust 1.90 released", "https://example.test/rust-1-90",
                "Faster compile times");

        mockMvc.perform(post("/api/interest/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"articleId\":" + articleId + ",\"description\":\"Rust\",\"topicId\":null}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Jev not configured"));
    }

    @Test
    void previewOfMissingArticleIs404() throws Exception {
        mockMvc.perform(post("/api/interest/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"articleId\":999999,\"description\":\"Rust\",\"topicId\":null}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void weightOutOfRangeIs400BeforeTheDatabase() throws Exception {
        mockMvc.perform(post("/api/interest/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Rust\",\"description\":\"Rust lang\",\"weight\":51}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Weight must be a whole number from -50 to +50"));

        Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM interest_topic", Integer.class);
        assertThat(rows).isZero();
    }
}
