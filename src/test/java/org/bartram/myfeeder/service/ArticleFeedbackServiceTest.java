package org.bartram.myfeeder.service;

import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.repository.ArticleFeedbackStore;
import org.bartram.myfeeder.repository.InterestScoreQueries;
import org.bartram.myfeeder.repository.InterestScoreQueries.TopicWeight;
import org.bartram.myfeeder.service.FeedbackResult.TopicEffect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleFeedbackServiceTest {

    private static final long ID = 7L;

    @Mock private ArticleService articleService;
    @Mock private ArticleFeedbackStore store;
    @Mock private InterestScoreQueries queries;

    private ArticleFeedbackService service;
    private Article article;

    @BeforeEach
    void setUp() {
        service = new ArticleFeedbackService(articleService, store, queries, new MyfeederProperties());
        article = new Article();
        article.setId(ID);
    }

    @Test
    void rejectsAVoteOtherThanPlusOrMinusOne() {
        for (Integer vote : Arrays.asList(0, 2, null)) {
            assertThatThrownBy(() -> service.vote(ID, vote, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("vote must be 1 or -1");
        }
        verify(store, never()).upsert(anyLong(), anyInt(), any());
        verifyNoInteractions(articleService);
    }

    @Test
    void rejectsAnEmptyTopicList() {
        assertThatThrownBy(() -> service.vote(ID, -1, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("topicIds must not be empty");
        verify(store, never()).upsert(anyLong(), anyInt(), any());
    }

    @Test
    void rejectsMoreTopicIdsThanTopicsCanExist() {
        List<Long> ids = LongStream.rangeClosed(1, 26).boxed().toList();

        assertThatThrownBy(() -> service.vote(ID, -1, ids))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("topicIds has too many entries");
        verify(store, never()).upsert(anyLong(), anyInt(), any());
        verifyNoInteractions(articleService, queries);
    }

    @Test
    void missingArticleIsNotFound() {
        when(articleService.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.vote(99L, 1, null))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Article not found: 99");
        verify(store, never()).upsert(anyLong(), anyInt(), any());
    }

    @Test
    void rejectsATopicTheArticleDidNotMatch() {
        givenArticle();
        when(queries.matchedTopicIds(ID)).thenReturn(List.of(10L, 11L));

        assertThatThrownBy(() -> service.vote(ID, -1, List.of(12L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("topicIds must be topics this article matched");
        assertThatThrownBy(() -> service.vote(ID, -1, Arrays.asList(10L, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("topicIds must be topics this article matched");
        verify(store, never()).upsert(anyLong(), anyInt(), any());
    }

    @Test
    void duplicatePicksAreStoredOnce() {
        givenArticle();
        when(queries.matchedTopicIds(ID)).thenReturn(List.of(10L, 11L));

        service.vote(ID, -1, List.of(10L, 10L, 11L));

        verify(store).upsert(ID, -1, List.of(10L, 11L));
    }

    @Test
    void unscoredArticleReportsNotScoredWithNoEffects() {
        givenArticle();
        when(queries.matchedTopicIds(ID)).thenReturn(List.of());
        when(queries.isScored(ID)).thenReturn(false);

        FeedbackResult result = service.vote(ID, 1, null);

        assertThat(result.scored()).isFalse();
        assertThat(result.effects()).isEmpty();
        verify(store).upsert(ID, 1, null);
    }

    @Test
    void scoredArticleWithNoMatchesIsStillStored() {
        givenArticle();
        when(queries.matchedTopicIds(ID)).thenReturn(List.of());
        when(queries.isScored(ID)).thenReturn(true);

        FeedbackResult result = service.vote(ID, 1, null);

        assertThat(result.scored()).isTrue();
        assertThat(result.effects()).isEmpty();
        verify(store).upsert(ID, 1, null);
    }

    @Test
    void effectsCarryBeforeAndAfterForEveryMatchedTopic() {
        givenArticle();
        List<Long> matched = List.of(10L, 11L);
        when(queries.matchedTopicIds(ID)).thenReturn(matched);
        when(queries.isScored(ID)).thenReturn(true);
        when(queries.topicWeights(matched)).thenReturn(
                Map.of(10L, new TopicWeight(10, "Rust", 20, 0, 0, 20),
                        11L, new TopicWeight(11, "Politics", -30, 22, 20, -10)),
                Map.of(10L, new TopicWeight(10, "Rust", 20, 1.8, 1.8, 21.8),
                        11L, new TopicWeight(11, "Politics", -30, 24, 20, -10)));

        FeedbackResult result = service.vote(ID, 1, null);

        assertThat(result.article()).isSameAs(article);
        assertThat(result.effects()).containsExactly(
                new TopicEffect(10, "Rust", 20, 21.8, 20, 1.8, LearnedLimit.NONE),
                new TopicEffect(11, "Politics", -10, -10, -30, 20, LearnedLimit.LEARNED_CAP));
    }

    @Test
    void learnedAtExactlyTheCapIsLearnedCap() {
        assertThat(LearnedLimit.of(weight(10, 20.0, 20.0), 20)).isEqualTo(LearnedLimit.LEARNED_CAP);
        assertThat(LearnedLimit.of(weight(10, -20.0, -20.0), 20)).isEqualTo(LearnedLimit.LEARNED_CAP);
        assertThat(LearnedLimit.of(weight(10, 19.999999, 19.999999), 20)).isEqualTo(LearnedLimit.NONE);
    }

    @Test
    void sumCrossingZeroIsSignClamp() {
        assertThat(LearnedLimit.of(weight(5, -10, -10), 20)).isEqualTo(LearnedLimit.SIGN_CLAMP);
        assertThat(LearnedLimit.of(weight(-5, 10, 10), 20)).isEqualTo(LearnedLimit.SIGN_CLAMP);
        // The cap outranks the clamp
        assertThat(LearnedLimit.of(weight(5, -24, -20), 20)).isEqualTo(LearnedLimit.LEARNED_CAP);
    }

    @Test
    void sumExactlyZeroIsNotSignClamp() {
        assertThat(LearnedLimit.of(weight(10, -10, -10), 20)).isEqualTo(LearnedLimit.NONE);
    }

    @Test
    void sumBeyondFiftyIsWeightRange() {
        assertThat(LearnedLimit.of(weight(45, 10, 10), 20)).isEqualTo(LearnedLimit.WEIGHT_RANGE);
        assertThat(LearnedLimit.of(weight(-45, -10, -10), 20)).isEqualTo(LearnedLimit.WEIGHT_RANGE);
    }

    @Test
    void sumExactlyFiftyIsNone() {
        assertThat(LearnedLimit.of(weight(40, 10, 10), 20)).isEqualTo(LearnedLimit.NONE);
    }

    /** A topic weight; the effective value is irrelevant to {@link LearnedLimit#of}. */
    private static TopicWeight weight(double base, double learnedRaw, double learned) {
        return new TopicWeight(1, "t", base, learnedRaw, learned, 0);
    }

    private void givenArticle() {
        when(articleService.findById(ID)).thenReturn(Optional.of(article));
        lenient().when(articleService.findByIdWithBreakdown(ID)).thenReturn(Optional.of(article));
    }
}
