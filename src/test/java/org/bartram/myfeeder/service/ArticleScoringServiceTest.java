package org.bartram.myfeeder.service;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.integration.JevApiClient;
import org.bartram.myfeeder.integration.JevJudgment;
import org.bartram.myfeeder.integration.JevJudgment.JevScore;
import org.bartram.myfeeder.integration.JevNotConfiguredException;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.model.InterestProfile;
import org.bartram.myfeeder.model.InterestTopic;
import org.bartram.myfeeder.repository.ArticleScoreStore;
import org.bartram.myfeeder.repository.ArticleScoreStore.Candidate;
import org.bartram.myfeeder.repository.ArticleScoreStore.ScoredRow;
import org.bartram.myfeeder.repository.ArticleScoreStore.TopicNoul;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springaicommunity.typesafe.exception.TypeSafeAnswerTypeException;
import org.springaicommunity.typesafe.exception.TypeSafeApiConnectionException;
import org.springaicommunity.typesafe.exception.TypeSafeApiException;
import org.springaicommunity.typesafe.exception.TypeSafeApiResponseValidationException;
import org.springaicommunity.typesafe.exception.TypeSafeAuthenticationException;
import org.springaicommunity.typesafe.exception.TypeSafeBadRequestException;
import org.springaicommunity.typesafe.exception.TypeSafeException;
import org.springaicommunity.typesafe.exception.TypeSafeInternalServerException;
import org.springaicommunity.typesafe.exception.TypeSafeMissingAnswerException;
import org.springaicommunity.typesafe.exception.TypeSafeNotFoundException;
import org.springaicommunity.typesafe.exception.TypeSafeOverloadedException;
import org.springaicommunity.typesafe.exception.TypeSafePermissionDeniedException;
import org.springaicommunity.typesafe.exception.TypeSafeRateLimitException;
import org.springaicommunity.typesafe.exception.TypeSafeUnprocessableEntityException;
import org.springaicommunity.typesafe.response.AnswerType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleScoringServiceTest {

    private static final long ID = 7L;
    private static final long TOPIC_ID = 11L;
    private static final String LEAK = "LEAKCHECK";
    private static final String ENDPOINT = "/v1/jev";

    @Mock private JevApiClient jevApiClient;
    @Mock private InterestService interestService;
    @Mock private ArticleScoreStore store;

    @Captor private ArgumentCaptor<ScoredRow> rowCaptor;
    @Captor private ArgumentCaptor<String> textCaptor;

    private ArticleScoringService scorer;

    @BeforeEach
    void setUp() {
        scorer = new ArticleScoringService(jevApiClient, interestService, store, new MyfeederProperties());
        lenient().when(jevApiClient.isConfigured()).thenReturn(true);
        lenient().when(interestService.isColdStart()).thenReturn(false);
        lenient().when(interestService.getProfile()).thenReturn(profile("Rust and Postgres", 2));
        lenient().when(interestService.listTopics())
                .thenReturn(List.of(topic(TOPIC_ID, "The Rust programming language", 1)));
        lenient().when(store.loadCandidate(eq(ID), any()))
                .thenReturn(Optional.of(candidate("g-1", "Rust 1.90 released", "Faster compile times")));
    }

    @Test
    void notConfiguredTouchesNothing() {
        when(jevApiClient.isConfigured()).thenReturn(false);

        scorer.score(ID);

        verifyNoInteractions(store);
        verify(jevApiClient, never()).judge(anyMap(), anyMap());
    }

    @Test
    void coldStartTouchesNothing() {
        when(interestService.isColdStart()).thenReturn(true);

        scorer.score(ID);

        verifyNoInteractions(store);
        verify(jevApiClient, never()).judge(anyMap(), anyMap());
    }

    @Test
    void noCandidateMeansNoCall() {
        when(store.loadCandidate(eq(ID), any())).thenReturn(Optional.empty());

        scorer.score(ID);

        verify(jevApiClient, never()).judge(anyMap(), anyMap());
        verify(store).loadCandidate(eq(ID), any());
        verifyNoMoreInteractions(store);
    }

    @Test
    void blankGuidIsSkippedWithoutACall() {
        when(store.loadCandidate(eq(ID), any())).thenReturn(
                Optional.of(candidate(null, "Rust 1.90 released", "Faster compile times")),
                Optional.of(candidate("", "Rust 1.90 released", "Faster compile times")));

        scorer.score(ID);
        scorer.score(ID);

        verify(store, times(2)).writeSkipped(ID, "no guid");
        verify(jevApiClient, never()).judge(anyMap(), anyMap());
        verify(store, never()).writeScored(anyLong(), any());
        verify(store, never()).writeFailed(anyLong(), anyString());
    }

    @Test
    void articleWithoutTextIsSkippedWithoutACall() {
        when(store.loadCandidate(eq(ID), any())).thenReturn(Optional.of(candidate("g-1", null, null)));

        scorer.score(ID);

        verify(store).writeSkipped(ID, "no text");
        verify(jevApiClient, never()).judge(anyMap(), anyMap());
        verify(store, never()).writeScored(anyLong(), any());
        verify(store, never()).writeFailed(anyLong(), anyString());
    }

    @Test
    void emptyRubricIsNeverJudged() {
        // isColdStart said false, but the rubric was cleared before the snapshot (a race)
        when(interestService.getProfile()).thenReturn(profile("   ", 3));
        when(interestService.listTopics()).thenReturn(List.of());

        scorer.score(ID);

        verify(jevApiClient, never()).judge(anyMap(), anyMap());
        verify(store).loadCandidate(eq(ID), any());
        verifyNoMoreInteractions(store);
    }

    @Test
    void permanentFailuresRecordAFixedTextAttempt() {
        List<RuntimeException> permanent = List.of(
                new TypeSafeBadRequestException(LEAK, 400, LEAK, headers(), ENDPOINT),
                new TypeSafeUnprocessableEntityException(LEAK, 422, LEAK, headers(), ENDPOINT),
                new TypeSafeNotFoundException(LEAK, 404, LEAK, headers(), ENDPOINT),
                new TypeSafeApiResponseValidationException(LEAK, 200, LEAK, headers(), ENDPOINT, LEAK),
                new TypeSafeMissingAnswerException(LEAK, List.of(LEAK)),
                new TypeSafeAnswerTypeException(LEAK, AnswerType.NOUL, AnswerType.SCORE),
                new TypeSafeException(LEAK),
                new IllegalArgumentException(LEAK));

        for (RuntimeException e : permanent) {
            clearInvocations(store);
            doThrow(e).when(jevApiClient).judge(anyMap(), anyMap());

            scorer.score(ID);

            verify(store, times(1)).writeFailed(eq(ID), textCaptor.capture());
            String text = textCaptor.getValue();
            assertThat(text).as(e.getClass().getSimpleName())
                    .startsWith(e.getClass().getSimpleName())
                    .doesNotContain(LEAK);
            if (e instanceof TypeSafeApiException api) {
                assertThat(text).contains("(HTTP " + api.status()).contains("req-9");
            }
            verify(store, never()).writeScored(anyLong(), any());
            verify(store, never()).writeSkipped(anyLong(), anyString());
        }
    }

    @Test
    void transientFailuresWriteNothing() {
        List<RuntimeException> transients = List.of(
                new TypeSafeRateLimitException(LEAK, 429, LEAK, headers(), ENDPOINT, 1000L),
                new TypeSafeInternalServerException(LEAK, 500, LEAK, headers(), ENDPOINT),
                new TypeSafeOverloadedException(LEAK, 529, LEAK, headers(), ENDPOINT),
                new TypeSafeApiConnectionException(LEAK, new RuntimeException(LEAK)),
                CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("jev")),
                new JevNotConfiguredException(),
                new TypeSafeAuthenticationException(LEAK, 401, LEAK, headers(), ENDPOINT),
                new TypeSafePermissionDeniedException(LEAK, 403, LEAK, headers(), ENDPOINT));

        for (RuntimeException e : transients) {
            clearInvocations(store);
            doThrow(e).when(jevApiClient).judge(anyMap(), anyMap());

            scorer.score(ID);

            verify(store, never()).writeFailed(anyLong(), anyString());
            verify(store, never()).writeScored(anyLong(), any());
            verify(store, never()).writeSkipped(anyLong(), anyString());
        }
    }

    @Test
    void invalidAnswerRecordsAFailedAttempt() {
        when(jevApiClient.judge(anyMap(), anyMap())).thenReturn(
                judgment(Map.of(InterestQuestions.topicKey(TOPIC_ID), 1.2), new JevScore(3.0, 4, 0.8)),
                judgment(Map.of(InterestQuestions.topicKey(TOPIC_ID), Double.NaN), new JevScore(3.0, 4, 0.8)),
                judgment(Map.of(InterestQuestions.topicKey(TOPIC_ID), 0.5), new JevScore(5.0, 4, 0.8)));

        scorer.score(ID);
        scorer.score(ID);
        scorer.score(ID);

        verify(store, times(3)).writeFailed(ID, "invalid answer");
        verify(store, never()).writeScored(anyLong(), any());
    }

    @Test
    void boundaryNoulsAreStoredExactly() {
        long otherTopic = 12L;
        when(interestService.listTopics()).thenReturn(List.of(
                topic(TOPIC_ID, "The Rust programming language", 1), topic(otherTopic, "Cryptocurrency markets", 3)));
        when(jevApiClient.judge(anyMap(), anyMap())).thenReturn(judgment(
                Map.of(InterestQuestions.topicKey(TOPIC_ID), 0.0, InterestQuestions.topicKey(otherTopic), 1.0),
                new JevScore(0.123456789, 4, 0.987654321)));

        scorer.score(ID);

        verify(store).writeScored(eq(ID), rowCaptor.capture());
        ScoredRow row = rowCaptor.getValue();
        assertThat(row.topics()).containsExactly(new TopicNoul(TOPIC_ID, 0.0, 1), new TopicNoul(otherTopic, 1.0, 3));
        assertThat(row.profileScore()).isEqualTo(0.123456789);
        assertThat(row.profileConfidence()).isEqualTo(0.987654321);
        assertThat(row.profileVersion()).isEqualTo(2);
        assertThat(row.model()).isEqualTo("jev-1.13.0");
        assertThat(row.requestId()).isEqualTo("req-1");
        verify(store, never()).writeFailed(anyLong(), anyString());
    }

    @Test
    void profileSavedMidCallDiscardsTheResultForTheSweep() {
        when(interestService.getProfile()).thenReturn(profile("Rust and Postgres", 2), profile("Go and SQLite", 3));
        when(jevApiClient.judge(anyMap(), anyMap())).thenReturn(
                judgment(Map.of(InterestQuestions.topicKey(TOPIC_ID), 0.5), new JevScore(3.0, 4, 0.8)));

        scorer.score(ID);

        verify(store, never()).writeScored(anyLong(), any());
        verify(store, never()).writeFailed(anyLong(), anyString());
        verify(store, never()).writeSkipped(anyLong(), anyString());
    }

    @Test
    void topicEditedAddedOrDeletedMidCallDiscardsTheResultForTheSweep() {
        long otherTopic = 12L;
        when(interestService.listTopics()).thenReturn(
                List.of(topic(TOPIC_ID, "The Rust programming language", 1)),
                List.of(topic(TOPIC_ID, "The Rust compiler", 2)),
                List.of(topic(TOPIC_ID, "The Rust programming language", 1)),
                List.of(topic(TOPIC_ID, "The Rust programming language", 1), topic(otherTopic, "Go", 1)),
                List.of(topic(TOPIC_ID, "The Rust programming language", 1)),
                List.of());
        when(jevApiClient.judge(anyMap(), anyMap())).thenReturn(
                judgment(Map.of(InterestQuestions.topicKey(TOPIC_ID), 0.5), new JevScore(3.0, 4, 0.8)));

        scorer.score(ID);
        scorer.score(ID);
        scorer.score(ID);

        verify(jevApiClient, times(3)).judge(anyMap(), anyMap());
        verify(store, never()).writeScored(anyLong(), any());
        verify(store, never()).writeFailed(anyLong(), anyString());
    }

    @Test
    void weightOnlyTopicEditMidCallStillStoresTheScore() {
        InterestTopic reweighted = topic(TOPIC_ID, "The Rust programming language", 1);
        reweighted.setWeight(-40);
        when(interestService.listTopics()).thenReturn(
                List.of(topic(TOPIC_ID, "The Rust programming language", 1)), List.of(reweighted));
        when(jevApiClient.judge(anyMap(), anyMap())).thenReturn(
                judgment(Map.of(InterestQuestions.topicKey(TOPIC_ID), 0.5), new JevScore(3.0, 4, 0.8)));

        scorer.score(ID);

        verify(store).writeScored(eq(ID), any());
    }

    @Test
    void writeErrorAfterBilledSuccessRecordsAFailedAttempt() {
        when(jevApiClient.judge(anyMap(), anyMap())).thenReturn(
                judgment(Map.of(InterestQuestions.topicKey(TOPIC_ID), 0.5), new JevScore(3.0, 4, 0.8)));
        when(store.writeScored(eq(ID), any())).thenThrow(new DataIntegrityViolationException(LEAK));

        scorer.score(ID);

        verify(store).writeFailed(ID, "write failed");
    }

    @Test
    void missingLegendFallsBackToProfileMaxLevel() {
        when(jevApiClient.judge(anyMap(), anyMap())).thenReturn(
                judgment(Map.of(InterestQuestions.topicKey(TOPIC_ID), 0.5), new JevScore(3.0, -1, 0.8)));

        scorer.score(ID);

        verify(store).writeScored(eq(ID), rowCaptor.capture());
        assertThat(rowCaptor.getValue().profileMaxLevel()).isEqualTo(InterestQuestions.PROFILE_MAX_LEVEL).isEqualTo(4);
    }

    @Test
    void blankProfileStoresNullProfileColumns() {
        when(interestService.getProfile()).thenReturn(profile("", 5));
        when(jevApiClient.judge(anyMap(), anyMap())).thenReturn(
                judgment(Map.of(InterestQuestions.topicKey(TOPIC_ID), 0.4), null));

        scorer.score(ID);

        verify(store).writeScored(eq(ID), rowCaptor.capture());
        ScoredRow row = rowCaptor.getValue();
        assertThat(row.profileScore()).isNull();
        assertThat(row.profileMaxLevel()).isNull();
        assertThat(row.profileConfidence()).isNull();
        assertThat(row.profileVersion()).isNull();
        assertThat(row.topics()).containsExactly(new TopicNoul(TOPIC_ID, 0.4, 1));
    }

    @Test
    void scorerHoldsNoTransactionAndNoBreakerHandle() throws NoSuchMethodException {
        assertThat(transactional(ArticleScoringService.class.getAnnotations())).isFalse();
        assertThat(transactional(ArticleScoringService.class.getMethod("score", long.class).getAnnotations()))
                .isFalse();

        Constructor<?>[] constructors = ArticleScoringService.class.getConstructors();
        assertThat(constructors).hasSize(1);
        assertThat(Arrays.asList(constructors[0].getParameterTypes())).containsExactly(
                JevApiClient.class, InterestService.class, ArticleScoreStore.class, MyfeederProperties.class);
    }

    private static boolean transactional(Annotation[] annotations) {
        return Arrays.stream(annotations)
                .anyMatch(a -> a.annotationType().getSimpleName().equals("Transactional"));
    }

    private static HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("x-typesafe-request-id", "req-9");
        return headers;
    }

    private static JevJudgment judgment(Map<String, Double> nouls, JevScore profileScore) {
        Map<String, JevScore> scores = new LinkedHashMap<>();
        if (profileScore != null) {
            scores.put(InterestQuestions.PROFILE_KEY, profileScore);
        }
        return new JevJudgment("jev-1.13.0", "req-1", nouls, scores, 100, 5);
    }

    private static Candidate candidate(String guid, String title, String summary) {
        Article article = new Article();
        article.setId(ID);
        article.setFeedId(1L);
        article.setGuid(guid);
        article.setTitle(title);
        article.setSummary(summary);
        return new Candidate(article, "Hacker News");
    }

    private static InterestProfile profile(String text, int version) {
        InterestProfile profile = new InterestProfile();
        profile.setId(1);
        profile.setProfileText(text);
        profile.setVersion(version);
        return profile;
    }

    private static InterestTopic topic(long id, String description, int version) {
        InterestTopic topic = new InterestTopic();
        topic.setId(id);
        topic.setName("Topic " + id);
        topic.setDescription(description);
        topic.setWeight(20);
        topic.setVersion(version);
        return topic;
    }
}
