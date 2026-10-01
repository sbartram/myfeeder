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
                Map.of(10L, new TopicWeight(10, "Rust", 20, 0, 0, 20, 0, 0, 0, 20),
                        11L, new TopicWeight(11, "Politics", -30, 22, 20, -10, 20, 0, 0, -10)),
                Map.of(10L, new TopicWeight(10, "Rust", 20, 1.8, 1.8, 21.8, 1.8, 0, 0, 21.8),
                        11L, new TopicWeight(11, "Politics", -30, 24, 20, -10, 20, 0, 0, -10)));

        FeedbackResult result = service.vote(ID, 1, null);

        assertThat(result.article()).isSameAs(article);
        assertThat(result.effects()).containsExactly(
                new TopicEffect(10, "Rust", 20, 21.8, 20, 1.8, LearnedLimit.NONE, 1.8, 0, false),
                new TopicEffect(11, "Politics", -10, -10, -30, 20, LearnedLimit.LEARNED_CAP, 20, 0, false));
    }

    @Test
    void anUpVoteOnAnEngagedArticleMarksTheReplacedEngagement() {
        givenScoredArticleMatching(List.of(10L), Map.of(10L, ENGAGED_RUST), Map.of(10L, VOTED_RUST));

        FeedbackResult result = service.vote(ID, 1, null);

        assertThat(result.effects()).containsExactly(
                new TopicEffect(10, "Rust", 20.9, 21.8, 20, 1.8, LearnedLimit.NONE, 1.8, 0, true));
    }

    @Test
    void removingTheVoteMarksTheRestoredEngagement() {
        givenScoredArticleMatching(List.of(10L), Map.of(10L, VOTED_RUST), Map.of(10L, ENGAGED_RUST));

        FeedbackResult result = service.clear(ID);

        assertThat(result.effects()).containsExactly(
                new TopicEffect(10, "Rust", 21.8, 20.9, 20, 0.9, LearnedLimit.NONE, 0, 0.9, true));
    }

    @Test
    void narrowedVoteLeavesUnpickedTopicsUnmarked() {
        // A 👎 narrowed to Rust drops Go's engagement share too, but Go's thumbs part does not change
        givenScoredArticleMatching(List.of(10L, 12L),
                Map.of(10L, ENGAGED_RUST, 12L, new TopicWeight(12, "Go", 10, 0, 0.2, 10.2, 0, 0.2, 0.2, 10)),
                Map.of(10L, new TopicWeight(10, "Rust", 20, -1.8, -1.8, 18.2, -1.8, 0, 0, 18.2),
                        12L, new TopicWeight(12, "Go", 10, 0, 0, 10, 0, 0, 0, 10)));

        FeedbackResult result = service.vote(ID, -1, List.of(10L));

        assertThat(result.effects()).containsExactly(
                new TopicEffect(10, "Rust", 20.9, 18.2, 20, -1.8, LearnedLimit.NONE, -1.8, 0, true),
                new TopicEffect(12, "Go", 10.2, 10, 10, 0, LearnedLimit.NONE, 0, 0, false));
    }

    @Test
    void sixDecimalNoiseIsNotAReplacement() {
        // A float8 SUM may differ in its last digit between reads; that is not an engagement change
        givenScoredArticleMatching(List.of(10L), Map.of(10L, ENGAGED_RUST),
                Map.of(10L, new TopicWeight(10, "Rust", 20, 1.8, 2.7000004, 22.7000004, 1.8, 0.9, 0.9000004, 21.8)));

        FeedbackResult result = service.vote(ID, 1, null);

        assertThat(result.effects()).extracting(TopicEffect::engagementReplaced).containsExactly(false);
    }

    @Test
    void learnedAtExactlyTheCapIsLearnedCap() {
        assertThat(LearnedLimit.of(weight(10, 20.0, 20.0), 20, 8)).isEqualTo(LearnedLimit.LEARNED_CAP);
        assertThat(LearnedLimit.of(weight(10, -20.0, -20.0), 20, 8)).isEqualTo(LearnedLimit.LEARNED_CAP);
        assertThat(LearnedLimit.of(weight(10, 19.999999, 19.999999), 20, 8)).isEqualTo(LearnedLimit.NONE);
    }

    @Test
    void sumCrossingZeroIsSignClamp() {
        assertThat(LearnedLimit.of(weight(5, -10, -10), 20, 8)).isEqualTo(LearnedLimit.SIGN_CLAMP);
        assertThat(LearnedLimit.of(weight(-5, 10, 10), 20, 8)).isEqualTo(LearnedLimit.SIGN_CLAMP);
        // The cap outranks the clamp
        assertThat(LearnedLimit.of(weight(5, -24, -20), 20, 8)).isEqualTo(LearnedLimit.LEARNED_CAP);
    }

    @Test
    void sumExactlyZeroIsNotSignClamp() {
        assertThat(LearnedLimit.of(weight(10, -10, -10), 20, 8)).isEqualTo(LearnedLimit.NONE);
    }

    @Test
    void sumBeyondFiftyIsWeightRange() {
        assertThat(LearnedLimit.of(weight(45, 10, 10), 20, 8)).isEqualTo(LearnedLimit.WEIGHT_RANGE);
        assertThat(LearnedLimit.of(weight(-45, -10, -10), 20, 8)).isEqualTo(LearnedLimit.WEIGHT_RANGE);
    }

    @Test
    void sumExactlyFiftyIsNone() {
        assertThat(LearnedLimit.of(weight(40, 10, 10), 20, 8)).isEqualTo(LearnedLimit.NONE);
    }

    @Test
    void learnedCapOutranksEngagementCap() {
        assertThat(LearnedLimit.of(weight(10, 24, 20, 9, 8), 20, 8)).isEqualTo(LearnedLimit.LEARNED_CAP);
    }

    @Test
    void engagementAtExactlyItsCapIsEngagementCap() {
        assertThat(LearnedLimit.of(weight(20, 0, 0, 8.0, 8.0), 20, 8)).isEqualTo(LearnedLimit.ENGAGEMENT_CAP);
        assertThat(LearnedLimit.of(weight(20, 0, 0, 7.999999, 7.999999), 20, 8)).isEqualTo(LearnedLimit.NONE);
    }

    @Test
    void clampAndRangeOutrankEngagementCap() {
        // Sum 45 + 5 + 8 = 58: the range holds the weight at +50, not the engagement cap (D-10)
        assertThat(LearnedLimit.of(weight(45, 5, 5, 10, 8), 20, 8)).isEqualTo(LearnedLimit.WEIGHT_RANGE);
        // Sum 10 - 19 + 8 = -1: the clamp holds the weight at 0, not the engagement cap (D-10)
        assertThat(LearnedLimit.of(weight(10, -19, -19, 8, 8), 20, 8)).isEqualTo(LearnedLimit.SIGN_CLAMP);
    }

    @Test
    void engagementAtCapIgnoresPrecedence() {
        TopicWeight heldByRange = weight(45, 5, 5, 10, 8);
        assertThat(LearnedLimit.of(heldByRange, 20, 8)).isEqualTo(LearnedLimit.WEIGHT_RANGE);
        assertThat(LearnedLimit.engagementAtCap(heldByRange, 8)).isTrue();
        assertThat(LearnedLimit.engagementAtCap(weight(20, 0, 0, 7.999999, 7.999999), 8)).isFalse();
        // Cap 0 disables engagement
        assertThat(LearnedLimit.engagementAtCap(weight(20, 0, 0, 5, 0), 0)).isFalse();
    }

    @Test
    void capZeroNeverReportsEngagementCap() {
        assertThat(LearnedLimit.of(weight(20, 0, 0, 5, 0), 20, 0)).isEqualTo(LearnedLimit.NONE);
    }

    @Test
    void engagementIsPartOfTheSum() {
        // 10 - 12 + 3 = +1: engagement keeps the sum above zero
        assertThat(LearnedLimit.of(weight(10, -12, -12, 3, 3), 20, 8)).isEqualTo(LearnedLimit.NONE);
        // 45 + 3 + 3 = 51: engagement pushes the sum beyond 50
        assertThat(LearnedLimit.of(weight(45, 3, 3, 3, 3), 20, 8)).isEqualTo(LearnedLimit.WEIGHT_RANGE);
    }

    @Test
    void negativeBaseNeverReportsEngagementCap() {
        // SQL zeroes engagementRaw for a negative base
        assertThat(LearnedLimit.of(weight(-5, 0, 0, 0, 0), 20, 8)).isEqualTo(LearnedLimit.NONE);
    }

    /** A topic weight; the effective value is irrelevant to {@link LearnedLimit#of}. */
    private static TopicWeight weight(double base, double learnedRaw, double learned) {
        return weight(base, learnedRaw, learned, 0, 0);
    }

    /** A topic weight with engagement; {@code learned} is the combined thumbs + engagement value, as in SQL. */
    private static TopicWeight weight(double base, double learnedRaw, double thumbsLearned, double engagementRaw,
                                      double engagementLearned) {
        return new TopicWeight(1, "t", base, learnedRaw, thumbsLearned + engagementLearned, 0, thumbsLearned,
                engagementRaw, engagementLearned, 0);
    }

    /** Rust with a starred article's 0.9 engagement share and no vote. */
    private static final TopicWeight ENGAGED_RUST = new TopicWeight(10, "Rust", 20, 0, 0.9, 20.9, 0, 0.9, 0.9, 20);
    /** Rust after an up vote on that article replaced its engagement share with the vote's 1.8. */
    private static final TopicWeight VOTED_RUST = new TopicWeight(10, "Rust", 20, 1.8, 1.8, 21.8, 1.8, 0, 0, 21.8);

    /** A scored article matching {@code matched}, whose topic weights read {@code before} then {@code after}. */
    private void givenScoredArticleMatching(List<Long> matched, Map<Long, TopicWeight> before,
                                            Map<Long, TopicWeight> after) {
        givenArticle();
        when(queries.matchedTopicIds(ID)).thenReturn(matched);
        when(queries.isScored(ID)).thenReturn(true);
        when(queries.topicWeights(matched)).thenReturn(before, after);
    }

    private void givenArticle() {
        when(articleService.findById(ID)).thenReturn(Optional.of(article));
        lenient().when(articleService.findByIdWithBreakdown(ID)).thenReturn(Optional.of(article));
    }
}
