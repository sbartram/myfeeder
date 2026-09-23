package org.bartram.myfeeder.service;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.bartram.myfeeder.integration.JevApiClient;
import org.bartram.myfeeder.integration.JevJudgment;
import org.bartram.myfeeder.integration.JevNotConfiguredException;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.repository.ArticleRepository;
import org.bartram.myfeeder.repository.FeedRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springaicommunity.typesafe.question.Question;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterestPreviewServiceTest {

    @Mock private ArticleRepository articleRepository;
    @Mock private FeedRepository feedRepository;
    @Mock private JevApiClient jevApiClient;
    @InjectMocks private InterestPreviewService previewService;

    @Captor private ArgumentCaptor<Map<String, Object>> stateCaptor;
    @Captor private ArgumentCaptor<Map<String, Question>> questionsCaptor;

    @Test
    void previewSendsExactlyOneNoulUnderTheDraftKey() {
        Article article = article(5L, "Rust 1.90 released", "Faster compile times");
        givenArticleInFeed(article, "Hacker News");
        when(jevApiClient.judge(anyMap(), anyMap())).thenReturn(judgment("topic_draft", 0.5));

        previewService.preview(5L, "  Rust  ", null);

        verify(jevApiClient).judge(stateCaptor.capture(), questionsCaptor.capture());
        Map<String, Question> questions = questionsCaptor.getValue();
        assertThat(questions).hasSize(1);
        assertThat(questions).containsOnlyKeys("topic_draft");
        assertThat(questions.get("topic_draft")).isEqualTo(InterestQuestions.topic("Rust"));
        assertThat(stateCaptor.getValue()).isEqualTo(ArticleStateBuilder.build("Hacker News", article));
    }

    @Test
    void previewUsesTopicKeyForSavedTopic() {
        givenArticleInFeed(article(5L, "Rust 1.90 released", "Faster compile times"), "Hacker News");
        when(jevApiClient.judge(anyMap(), anyMap())).thenReturn(judgment("topic_7", 0.5));

        previewService.preview(5L, "Rust", 7L);

        verify(jevApiClient).judge(anyMap(), questionsCaptor.capture());
        assertThat(questionsCaptor.getValue()).containsOnlyKeys("topic_7");
    }

    @Test
    void previewReturnsNoulAndModel() {
        givenArticleInFeed(article(5L, "Rust 1.90 released", "Faster compile times"), "Hacker News");
        when(jevApiClient.judge(anyMap(), anyMap())).thenReturn(judgment("topic_draft", 0.82));

        TopicPreviewResponse response = previewService.preview(5L, "Rust", null);

        assertThat(response).isEqualTo(new TopicPreviewResponse(0.82, "jev-1.13.0"));
    }

    @Test
    void previewRejectsMissingArticleIdBeforeAnyCall() {
        assertThatThrownBy(() -> previewService.preview(null, "Rust", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("articleId is required");
        verifyNoInteractions(jevApiClient);
    }

    @Test
    void previewRejectsBlankDescriptionBeforeAnyCall() {
        assertThatThrownBy(() -> previewService.preview(5L, "   ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Write a description first");
        assertThatThrownBy(() -> previewService.preview(5L, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Write a description first");
        verifyNoInteractions(jevApiClient);
    }

    @Test
    void previewRejectsTooLongDescription() {
        String tooLong = "x".repeat(InterestService.MAX_DESCRIPTION_CHARS + 1);

        assertThatThrownBy(() -> previewService.preview(5L, tooLong, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("description can be at most 500 characters");
        verifyNoInteractions(jevApiClient);
    }

    @Test
    void previewMissingArticleThrowsNotFound() {
        when(articleRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> previewService.preview(5L, "Rust", null))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Article not found: 5");
        verifyNoInteractions(jevApiClient);
    }

    @Test
    void previewRejectsArticleWithoutText() {
        Article empty = article(5L, " ", null);
        givenArticleInFeed(empty, "Hacker News");

        assertThatThrownBy(() -> previewService.preview(5L, "Rust", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("This article has no text to judge");
        verifyNoInteractions(jevApiClient);
    }

    @Test
    void previewPropagatesJevExceptionsWithoutRetrying() {
        givenArticleInFeed(article(5L, "Rust 1.90 released", "Faster compile times"), "Hacker News");

        JevNotConfiguredException notConfigured = new JevNotConfiguredException();
        when(jevApiClient.judge(anyMap(), anyMap())).thenThrow(notConfigured);
        assertThatThrownBy(() -> previewService.preview(5L, "Rust", null)).isSameAs(notConfigured);
        verify(jevApiClient, times(1)).judge(anyMap(), anyMap());

        CallNotPermittedException breakerOpen =
                CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("jev"));
        doThrow(breakerOpen).when(jevApiClient).judge(anyMap(), anyMap());
        assertThatThrownBy(() -> previewService.preview(5L, "Rust", null)).isSameAs(breakerOpen);
        verify(jevApiClient, times(2)).judge(anyMap(), anyMap());
    }

    @Test
    void previewHasNoWriteCollaborators() {
        Constructor<?>[] constructors = InterestPreviewService.class.getConstructors();
        assertThat(constructors).hasSize(1);
        assertThat(Arrays.asList(constructors[0].getParameterTypes()))
                .containsExactly(ArticleRepository.class, FeedRepository.class, JevApiClient.class);

        givenArticleInFeed(article(5L, "Rust 1.90 released", "Faster compile times"), "Hacker News");
        when(jevApiClient.judge(anyMap(), anyMap())).thenReturn(judgment("topic_draft", 0.5));
        previewService.preview(5L, "Rust", null);

        verify(articleRepository, never()).save(any());
        verify(feedRepository, never()).save(any());
    }

    private void givenArticleInFeed(Article article, String feedTitle) {
        Feed feed = new Feed();
        feed.setId(article.getFeedId());
        feed.setTitle(feedTitle);
        when(articleRepository.findById(article.getId())).thenReturn(Optional.of(article));
        when(feedRepository.findById(article.getFeedId())).thenReturn(Optional.of(feed));
    }

    private static Article article(long id, String title, String summary) {
        Article article = new Article();
        article.setId(id);
        article.setFeedId(3L);
        article.setTitle(title);
        article.setSummary(summary);
        return article;
    }

    private static JevJudgment judgment(String key, double noul) {
        return new JevJudgment("jev-1.13.0", "req-1", Map.of(key, noul), Map.of(), null, null);
    }
}
