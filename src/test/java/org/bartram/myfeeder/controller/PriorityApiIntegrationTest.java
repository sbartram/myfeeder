package org.bartram.myfeeder.controller;

import com.jayway.jsonpath.JsonPath;
import org.bartram.myfeeder.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end: MockMvc → ArticleController → PriorityService → InterestScoreQueries → Testcontainers
 * Postgres. The container is shared with other test classes, so assertions are relative to the ids
 * seeded here only.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PriorityApiIntegrationTest {

    private static final String PRIORITY_FEED_URL = "https://example.test/priority-it-feed.xml";
    private static final String PRIORITY_TOPIC_NAME = "priority-it-topic";

    @Autowired private WebApplicationContext wac;
    @Autowired private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        // Articles, score rows and topic-score rows cascade from the feed
        jdbcTemplate.update("DELETE FROM feed WHERE url = ?", PRIORITY_FEED_URL);
        jdbcTemplate.update("DELETE FROM interest_topic WHERE name = ?", PRIORITY_TOPIC_NAME);
    }

    @Test
    void rankedWalkCrossesIntoUnscoredWithNoDuplicates() throws Exception {
        Long feedId = jdbcTemplate.queryForObject(
                "INSERT INTO feed (url, title, feed_type) VALUES (?, ?, ?) RETURNING id",
                Long.class, PRIORITY_FEED_URL, "Priority Feed", "RSS");
        Instant now = Instant.now();
        long top = insertArticle(feedId, "top", now.minus(Duration.ofHours(3)), false);
        long mid = insertArticle(feedId, "mid", now.minus(Duration.ofHours(1)), false);
        long unscoredNew = insertArticle(feedId, "unscored-new", now.minus(Duration.ofMinutes(30)), false);
        long unscoredFailed = insertArticle(feedId, "unscored-failed", now.minus(Duration.ofHours(2)), false);
        long readTop = insertArticle(feedId, "read-top", now.minus(Duration.ofHours(1)), true);

        // Every article_score row before any article_topic_score row (FK)
        insertScored(top, 4.0, 4);
        insertScored(mid, 2.0, 4);
        jdbcTemplate.update("INSERT INTO article_score (article_id, status, attempts) VALUES (?, 'FAILED', 1)",
                unscoredFailed);
        insertScored(readTop, 4.0, 4);
        Long topicId = jdbcTemplate.queryForObject(
                "INSERT INTO interest_topic (name, description, weight) VALUES (?, ?, ?) RETURNING id",
                Long.class, PRIORITY_TOPIC_NAME, "Priority test", 50);
        jdbcTemplate.update(
                "INSERT INTO article_topic_score (article_id, topic_id, noul, topic_version) VALUES (?, ?, ?, ?)",
                top, topicId, 1.0, 1);

        List<Map<String, Object>> items = new ArrayList<>();
        String url = "/api/articles/priority?limit=2";
        int pages = 0;
        while (true) {
            if (++pages > 1000) {
                fail("Priority walk did not terminate after 1000 pages");
            }
            String body = mockMvc.perform(get(url))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            List<Map<String, Object>> page = JsonPath.read(body, "$.items");
            items.addAll(page);
            Object next = JsonPath.read(body, "$.nextCursor");
            if (next == null) {
                break;
            }
            url = "/api/articles/priority?limit=2&before=" + ((Number) next).longValue();
        }

        List<Long> ids = items.stream().map(i -> ((Number) i.get("id")).longValue()).toList();
        Set<Long> unique = new HashSet<>(ids);
        assertThat(unique).as("no id appears twice in the walk").hasSize(ids.size());
        assertThat(ids).doesNotContain(readTop);
        assertThat(ids.stream().filter(id -> List.of(top, mid, unscoredNew, unscoredFailed).contains(id)).toList())
                .containsExactly(top, mid, unscoredNew, unscoredFailed);

        Map<Long, Map<String, Object>> byId = new LinkedHashMap<>();
        items.forEach(i -> byId.put(((Number) i.get("id")).longValue(), i));
        assertThat(byId.get(top).get("interestScore")).isEqualTo(100);
        assertThat(byId.get(mid).get("interestScore")).isEqualTo(50);
        assertThat(byId.get(unscoredNew)).containsKey("interestScore");
        assertThat(byId.get(unscoredNew).get("interestScore")).isNull();
        assertThat(byId.get(unscoredFailed)).containsKey("interestScore");
        assertThat(byId.get(unscoredFailed).get("interestScore")).isNull();
    }

    @Test
    void missingCursorIs404() throws Exception {
        Long maxId = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) FROM article", Long.class);

        mockMvc.perform(get("/api/articles/priority?before=" + (maxId + 1000)))
                .andExpect(status().isNotFound());
    }

    private long insertArticle(long feedId, String guid, Instant publishedAt, boolean read) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO article (feed_id, guid, title, url, summary, published_at, read) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class, feedId, "priority-" + guid, "Article " + guid, "https://example.test/priority-" + guid,
                "Summary " + guid, Timestamp.from(publishedAt), read);
    }

    private void insertScored(long articleId, double profileScore, int profileMaxLevel) {
        jdbcTemplate.update(
                "INSERT INTO article_score (article_id, status, profile_score, profile_max_level, attempts) "
                        + "VALUES (?, 'SCORED', ?, ?, 1)",
                articleId, profileScore, profileMaxLevel);
    }
}
