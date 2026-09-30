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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end: MockMvc → ArticleController → ArticleService → ArticleEngagementStore → Testcontainers
 * Postgres (V7). The container is shared with other test classes, so assertions only read rows seeded here.
 * Jev is mocked and must never be called: engagement goes nowhere but the app's own Postgres.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class EngagementApiIntegrationTest {

    private static final String ENGAGEMENT_FEED_URL = "https://example.test/engagement-it-feed.xml";

    @Autowired private WebApplicationContext wac;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private JevApiClient jevApiClient;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        // Articles and their engagement rows cascade from the feed; boards do not, so they go by name
        jdbcTemplate.update("DELETE FROM feed WHERE url = ?", ENGAGEMENT_FEED_URL);
        jdbcTemplate.update("DELETE FROM board WHERE name LIKE 'engagement-it-%'");
    }

    @AfterEach
    void neverCallsJev() {
        verify(jevApiClient, never()).judge(any(), any());
    }

    @Test
    void openReturns204AndStoresOneOpenOriginalRow() throws Exception {
        long a = insertArticle(insertFeed(), "a");

        mockMvc.perform(put("/api/articles/{id}/engagement/open", a))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(kinds(a)).containsExactly("OPEN_ORIGINAL");
    }

    @Test
    void repeatedOpenKeepsOneRowAndItsFirstCreatedAt() throws Exception {
        long a = insertArticle(insertFeed(), "a");

        putOpen(a);
        Timestamp first = createdAt(a);
        putOpen(a);
        Timestamp second = createdAt(a);

        assertThat(kinds(a)).containsExactly("OPEN_ORIGINAL");
        assertThat(second).isEqualTo(first);
    }

    @Test
    void byIdCarriesEngagement() throws Exception {
        long a = insertArticle(insertFeed(), "a");

        List<String> before = JsonPath.read(getArticle(a), "$.engagement");
        putOpen(a);
        List<String> after = JsonPath.read(getArticle(a), "$.engagement");

        assertThat(before).isEmpty();
        assertThat(after).containsExactly("OPEN_ORIGINAL");
    }

    @Test
    void listItemsCarryNoEngagement() throws Exception {
        long feedId = insertFeed();
        long a = insertArticle(feedId, "a");
        putOpen(a);

        String body = mockMvc.perform(get("/api/articles").param("feedId", String.valueOf(feedId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> items = JsonPath.read(body, "$.items");
        assertThat(items).hasSize(1);
        assertThat(((Number) items.get(0).get("id")).longValue()).isEqualTo(a);
        assertThat(items.get(0)).doesNotContainKey("engagement");
    }

    @Test
    void openOnAnUnknownArticleIs404AndStoresNothing() throws Exception {
        mockMvc.perform(put("/api/articles/{id}/engagement/open", 999_999_999L))
                .andExpect(status().isNotFound());

        assertThat(kinds(999_999_999L)).isEmpty();
    }

    @Test
    void openWithANonNumericIdIs400() throws Exception {
        mockMvc.perform(put("/api/articles/{id}/engagement/open", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forgetDeletesEveryKindAndReturns204() throws Exception {
        long a = insertArticle(insertFeed(), "a");
        putOpen(a);
        insertEngagement(a, "STAR");
        insertEngagement(a, "BOARD");
        assertThat(kinds(a)).containsExactly("BOARD", "OPEN_ORIGINAL", "STAR");

        mockMvc.perform(delete("/api/articles/{id}/engagement", a))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(kinds(a)).isEmpty();
        List<String> engagement = JsonPath.read(getArticle(a), "$.engagement");
        assertThat(engagement).isEmpty();
    }

    @Test
    void forgetLeavesStarBoardAndVoteAlone() throws Exception {
        long a = insertArticle(insertFeed(), "a");
        jdbcTemplate.update("UPDATE article SET starred = true WHERE id = ?", a);
        long board = jdbcTemplate.queryForObject(
                "INSERT INTO board (name) VALUES ('engagement-it-keep') RETURNING id", Long.class);
        jdbcTemplate.update("INSERT INTO board_article (board_id, article_id) VALUES (?, ?)", board, a);
        jdbcTemplate.update("INSERT INTO article_feedback (article_id, vote) VALUES (?, 1)", a);
        putOpen(a);
        insertEngagement(a, "STAR");
        insertEngagement(a, "BOARD");

        mockMvc.perform(delete("/api/articles/{id}/engagement", a))
                .andExpect(status().isNoContent());

        assertThat(kinds(a)).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT starred FROM article WHERE id = ?", Boolean.class, a))
                .isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM board_article WHERE board_id = ? AND article_id = ?", Integer.class, board, a))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT vote FROM article_feedback WHERE article_id = ?", Integer.class, a))
                .isEqualTo(1);
    }

    @Test
    void openAfterForgetRecordsAgain() throws Exception {
        long a = insertArticle(insertFeed(), "a");
        putOpen(a);
        mockMvc.perform(delete("/api/articles/{id}/engagement", a))
                .andExpect(status().isNoContent());
        assertThat(kinds(a)).isEmpty();

        putOpen(a);

        assertThat(kinds(a)).containsExactly("OPEN_ORIGINAL");
    }

    @Test
    void forgetWithoutEngagementIs204() throws Exception {
        long a = insertArticle(insertFeed(), "a");

        mockMvc.perform(delete("/api/articles/{id}/engagement", a))
                .andExpect(status().isNoContent());

        assertThat(kinds(a)).isEmpty();
    }

    @Test
    void forgetOnAnUnknownArticleIs404() throws Exception {
        mockMvc.perform(delete("/api/articles/{id}/engagement", 999_999_999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void starringRecordsStarAndShowsOnTheArticle() throws Exception {
        long a = insertArticle(insertFeed(), "a");

        patchState(a, "{\"starred\":true}");

        assertThat(kinds(a)).containsExactly("STAR");
        List<String> engagement = JsonPath.read(getArticle(a), "$.engagement");
        assertThat(engagement).containsExactly("STAR");
    }

    @Test
    void unstarKeepsStarAndRestarKeepsOneRow() throws Exception {
        long a = insertArticle(insertFeed(), "a");

        patchState(a, "{\"starred\":true}");
        patchState(a, "{\"starred\":false}");
        assertThat(kinds(a)).containsExactly("STAR");

        patchState(a, "{\"starred\":true}");
        assertThat(kinds(a)).containsExactly("STAR");
    }

    @Test
    void readOnlyPatchRecordsNothing() throws Exception {
        long a = insertArticle(insertFeed(), "a");

        patchState(a, "{\"read\":true}");

        assertThat(kinds(a)).isEmpty();
    }

    @Test
    void starringAStarredArticleRecordsNothing() throws Exception {
        long a = insertStarredArticle(insertFeed(), "a");

        patchState(a, "{\"starred\":true}");

        assertThat(kinds(a)).isEmpty();
    }

    @Test
    void bulkMarkReadRecordsNothing() throws Exception {
        long a = insertArticle(insertFeed(), "a");

        mockMvc.perform(post("/api/articles/mark-read")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"articleIds\":[" + a + "]}"))
                .andExpect(status().isNoContent());

        assertThat(kinds(a)).isEmpty();
    }

    /** Seeds a row directly, bypassing the save paths that capture STAR and BOARD. */
    private void insertEngagement(long articleId, String kind) {
        jdbcTemplate.update("INSERT INTO article_engagement (article_id, kind) VALUES (?, ?)", articleId, kind);
    }

    private void patchState(long articleId, String json) throws Exception {
        mockMvc.perform(patch("/api/articles/{id}", articleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());
    }

    private void putOpen(long articleId) throws Exception {
        mockMvc.perform(put("/api/articles/{id}/engagement/open", articleId))
                .andExpect(status().isNoContent());
    }

    private String getArticle(long articleId) throws Exception {
        return mockMvc.perform(get("/api/articles/{id}", articleId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /** The article's stored engagement kinds, alphabetically (SQL order, not the enum order). */
    private List<String> kinds(long articleId) {
        return jdbcTemplate.queryForList(
                "SELECT kind FROM article_engagement WHERE article_id = ? ORDER BY kind", String.class, articleId);
    }

    private Timestamp createdAt(long articleId) {
        return jdbcTemplate.queryForObject(
                "SELECT created_at FROM article_engagement WHERE article_id = ? AND kind = 'OPEN_ORIGINAL'",
                Timestamp.class, articleId);
    }

    private long insertFeed() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO feed (url, title, feed_type) VALUES (?, ?, ?) RETURNING id",
                Long.class, ENGAGEMENT_FEED_URL, "Engagement Feed", "RSS");
    }

    private long insertArticle(long feedId, String guid) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO article (feed_id, guid, title, url, summary, published_at, read) "
                        + "VALUES (?, ?, ?, ?, ?, ?, false) RETURNING id",
                Long.class, feedId, "engagement-" + guid, "Article " + guid,
                "https://example.test/engagement-" + guid, "Summary " + guid,
                Timestamp.from(Instant.parse("2026-09-20T10:00:00Z")));
    }

    private long insertStarredArticle(long feedId, String guid) {
        long id = insertArticle(feedId, guid);
        jdbcTemplate.update("UPDATE article SET starred = true WHERE id = ?", id);
        return id;
    }
}
