package org.bartram.myfeeder.controller;

import com.jayway.jsonpath.JsonPath;
import org.bartram.myfeeder.TestcontainersConfiguration;
import org.bartram.myfeeder.repository.InterestScoreQueries.SortKey;
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

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    private static final String PRIORITY_BOARD_NAME = "priority-it-board";

    @Autowired private WebApplicationContext wac;
    @Autowired private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        // Articles, score rows and topic-score rows cascade from the feed
        jdbcTemplate.update("DELETE FROM feed WHERE url = ?", PRIORITY_FEED_URL);
        jdbcTemplate.update("DELETE FROM interest_topic WHERE name LIKE ?", PRIORITY_TOPIC_NAME + "%");
        // board_article rows cascade from the board
        jdbcTemplate.update("DELETE FROM board WHERE name = ?", PRIORITY_BOARD_NAME);
    }

    @Test
    void everyArticleResponseCarriesInterestScore() throws Exception {
        long feedId = insertFeed();
        Instant now = Instant.now();
        long scoredUnread = insertArticle(feedId, "scored-unread", now.minus(Duration.ofHours(1)), false);
        long scoredRead = insertArticle(feedId, "scored-read", now.minus(Duration.ofHours(2)), true);
        long unscored = insertArticle(feedId, "unscored", now.minus(Duration.ofHours(3)), false);
        insertScored(scoredUnread, 2.0, 4);
        insertScored(scoredRead, 4.0, 4);

        // Date order is kept; the read article keeps its badge
        String list = mockMvc.perform(get("/api/articles?feedId=" + feedId + "&limit=50"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> items = JsonPath.read(list, "$.items");
        assertThat(items).extracting(i -> ((Number) i.get("id")).longValue())
                .containsExactly(scoredUnread, scoredRead, unscored);
        assertThat(items).extracting(i -> i.get("interestScore")).containsExactly(50, 100, null);
        assertThat(items.get(2)).containsKey("interestScore");

        String board = mockMvc.perform(post("/api/boards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + PRIORITY_BOARD_NAME + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long boardId = ((Number) JsonPath.read(board, "$.id")).longValue();
        for (long articleId : List.of(scoredRead, unscored)) {
            mockMvc.perform(post("/api/boards/" + boardId + "/articles")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"articleId\": " + articleId + "}"))
                    .andExpect(status().isCreated());
        }
        String boardArticles = mockMvc.perform(get("/api/boards/" + boardId + "/articles"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Map<Long, Object> boardScores = new LinkedHashMap<>();
        List<Map<String, Object>> boardItems = JsonPath.read(boardArticles, "$.items");
        boardItems.forEach(i -> boardScores.put(((Number) i.get("id")).longValue(), i.get("interestScore")));
        assertThat(boardScores).containsOnlyKeys(scoredRead, unscored);
        assertThat(boardScores.get(scoredRead)).isEqualTo(100);
        assertThat(boardScores.get(unscored)).isNull();

        String patched = mockMvc.perform(patch("/api/articles/" + scoredUnread)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"starred\": true}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat((Boolean) JsonPath.read(patched, "$.starred")).isTrue();
        assertThat((Integer) JsonPath.read(patched, "$.interestScore")).isEqualTo(50);
    }

    @Test
    void rankedWalkCrossesIntoUnscoredWithNoDuplicates() throws Exception {
        long feedId = insertFeed();
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
        String cursor = null;
        int pages = 0;
        while (true) {
            if (++pages > 1000) {
                fail("Priority walk did not terminate after 1000 pages");
            }
            var request = get("/api/articles/priority").param("limit", "2");
            if (cursor != null) {
                request.param("before", cursor);
            }
            String body = mockMvc.perform(request)
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            List<Map<String, Object>> page = JsonPath.read(body, "$.items");
            items.addAll(page);
            cursor = JsonPath.read(body, "$.nextCursor");
            if (cursor == null) {
                break;
            }
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

    /**
     * G-05-7 / WR-02: the next page compares against the tuple the cursor row was served with. Lowering
     * the cursor article's score between page fetches must not skip the rows it dropped past.
     */
    @Test
    void cursorScoreDropMidWalkSkipsNoRow() throws Exception {
        long feedId = insertFeed();
        Instant now = Instant.now();
        long top = insertArticle(feedId, "drop-top", now.minus(Duration.ofHours(3)), false);
        long mid = insertArticle(feedId, "drop-mid", now.minus(Duration.ofHours(1)), false);
        long unscoredNew = insertArticle(feedId, "drop-unscored-new", now.minus(Duration.ofMinutes(30)), false);
        long unscoredFailed = insertArticle(feedId, "drop-unscored-failed", now.minus(Duration.ofHours(2)), false);

        // Every article_score row before any article_topic_score row (FK)
        insertScored(top, 4.0, 4);
        insertScored(mid, 2.0, 4);
        jdbcTemplate.update("INSERT INTO article_score (article_id, status, attempts) VALUES (?, 'FAILED', 1)",
                unscoredFailed);
        long topicId = insertTopic(PRIORITY_TOPIC_NAME, 50);
        insertTopicScore(top, topicId, 1.0); // top raw 150, mid raw 50

        List<Long> walked = new ArrayList<>();
        String cursor = null;
        String limit = "1";
        boolean dropped = false;
        int pages = 0;
        while (true) {
            if (++pages > 1000) {
                fail("Priority walk did not terminate after 1000 requests");
            }
            var request = get("/api/articles/priority").param("limit", limit);
            if (cursor != null) {
                request.param("before", cursor);
            }
            String body = mockMvc.perform(request)
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            List<Map<String, Object>> page = JsonPath.read(body, "$.items");
            page.forEach(i -> walked.add(((Number) i.get("id")).longValue()));
            cursor = JsonPath.read(body, "$.nextCursor");
            if (!dropped && walked.contains(top)) {
                // This response's cursor is top's served tuple; top's live raw now drops to 50, tied
                // with mid but older, so it ranks below mid.
                jdbcTemplate.update("UPDATE interest_topic SET weight = -50 WHERE id = ?", topicId);
                dropped = true;
                limit = "50";
            }
            if (cursor == null) {
                break;
            }
        }

        List<Long> seeded = List.of(top, mid, unscoredNew, unscoredFailed);
        List<Long> ours = walked.stream().filter(seeded::contains).toList();
        assertThat(ours).as("mid must not be skipped when the cursor article's score drops").contains(mid);
        assertThat(ours.stream().distinct().toList()).containsExactly(top, mid, unscoredNew, unscoredFailed);
        assertThat(ours.stream().filter(id -> id == top).count()).isEqualTo(2);
    }

    /**
     * Phase 10 SC-3 (LRN-06): after an engagement, and again after Forget, the engaged article's score
     * agrees across the Priority walk, GET by id, its breakdown and the feed list, and its Priority
     * position follows the score. Both articles start at 50 (profile 2.0/4) + 18 (base 20 x hinge 0.9)
     * = 68, the peer newer so it ranks first; a star adds learn-rate 2 x save 0.5 x hinge 0.9 = 0.9 to
     * the engaged topic, so 20.9 x 0.9 = 18.81 rounds to 19 and the engaged article reads 69.
     */
    @Test
    void engagedArticleAgreesEverywhereAfterRefresh() throws Exception {
        long feedId = insertFeed();
        Instant now = Instant.now();
        long engaged = insertArticle(feedId, "sc3-engaged", now.minus(Duration.ofHours(2)), false);
        long peer = insertArticle(feedId, "sc3-peer", now.minus(Duration.ofHours(1)), false);
        // Every article_score row before any article_topic_score row (FK)
        insertScored(engaged, 2.0, 4);
        insertScored(peer, 2.0, 4);
        long engagedTopic = insertTopic(PRIORITY_TOPIC_NAME + "-eng", 20);
        long peerTopic = insertTopic(PRIORITY_TOPIC_NAME + "-peer", 20);
        insertTopicScore(engaged, engagedTopic, 0.95);
        insertTopicScore(peer, peerTopic, 0.95);

        assertEverySourceAgrees(feedId, engaged, peer, 68, 0.0, List.of(peer, engaged));

        mockMvc.perform(patch("/api/articles/" + engaged)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"starred\": true}"))
                .andExpect(status().isOk());
        assertEverySourceAgrees(feedId, engaged, peer, 69, 0.9, List.of(engaged, peer));

        mockMvc.perform(delete("/api/articles/" + engaged + "/engagement"))
                .andExpect(status().isNoContent());
        assertEverySourceAgrees(feedId, engaged, peer, 68, 0.0, List.of(peer, engaged));
    }

    /**
     * Asserts {@code engaged} reads {@code score} by id, in its breakdown display and point sum, in the
     * feed list and in a fresh Priority walk; its TOPIC row carries {@code engagementWeight} and no vote
     * part; the peer stays at 68; and the walk lists the two seeded ids in {@code order}.
     */
    private void assertEverySourceAgrees(long feedId, long engaged, long peer, int score,
                                         double engagementWeight, List<Long> order) throws Exception {
        String byId = mockMvc.perform(get("/api/articles/" + engaged))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat((Integer) JsonPath.read(byId, "$.interestScore")).as("by-id score").isEqualTo(score);
        assertThat((Integer) JsonPath.read(byId, "$.interestBreakdown.display")).as("breakdown display")
                .isEqualTo(score);
        List<Map<String, Object>> rows = JsonPath.read(byId, "$.interestBreakdown.rows");
        assertThat(rows.stream().mapToLong(r -> ((Number) r.get("points")).longValue()).sum())
                .as("breakdown points sum").isEqualTo(score);
        List<Map<String, Object>> topicRows = rows.stream().filter(r -> "TOPIC".equals(r.get("kind"))).toList();
        assertThat(topicRows).hasSize(1);
        assertThat(((Number) topicRows.get(0).get("engagementWeight")).doubleValue())
                .as("engagement part").isEqualTo(engagementWeight);
        assertThat(((Number) topicRows.get(0).get("thumbsWeight")).doubleValue()).as("vote part").isEqualTo(0.0);

        String list = mockMvc.perform(get("/api/articles?feedId=" + feedId + "&limit=50"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(scoreOf(JsonPath.read(list, "$.items"), engaged)).as("list badge").isEqualTo(score);

        List<Map<String, Object>> walk = walkPriority();
        assertThat(scoreOf(walk, engaged)).as("Priority score").isEqualTo(score);
        assertThat(scoreOf(walk, peer)).as("peer Priority score").isEqualTo(68);
        assertThat(walk.stream().map(i -> ((Number) i.get("id")).longValue()).filter(order::contains).toList())
                .as("Priority order").containsExactlyElementsOf(order);
    }

    /** Every item a full Priority walk serves, in order, following nextCursor until it is null. */
    private List<Map<String, Object>> walkPriority() throws Exception {
        List<Map<String, Object>> items = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        while (true) {
            if (++pages > 1000) {
                fail("Priority walk did not terminate after 1000 pages");
            }
            var request = get("/api/articles/priority").param("limit", "50");
            if (cursor != null) {
                request.param("before", cursor);
            }
            String body = mockMvc.perform(request)
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            items.addAll(JsonPath.read(body, "$.items"));
            cursor = JsonPath.read(body, "$.nextCursor");
            if (cursor == null) {
                return items;
            }
        }
    }

    /** The interestScore of the single item with {@code id}. */
    private static Object scoreOf(List<Map<String, Object>> items, long id) {
        List<Object> scores = items.stream()
                .filter(i -> ((Number) i.get("id")).longValue() == id)
                .map(i -> i.get("interestScore"))
                .toList();
        assertThat(scores).as("article %d listed once", id).hasSize(1);
        return scores.get(0);
    }

    @Test
    void articleByIdCarriesAnExactBreakdown() throws Exception {
        long feedId = insertFeed();
        Instant now = Instant.now();
        long scored = insertArticle(feedId, "breakdown", now.minus(Duration.ofHours(1)), false);
        long unscored = insertArticle(feedId, "no-breakdown", now.minus(Duration.ofHours(2)), false);
        insertScored(scored, 2.56, 4);
        long a = insertTopic(PRIORITY_TOPIC_NAME + "-a", 20);
        long b = insertTopic(PRIORITY_TOPIC_NAME + "-b", -30);
        long c = insertTopic(PRIORITY_TOPIC_NAME + "-c", 10);
        insertTopicScore(scored, a, 0.93);
        insertTopicScore(scored, b, 0.60);
        insertTopicScore(scored, c, 0.20);

        String body = mockMvc.perform(get("/api/articles/" + scored))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat((Integer) JsonPath.read(body, "$.interestScore")).isEqualTo(75);
        assertThat((Integer) JsonPath.read(body, "$.interestBreakdown.total")).isEqualTo(75);
        assertThat((Integer) JsonPath.read(body, "$.interestBreakdown.display")).isEqualTo(75);
        List<Map<String, Object>> rows = JsonPath.read(body, "$.interestBreakdown.rows");
        assertThat(rows).extracting(r -> r.get("kind")).containsExactly("PROFILE", "TOPIC", "TOPIC");
        assertThat(rows).extracting(r -> r.get("name"))
                .containsExactly(null, PRIORITY_TOPIC_NAME + "-a", PRIORITY_TOPIC_NAME + "-b");
        List<Long> points = rows.stream().map(r -> ((Number) r.get("points")).longValue()).toList();
        assertThat(points).containsExactly(64L, 17L, -6L);
        assertThat(points.stream().mapToLong(Long::longValue).sum()).isEqualTo(75);
        List<Map<String, Object>> nonMatching = JsonPath.read(body, "$.interestBreakdown.nonMatching");
        assertThat(nonMatching).extracting(t -> t.get("name")).containsExactly(PRIORITY_TOPIC_NAME + "-c");

        String plain = mockMvc.perform(get("/api/articles/" + unscored))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> json = JsonPath.read(plain, "$");
        assertThat(json).containsKey("interestScore").doesNotContainKey("interestBreakdown");
        assertThat(json.get("interestScore")).isNull();
    }

    @Test
    void missingCursorIs404() throws Exception {
        Long maxId = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) FROM article", Long.class);

        String gone = PriorityPage.encodeCursor(new SortKey(50.0, Instant.now(), maxId + 1000));

        mockMvc.perform(get("/api/articles/priority").param("before", gone))
                .andExpect(status().isNotFound());
    }

    @Test
    void outOfRangeCursorDateIs404() throws Exception {
        long feedId = insertFeed();
        long articleId = insertArticle(feedId, "wr04", Instant.now(), false);

        String before = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("1.0|" + Long.MIN_VALUE + "|" + articleId).getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/articles/priority").param("before", before))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Priority cursor not recognized"));
    }

    private long insertFeed() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO feed (url, title, feed_type) VALUES (?, ?, ?) RETURNING id",
                Long.class, PRIORITY_FEED_URL, "Priority Feed", "RSS");
    }

    private long insertArticle(long feedId, String guid, Instant publishedAt, boolean read) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO article (feed_id, guid, title, url, summary, published_at, read) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class, feedId, "priority-" + guid, "Article " + guid, "https://example.test/priority-" + guid,
                "Summary " + guid, Timestamp.from(publishedAt), read);
    }

    private long insertTopic(String name, int weight) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO interest_topic (name, description, weight) VALUES (?, ?, ?) RETURNING id",
                Long.class, name, "Priority test", weight);
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
