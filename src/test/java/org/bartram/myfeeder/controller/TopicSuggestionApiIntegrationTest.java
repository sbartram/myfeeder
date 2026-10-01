package org.bartram.myfeeder.controller;

import com.jayway.jsonpath.JsonPath;
import org.bartram.myfeeder.TestcontainersConfiguration;
import org.bartram.myfeeder.integration.JevApiClient;
import org.bartram.myfeeder.model.SuggestionDismissalReason;
import org.bartram.myfeeder.repository.TopicSuggestionStore;
import org.bartram.myfeeder.service.InterestService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end: MockMvc → TopicSuggestionController → TopicSuggestionService → TopicSuggestionStore and
 * InterestScoreQueries → Testcontainers Postgres. The test yaml leaves Jev unconfigured, and the list is
 * still served (D-03). The container is shared with other test classes, so assertions only read seeded ids;
 * seeded rows have badge 0 and a fresh engagement, so they sort first (Pitfall 3). Jev must never be called.
 * The two writes (Dismiss and a topic created with a sourceArticleId) are proven permanent, idempotent and,
 * for the topic create, atomic with the topic insert.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TopicSuggestionApiIntegrationTest {

    private static final String SUGGESTIONS_FEED_URL = "https://example.test/suggestions-it-feed.xml";
    private static final String SUGGESTIONS_FEED_TITLE = "Suggestions Feed";

    @Autowired private WebApplicationContext wac;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private JevApiClient jevApiClient;
    @MockitoSpyBean private TopicSuggestionStore suggestionStore;
    @Autowired private InterestService interestService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        // Articles, engagement, scores, votes and dismissals cascade from the feed
        jdbcTemplate.update("DELETE FROM feed WHERE url = ?", SUGGESTIONS_FEED_URL);
        // The 25-topic cap is global, so start from no topics at all (as InterestApiIntegrationTest does)
        jdbcTemplate.update("DELETE FROM interest_topic");
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

    @Test
    void dismissReturns204AndHidesTheSuggestion() throws Exception {
        long a = engagedScoredArticle("a");
        assertThat(listedIds(getSuggestions())).contains(a);

        mockMvc.perform(put("/api/interest/suggestions/{id}/dismissal", a))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(dismissalReasons(a)).containsExactly("DISMISSED");
        assertThat(listedIds(getSuggestions())).doesNotContain(a);
    }

    @Test
    void dismissIsIdempotentAndKeepsTheFirstReason() throws Exception {
        long a = engagedScoredArticle("a");

        mockMvc.perform(put("/api/interest/suggestions/{id}/dismissal", a)).andExpect(status().isNoContent());
        mockMvc.perform(put("/api/interest/suggestions/{id}/dismissal", a)).andExpect(status().isNoContent());

        assertThat(dismissalReasons(a)).containsExactly("DISMISSED");
    }

    @Test
    void dismissingAnUnknownArticleIs404() throws Exception {
        long gone = insertArticle(insertFeed(), "gone");
        jdbcTemplate.update("DELETE FROM article WHERE id = ?", gone);

        mockMvc.perform(put("/api/interest/suggestions/{id}/dismissal", gone))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Article not found: " + gone));

        assertThat(dismissalReasons(gone)).isEmpty();
    }

    @Test
    void dismissingWithANonNumericIdIs400() throws Exception {
        mockMvc.perform(put("/api/interest/suggestions/abc/dismissal"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aDismissedSuggestionStaysGoneAfterLaterEngagement() throws Exception {
        long a = engagedScoredArticle("a");
        mockMvc.perform(put("/api/interest/suggestions/{id}/dismissal", a)).andExpect(status().isNoContent());

        mockMvc.perform(put("/api/articles/{id}/engagement/open", a)).andExpect(status().isNoContent());
        insertEngagement(a, "STAR");

        assertThat(listedIds(getSuggestions())).doesNotContain(a);
        assertThat(listedIds(getSuggestions())).doesNotContain(a);
        assertThat(dismissalReasons(a)).containsExactly("DISMISSED");
    }

    @Test
    void dismissingLeavesTheBadgeUnchanged() throws Exception {
        long a = insertArticle(insertFeed(), "a");
        jdbcTemplate.update(
                "INSERT INTO article_score (article_id, status, profile_score, profile_max_level, attempts) "
                        + "VALUES (?, 'SCORED', 2, 4, 1)",
                a);
        insertEngagement(a, "OPEN_ORIGINAL");

        mockMvc.perform(get("/api/articles/{id}", a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interestScore").value(50));

        mockMvc.perform(put("/api/interest/suggestions/{id}/dismissal", a)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/articles/{id}", a))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interestScore").value(50));
    }

    @Test
    void creatingATopicFromASuggestionHidesItForGood() throws Exception {
        long a = engagedScoredArticle("a");

        postTopic("{\"name\":\"suggestions-it-rust\",\"description\":\"Rust\",\"weight\":20,\"sourceArticleId\":"
                + a + "}")
                .andExpect(status().isCreated());

        assertThat(dismissalReason(a)).isEqualTo("TOPIC_CREATED");
        assertThat(listedIds(getSuggestions())).doesNotContain(a);
        assertThat(listedIds(getSuggestions())).doesNotContain(a);
    }

    @Test
    void aStaleSourceArticleIdStillCreatesTheTopic() throws Exception {
        long gone = insertArticle(insertFeed(), "gone");
        jdbcTemplate.update("DELETE FROM article WHERE id = ?", gone);

        postTopic("{\"name\":\"suggestions-it-stale\",\"description\":\"Stale\",\"sourceArticleId\":" + gone + "}")
                .andExpect(status().isCreated());

        assertThat(topicCount("suggestions-it-stale")).isEqualTo(1);
        assertThat(dismissalReason(gone)).isNull();
    }

    @Test
    void withoutASourceNoRowIsWritten() throws Exception {
        long a = engagedScoredArticle("a");

        postTopic("{\"name\":\"suggestions-it-plain\",\"description\":\"Plain\",\"weight\":20}")
                .andExpect(status().isCreated());

        assertThat(dismissalReason(a)).isNull();
        assertThat(listedIds(getSuggestions())).contains(a);
    }

    @Test
    void theTwentyFifthTopicWritesTheRowAndTheTwentySixthWritesNothing() throws Exception {
        long feed = insertFeed();
        long a = engagedScored(feed, "a");
        long b = engagedScored(feed, "b");
        for (int n = 1; n <= 24; n++) {
            insertTopic("suggestions-it-cap-" + n, 10);
        }

        postTopic("{\"name\":\"suggestions-it-25\",\"description\":\"Twenty-fifth\",\"sourceArticleId\":" + a + "}")
                .andExpect(status().isCreated());
        assertThat(dismissalReason(a)).isEqualTo("TOPIC_CREATED");

        postTopic("{\"name\":\"suggestions-it-26\",\"description\":\"Twenty-sixth\",\"sourceArticleId\":" + b + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("A maximum of 25 topics is allowed"));
        assertThat(dismissalReason(b)).isNull();
        assertThat(listedIds(getSuggestions())).contains(b);
    }

    @Test
    void aDismissedArticleKeepsItsFirstReasonWhenATopicIsCreatedFromIt() throws Exception {
        long a = engagedScoredArticle("a");
        mockMvc.perform(put("/api/interest/suggestions/{id}/dismissal", a)).andExpect(status().isNoContent());

        postTopic("{\"name\":\"suggestions-it-again\",\"description\":\"Again\",\"sourceArticleId\":" + a + "}")
                .andExpect(status().isCreated());

        assertThat(dismissalReasons(a)).containsExactly("DISMISSED");
    }

    @Test
    void deletingTheTopicKeepsTheArticleHandled() throws Exception {
        long a = engagedScoredArticle("a");
        String created = postTopic("{\"name\":\"suggestions-it-del\",\"description\":\"Delete me\",\"sourceArticleId\":"
                + a + "}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long topicId = ((Number) JsonPath.read(created, "$.id")).longValue();

        mockMvc.perform(delete("/api/interest/topics/{id}", topicId)).andExpect(status().isNoContent());

        assertThat(dismissalReason(a)).isEqualTo("TOPIC_CREATED");
        assertThat(listedIds(getSuggestions())).doesNotContain(a);
    }

    @Test
    void aFailedDismissalInsertRollsBackTheTopic() {
        long a = engagedScoredArticle("a");
        doThrow(new DataIntegrityViolationException("forced"))
                .when(suggestionStore).handle(a, SuggestionDismissalReason.TOPIC_CREATED);

        // Called on the transactional bean directly: through MockMvc the exception would be rethrown,
        // because GlobalExceptionHandler has no catch-all
        assertThatThrownBy(() -> interestService.createTopic("suggestions-it-atomic", "Atomic", 20, a))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(topicCount("suggestions-it-atomic")).isZero();
        assertThat(dismissalReason(a)).isNull();
    }

    @Test
    void aTextPlainTopicCreateIs415() throws Exception {
        mockMvc.perform(post("/api/interest/topics")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{\"name\":\"suggestions-it-csrf\",\"description\":\"Cross-site\"}"))
                .andExpect(status().isUnsupportedMediaType());

        assertThat(topicCount("suggestions-it-csrf")).isZero();
    }

    @Test
    void updatingATopicWithASourceWritesNoRow() throws Exception {
        long a = engagedScoredArticle("a");
        long topicId = insertTopic("suggestions-it-go", 20);

        mockMvc.perform(put("/api/interest/topics/{id}", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"suggestions-it-go\",\"description\":\"Go\",\"weight\":10,"
                                + "\"sourceArticleId\":" + a + "}"))
                .andExpect(status().isOk());

        assertThat(dismissalReason(a)).isNull();
    }

    private ResultActions postTopic(String json) throws Exception {
        return mockMvc.perform(post("/api/interest/topics")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private int topicCount(String name) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM interest_topic WHERE name = ?", Integer.class, name);
    }

    /** The article's dismissal reason, or null when it has no row. */
    private String dismissalReason(long articleId) {
        List<String> reasons = dismissalReasons(articleId);
        return reasons.isEmpty() ? null : reasons.get(0);
    }

    /** An engaged (OPEN_ORIGINAL), SCORED (badge 0), unmatched article in the given feed. */
    private long engagedScored(long feedId, String guid) {
        long id = insertArticle(feedId, guid);
        insertScored(id);
        insertEngagement(id, "OPEN_ORIGINAL");
        return id;
    }

    /** An engaged (OPEN_ORIGINAL), SCORED (badge 0), unmatched article in a fresh test feed. */
    private long engagedScoredArticle(String guid) {
        return engagedScored(insertFeed(), guid);
    }

    private List<String> dismissalReasons(long articleId) {
        return jdbcTemplate.queryForList(
                "SELECT reason FROM topic_suggestion_dismissal WHERE article_id = ?", String.class, articleId);
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
