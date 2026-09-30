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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

    @Test
    void upDownUpEqualsASingleUp() throws Exception {
        long feedId = insertFeed();
        long rust = insertTopic(FEEDBACK_TOPIC_PREFIX + "rust", 20);
        long a = insertMatchingArticle(feedId, "a", rust, 0.95);

        putVote(a, "{\"vote\":1}");
        String down = putVote(a, "{\"vote\":-1}");
        String up = putVote(a, "{\"vote\":1}");

        assertEffect(down, 0, rust, 21.8, 18.2);
        assertEffect(up, 0, rust, 18.2, 21.8);
        assertThat(badge(a)).isEqualTo(70);
        assertThat(storedFeedback(a)).containsExactly("vote=1 narrowed=false");
        assertThat(topicWeight(rust)).isEqualTo(20);
    }

    @Test
    void deleteRestoresExactly() throws Exception {
        long feedId = insertFeed();
        long rust = insertTopic(FEEDBACK_TOPIC_PREFIX + "rust", 20);
        long a = insertMatchingArticle(feedId, "a", rust, 0.95);

        putVote(a, "{\"vote\":1}");
        String body = deleteVote(a);

        assertEffect(body, 0, rust, 21.8, 20.0);
        assertThat((Integer) JsonPath.read(body, "$.article.interestScore")).isEqualTo(68);
        assertThat(badge(a)).isEqualTo(68);
        assertThat(storedFeedback(a)).isEmpty();
        assertThat(topicWeight(rust)).isEqualTo(20);
    }

    @Test
    void repeatedPutChangesNothing() throws Exception {
        long feedId = insertFeed();
        long rust = insertTopic(FEEDBACK_TOPIC_PREFIX + "rust", 20);
        long a = insertMatchingArticle(feedId, "a", rust, 0.95);
        long c = insertMatchingArticle(feedId, "c", rust, 0.4);

        putVote(a, "{\"vote\":1}");
        String again = putVote(a, "{\"vote\":1}");

        assertEffect(again, 0, rust, 21.8, 21.8);
        assertThat(storedFeedback(a)).containsExactly("vote=1 narrowed=false");

        for (int i = 0; i < 2; i++) {
            String noMatch = putVote(c, "{\"vote\":1}");
            assertThat((Boolean) JsonPath.read(noMatch, "$.scored")).isTrue();
            assertThat((List<?>) JsonPath.read(noMatch, "$.effects")).isEmpty();
        }
        assertThat(storedFeedback(c)).containsExactly("vote=1 narrowed=false");
        assertThat(topicWeight(rust)).isEqualTo(20);
    }

    @Test
    void deleteWithoutAVoteIsANoOp() throws Exception {
        long feedId = insertFeed();
        long rust = insertTopic(FEEDBACK_TOPIC_PREFIX + "rust", 20);
        long a = insertMatchingArticle(feedId, "a", rust, 0.95);

        String body = deleteVote(a);

        assertEffect(body, 0, rust, 20.0, 20.0);
        assertThat(storedFeedback(a)).isEmpty();
        assertThat(topicWeight(rust)).isEqualTo(20);
    }

    @Test
    void narrowedDownVoteMovesOnlyPickedTopics() throws Exception {
        long feedId = insertFeed();
        long rust = insertTopic(FEEDBACK_TOPIC_PREFIX + "rust", 20);
        long politics = insertTopic(FEEDBACK_TOPIC_PREFIX + "politics", -30);
        long go = insertTopic(FEEDBACK_TOPIC_PREFIX + "go", 10);
        long n = insertThreeTopicArticle(feedId, rust, politics, go);

        String body = putVote(n, "{\"vote\":-1,\"topicIds\":[" + politics + "]}");

        assertEffect(body, 0, rust, 20.0, 20.0);
        assertEffect(body, 1, politics, -30.0, -31.2);
        assertEffect(body, 2, go, 10.0, 10.0);
        assertThat(storedFeedback(n)).containsExactly("vote=-1 narrowed=true");
        assertThat(storedPicks(n)).containsExactly(politics);
        assertThat(topicWeight(politics)).isEqualTo(-30);
    }

    @Test
    void flipToUpClearsNarrowing() throws Exception {
        long feedId = insertFeed();
        long rust = insertTopic(FEEDBACK_TOPIC_PREFIX + "rust", 20);
        long politics = insertTopic(FEEDBACK_TOPIC_PREFIX + "politics", -30);
        long go = insertTopic(FEEDBACK_TOPIC_PREFIX + "go", 10);
        long n = insertThreeTopicArticle(feedId, rust, politics, go);
        putVote(n, "{\"vote\":-1,\"topicIds\":[" + politics + "]}");

        String body = putVote(n, "{\"vote\":1}");

        assertEffect(body, 0, rust, 20.0, 21.8);
        assertEffect(body, 1, politics, -31.2, -28.8);
        assertEffect(body, 2, go, 10.0, 10.4);
        assertThat(storedFeedback(n)).containsExactly("vote=1 narrowed=false");
        assertThat(storedPicks(n)).isEmpty();
    }

    @Test
    void pickingAnUnmatchedTopicIs400AndWritesNothing() throws Exception {
        long feedId = insertFeed();
        long rust = insertTopic(FEEDBACK_TOPIC_PREFIX + "rust", 20);
        long politics = insertTopic(FEEDBACK_TOPIC_PREFIX + "politics", -30);
        long go = insertTopic(FEEDBACK_TOPIC_PREFIX + "go", 10);
        long other = insertTopic(FEEDBACK_TOPIC_PREFIX + "other", 15);
        long n = insertThreeTopicArticle(feedId, rust, politics, go);
        putVote(n, "{\"vote\":1}");

        mockMvc.perform(put("/api/articles/{id}/feedback", n)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vote\":-1,\"topicIds\":[" + other + "]}"))
                .andExpect(status().isBadRequest());

        assertThat(storedFeedback(n)).containsExactly("vote=1 narrowed=false");
        assertThat(storedPicks(n)).isEmpty();
    }

    @Test
    void unscoredVoteIsStoredAndReportsNotScored() throws Exception {
        long feedId = insertFeed();
        long u = insertArticle(feedId, "u", Instant.parse("2026-09-20T10:00:00Z"));

        String body = putVote(u, "{\"vote\":1}");

        assertThat((Boolean) JsonPath.read(body, "$.scored")).isFalse();
        assertThat((List<?>) JsonPath.read(body, "$.effects")).isEmpty();
        assertThat(storedFeedback(u)).containsExactly("vote=1 narrowed=false");
    }

    @Test
    void scoredNoMatchVoteIsStoredWithNoEffects() throws Exception {
        long feedId = insertFeed();
        long rust = insertTopic(FEEDBACK_TOPIC_PREFIX + "rust", 20);
        long c = insertMatchingArticle(feedId, "c", rust, 0.4);

        String body = putVote(c, "{\"vote\":-1}");

        assertThat((Boolean) JsonPath.read(body, "$.scored")).isTrue();
        assertThat((List<?>) JsonPath.read(body, "$.effects")).isEmpty();
        assertThat(storedFeedback(c)).containsExactly("vote=-1 narrowed=false");
        assertThat(topicWeight(rust)).isEqualTo(20);
    }

    @Test
    void voteLeavesReadAndStarredAlone() throws Exception {
        long feedId = insertFeed();
        long rust = insertTopic(FEEDBACK_TOPIC_PREFIX + "rust", 20);
        long a = insertMatchingArticle(feedId, "a", rust, 0.95);

        putVote(a, "{\"vote\":1}");
        assertThat(readAndStarred(a)).isEqualTo("read=false starred=false");
        deleteVote(a);
        assertThat(readAndStarred(a)).isEqualTo("read=false starred=false");
    }

    @Test
    void upVoteEffectNamesItsLimit() throws Exception {
        long feedId = insertFeed();
        long rust = insertTopic(FEEDBACK_TOPIC_PREFIX + "rust", 20);
        long a = insertMatchingArticle(feedId, "a", rust, 0.95);

        String body = putVote(a, "{\"vote\":1}");

        assertThat((String) JsonPath.read(body, "$.effects[0].limit")).isEqualTo("NONE");
    }

    @Test
    void getArticleCarriesTheVoteState() throws Exception {
        long feedId = insertFeed();
        long rust = insertTopic(FEEDBACK_TOPIC_PREFIX + "rust", 20);
        long politics = insertTopic(FEEDBACK_TOPIC_PREFIX + "politics", -30);
        long go = insertTopic(FEEDBACK_TOPIC_PREFIX + "go", 10);
        long n = insertThreeTopicArticle(feedId, rust, politics, go);

        String put = putVote(n, "{\"vote\":-1,\"topicIds\":[" + politics + "]}");
        String get = getArticle(n);

        for (String[] doc : new String[][] {{get, "$.feedback"}, {put, "$.article.feedback"}}) {
            assertThat((Integer) JsonPath.read(doc[0], doc[1] + ".vote")).isEqualTo(-1);
            assertThat((Boolean) JsonPath.read(doc[0], doc[1] + ".narrowed")).isTrue();
            assertThat((List<?>) JsonPath.read(doc[0], doc[1] + ".topics")).hasSize(1);
            assertThat(((Number) JsonPath.read(doc[0], doc[1] + ".topics[0].topicId")).longValue()).isEqualTo(politics);
            assertThat((String) JsonPath.read(doc[0], doc[1] + ".topics[0].name"))
                    .isEqualTo(FEEDBACK_TOPIC_PREFIX + "politics");
        }
    }

    @Test
    void unNarrowedVoteHasNoPicks() throws Exception {
        long feedId = insertFeed();
        long rust = insertTopic(FEEDBACK_TOPIC_PREFIX + "rust", 20);
        long a = insertMatchingArticle(feedId, "a", rust, 0.95);

        putVote(a, "{\"vote\":1}");
        String get = getArticle(a);

        assertThat((Integer) JsonPath.read(get, "$.feedback.vote")).isEqualTo(1);
        assertThat((Boolean) JsonPath.read(get, "$.feedback.narrowed")).isFalse();
        assertThat((List<?>) JsonPath.read(get, "$.feedback.topics")).isEmpty();
    }

    @Test
    void removedVoteHasNoFeedbackKey() throws Exception {
        long feedId = insertFeed();
        long rust = insertTopic(FEEDBACK_TOPIC_PREFIX + "rust", 20);
        long a = insertMatchingArticle(feedId, "a", rust, 0.95);
        putVote(a, "{\"vote\":1}");

        String deleted = deleteVote(a);
        String get = getArticle(a);

        Map<String, Object> deletedArticle = JsonPath.read(deleted, "$.article");
        Map<String, Object> gotArticle = JsonPath.read(get, "$");
        assertThat(deletedArticle).doesNotContainKey("feedback");
        assertThat(gotArticle).doesNotContainKey("feedback");
    }

    @Test
    void listItemsOmitFeedback() throws Exception {
        long feedId = insertFeed();
        long rust = insertTopic(FEEDBACK_TOPIC_PREFIX + "rust", 20);
        long a = insertMatchingArticle(feedId, "a", rust, 0.95);
        putVote(a, "{\"vote\":1}");

        String body = mockMvc.perform(get("/api/articles").param("feedId", String.valueOf(feedId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Map<String, Object>> items = JsonPath.read(body, "$.items");
        assertThat(items).hasSize(1);
        assertThat(((Number) items.get(0).get("id")).longValue()).isEqualTo(a);
        assertThat(items.get(0)).doesNotContainKey("feedback");
    }

    @Test
    void learnedEndpointMatchesTheVoteEffects() throws Exception {
        long feedId = insertFeed();
        long rust = insertTopic(FEEDBACK_TOPIC_PREFIX + "rust", 20);
        long go = insertTopic(FEEDBACK_TOPIC_PREFIX + "go", 10);
        long a = insertMatchingArticle(feedId, "a", rust, 0.95);
        putVote(a, "{\"vote\":1}");

        List<Map<String, Object>> entries = learnedEntries(List.of(rust, go));

        assertThat(entries).extracting(e -> ((Number) e.get("topicId")).longValue()).containsExactly(rust, go);
        assertLearned(entries.get(0), 20.0, 1.8, 21.8, "NONE");
        assertLearnedParts(entries.get(0), 1.8, 0.0);
        assertLearned(entries.get(1), 10.0, 0.0, 10.0, "NONE");
        assertLearnedParts(entries.get(1), 0.0, 0.0);
        assertThat(topicWeight(rust)).isEqualTo(20);
        assertThat(topicWeight(go)).isEqualTo(10);
    }

    @Test
    void learnedEndpointReportsTheEngagementCap() throws Exception {
        long feedId = insertFeed();
        long engcap = insertTopic(FEEDBACK_TOPIC_PREFIX + "engcap", 20);
        for (int i = 0; i < 8; i++) {
            long id = insertMatchingArticle(feedId, "eng" + i, engcap, 1.0);
            insertEngagement(id, "STAR");
        }

        List<Map<String, Object>> entries = learnedEntries(List.of(engcap));

        assertThat(entries).hasSize(1);
        assertLearned(entries.get(0), 20.0, 8.0, 28.0, "ENGAGEMENT_CAP");
        assertLearnedParts(entries.get(0), 0.0, 8.0);
        assertThat(topicWeight(engcap)).isEqualTo(20);
    }

    @Test
    void learnedEndpointSplitsVotesAndEngagement() throws Exception {
        long feedId = insertFeed();
        long mixed = insertTopic(FEEDBACK_TOPIC_PREFIX + "mixed", 20);
        long voted = insertMatchingArticle(feedId, "voted", mixed, 0.95);
        long saved = insertMatchingArticle(feedId, "saved", mixed, 0.95);
        putVote(voted, "{\"vote\":1}");
        insertEngagement(saved, "STAR");

        List<Map<String, Object>> entries = learnedEntries(List.of(mixed));

        assertThat(entries).hasSize(1);
        assertLearned(entries.get(0), 20.0, 2.7, 22.7, "NONE");
        assertLearnedParts(entries.get(0), 1.8, 0.9);
        assertThat(topicWeight(mixed)).isEqualTo(20);
    }

    @Test
    void learnedEntryDisappearsWithItsTopic() throws Exception {
        long rust = insertTopic(FEEDBACK_TOPIC_PREFIX + "rust", 20);
        long go = insertTopic(FEEDBACK_TOPIC_PREFIX + "go", 10);
        assertThat(learnedEntries(List.of(rust, go))).hasSize(2);

        mockMvc.perform(delete("/api/interest/topics/{id}", go))
                .andExpect(status().isNoContent());

        List<Map<String, Object>> entries = learnedEntries(List.of(rust, go));
        assertThat(entries).extracting(e -> ((Number) e.get("topicId")).longValue()).containsExactly(rust);
    }

    /** The learned entries for {@code ids} (the container is shared), in the order the endpoint served them. */
    private List<Map<String, Object>> learnedEntries(List<Long> ids) throws Exception {
        String body = mockMvc.perform(get("/api/interest/topics/learned"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> all = JsonPath.read(body, "$");
        return all.stream().filter(e -> ids.contains(((Number) e.get("topicId")).longValue())).toList();
    }

    private static void assertLearned(Map<String, Object> entry, double base, double learned, double effective,
                                      String limit) {
        assertThat(((Number) entry.get("baseWeight")).doubleValue()).isEqualTo(base);
        assertThat(((Number) entry.get("learned")).doubleValue()).isEqualTo(learned);
        assertThat(((Number) entry.get("effectiveWeight")).doubleValue()).isEqualTo(effective);
        assertThat(entry.get("limit")).isEqualTo(limit);
    }

    private static void assertLearnedParts(Map<String, Object> entry, double thumbs, double engagement) {
        assertThat(((Number) entry.get("thumbsLearned")).doubleValue()).isEqualTo(thumbs);
        assertThat(((Number) entry.get("engagementLearned")).doubleValue()).isEqualTo(engagement);
    }

    /** Article N: rust noul 0.95 (m 0.9), politics 0.8 (m 0.6), go 0.6 (m 0.2). */
    private long insertThreeTopicArticle(long feedId, long rust, long politics, long go) {
        long n = insertMatchingArticle(feedId, "n", rust, 0.95);
        insertTopicScore(n, politics, 0.8);
        insertTopicScore(n, go, 0.6);
        return n;
    }

    private List<Long> storedPicks(long articleId) {
        return jdbcTemplate.queryForList(
                "SELECT topic_id FROM article_feedback_topic WHERE article_id = ? ORDER BY topic_id",
                Long.class, articleId);
    }

    private String readAndStarred(long articleId) {
        return jdbcTemplate.queryForObject("SELECT read, starred FROM article WHERE id = ?",
                (rs, rowNum) -> "read=" + rs.getBoolean("read") + " starred=" + rs.getBoolean("starred"),
                articleId);
    }

    /** A SCORED article (profile 2.0/4) that judged {@code topicId} at {@code noul}. */
    private long insertMatchingArticle(long feedId, String guid, long topicId, double noul) {
        long id = insertArticle(feedId, guid, Instant.parse("2026-09-20T10:00:00Z"));
        insertScored(id, 2.0, 4);
        insertTopicScore(id, topicId, noul);
        return id;
    }

    private static void assertEffect(String body, int index, long topicId, double before, double after) {
        Map<String, Object> effect = JsonPath.read(body, "$.effects[" + index + "]");
        assertThat(((Number) effect.get("topicId")).longValue()).isEqualTo(topicId);
        assertThat(((Number) effect.get("before")).doubleValue()).isEqualTo(before);
        assertThat(((Number) effect.get("after")).doubleValue()).isEqualTo(after);
    }

    private String deleteVote(long articleId) throws Exception {
        return mockMvc.perform(delete("/api/articles/{id}/feedback", articleId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String putVote(long articleId, String json) throws Exception {
        return mockMvc.perform(put("/api/articles/{id}/feedback", articleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String getArticle(long articleId) throws Exception {
        return mockMvc.perform(get("/api/articles/{id}", articleId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private Integer badge(long articleId) throws Exception {
        return JsonPath.read(getArticle(articleId), "$.interestScore");
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

    private void insertEngagement(long articleId, String kind) {
        jdbcTemplate.update("INSERT INTO article_engagement (article_id, kind) VALUES (?, ?)", articleId, kind);
    }

    private void insertScored(long articleId, double profileScore, int profileMaxLevel) {
        jdbcTemplate.update(
                "INSERT INTO article_score (article_id, status, profile_score, profile_max_level, attempts) "
                        + "VALUES (?, 'SCORED', ?, ?, 1)",
                articleId, profileScore, profileMaxLevel);
    }
}
