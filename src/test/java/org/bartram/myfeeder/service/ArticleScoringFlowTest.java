package org.bartram.myfeeder.service;

import org.bartram.myfeeder.TestcontainersConfiguration;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.integration.JevApiClient;
import org.bartram.myfeeder.integration.JevJudgment;
import org.bartram.myfeeder.integration.JevJudgment.JevScore;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.model.InterestTopic;
import org.bartram.myfeeder.repository.ArticleScoreStore;
import org.bartram.myfeeder.repository.TopicSuggestionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springaicommunity.typesafe.question.Question;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Scorer → store → Postgres with the real {@link InterestService}; only the Jev client is mocked.
 */
@DataJdbcTest
@Import({TestcontainersConfiguration.class, ArticleScoreStore.class, InterestService.class,
        TopicSuggestionStore.class})
class ArticleScoringFlowTest {

    private static final String FEED_TITLE = "Hacker News";

    @Autowired private ArticleScoreStore store;
    @Autowired private InterestService interestService;
    @Autowired private JdbcTemplate jdbc;

    private JevApiClient jevApiClient;
    private ArticleScoringService scorer;
    private long feedId;
    private Instant now;

    @BeforeEach
    void setUp() {
        jevApiClient = Mockito.mock(JevApiClient.class);
        when(jevApiClient.isConfigured()).thenReturn(true);
        scorer = new ArticleScoringService(jevApiClient, interestService, store, new MyfeederProperties());

        // Inside the rolled-back test transaction
        jdbc.update("DELETE FROM article_score");
        jdbc.update("DELETE FROM article");
        jdbc.update("DELETE FROM interest_topic");
        feedId = jdbc.queryForObject(
                "INSERT INTO feed (url, title, feed_type) VALUES (?, ?, ?) RETURNING id",
                Long.class, "https://news.ycombinator.com/rss", FEED_TITLE, "RSS");
        now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }

    @Test
    void scoresAnEligibleArticleOnceAndStoresRawOutputs() {
        interestService.updateProfile("Rust and Postgres");
        int profileVersion = interestService.getProfile().getVersion();
        InterestTopic rust = interestService.createTopic("Rust", "The Rust programming language", 30, null);
        InterestTopic crypto = interestService.createTopic("Crypto", "Cryptocurrency markets", -40, null);
        Article article = article("g-1", "Rust 1.90 released", "<p>Faster compile times</p>", false);
        long articleId = insert(article);

        when(jevApiClient.judge(anyMap(), anyMap())).thenReturn(new JevJudgment("jev-1.13.0", "req-1",
                Map.of(InterestQuestions.topicKey(rust.getId()), 0.92,
                        InterestQuestions.topicKey(crypto.getId()), 0.03),
                Map.of("profile", new JevScore(3.2, 4, 0.81)), 100, 5));

        scorer.score(articleId);
        scorer.score(articleId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> stateCaptor = ArgumentCaptor.forClass(Map.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Question>> questionsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(jevApiClient, times(1)).judge(stateCaptor.capture(), questionsCaptor.capture());

        Map<String, Object> expectedState = ArticleStateBuilder.build(FEED_TITLE, article);
        assertThat(stateCaptor.getValue()).isEqualTo(expectedState);
        assertThat(stateCaptor.getValue())
                .containsEntry("feed", FEED_TITLE)
                .containsEntry("title", "Rust 1.90 released")
                .containsEntry("summary", "Faster compile times");
        assertThat(questionsCaptor.getValue())
                .isEqualTo(InterestQuestions.forRubric("Rust and Postgres", interestService.listTopics()));

        Map<String, Object> score = jdbc.queryForMap("SELECT * FROM article_score WHERE article_id = ?", articleId);
        assertThat(score.get("status")).isEqualTo("SCORED");
        assertThat(score.get("profile_score")).isEqualTo(3.2);
        assertThat(score.get("profile_max_level")).isEqualTo(4);
        assertThat(score.get("profile_confidence")).isEqualTo(0.81);
        assertThat(score.get("profile_version")).isEqualTo(profileVersion);
        assertThat(profileVersion).isEqualTo(2);
        assertThat(score.get("model")).isEqualTo("jev-1.13.0");
        assertThat(score.get("request_id")).isEqualTo("req-1");
        assertThat(score.get("attempts")).isEqualTo(1);

        List<Map<String, Object>> topics = jdbc.queryForList(
                "SELECT topic_id, noul, topic_version FROM article_topic_score WHERE article_id = ?", articleId);
        Map<Long, Double> nouls = new java.util.HashMap<>();
        topics.forEach(t -> nouls.put(((Number) t.get("topic_id")).longValue(), (Double) t.get("noul")));
        assertThat(nouls).containsOnly(Map.entry(rust.getId(), 0.92), Map.entry(crypto.getId(), 0.03));
        assertThat(topics).allSatisfy(t -> assertThat(t.get("topic_version")).isEqualTo(1));
    }

    @Test
    void readArticleIsNeverJudged() {
        interestService.updateProfile("Rust and Postgres");
        interestService.createTopic("Rust", "The Rust programming language", 30, null);
        long articleId = insert(article("g-read", "Rust 1.90 released", "<p>Faster compile times</p>", true));

        scorer.score(articleId);

        verify(jevApiClient, never()).judge(anyMap(), anyMap());
        assertThat(scoreRowCount(articleId)).isZero();
    }

    @Test
    void coldStartJudgesNothing() {
        interestService.updateProfile("");
        assertThat(interestService.isColdStart()).isTrue();
        long articleId = insert(article("g-cold", "Rust 1.90 released", "<p>Faster compile times</p>", false));

        scorer.score(articleId);

        verify(jevApiClient, never()).judge(anyMap(), anyMap());
        assertThat(scoreRowCount(articleId)).isZero();
    }

    private Article article(String guid, String title, String summary, boolean read) {
        Article article = new Article();
        article.setFeedId(feedId);
        article.setGuid(guid);
        article.setTitle(title);
        article.setUrl("https://example.com/" + guid);
        article.setSummary(summary);
        article.setPublishedAt(now.minus(Duration.ofHours(1)));
        article.setFetchedAt(now);
        article.setRead(read);
        return article;
    }

    private long insert(Article a) {
        long id = jdbc.queryForObject(
                "INSERT INTO article (feed_id, guid, title, url, summary, content, published_at, fetched_at, \"read\") "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class, a.getFeedId(), a.getGuid(), a.getTitle(), a.getUrl(), a.getSummary(), a.getContent(),
                Timestamp.from(a.getPublishedAt()), Timestamp.from(a.getFetchedAt()), a.isRead());
        a.setId(id);
        return id;
    }

    private int scoreRowCount(long articleId) {
        return jdbc.queryForObject("SELECT count(*) FROM article_score WHERE article_id = ?", Integer.class, articleId);
    }
}
