package org.bartram.myfeeder.service;

import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.repository.InterestScoreQueries;
import org.bartram.myfeeder.repository.TopicSuggestionStore;
import org.bartram.myfeeder.repository.TopicSuggestionStore.Candidate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The D-05 order, the D-07 cap and total, and the Pitfall 8 badge drop, over a mocked store and blend. */
@ExtendWith(MockitoExtension.class)
class TopicSuggestionServiceTest {

    private static final Instant T = Instant.parse("2026-09-25T12:00:00Z");

    @Mock private TopicSuggestionStore store;
    @Mock private InterestScoreQueries scoreQueries;
    @Spy private MyfeederProperties properties = new MyfeederProperties();
    @InjectMocks private TopicSuggestionService service;

    @Test
    void ordersByBadgeAscendingThenLatestEngagement() {
        givenCandidates(candidate(1, T), candidate(2, T.plusSeconds(60)), candidate(3, T));
        givenBadges(Map.of(1L, 40, 2L, 10, 3L, 10));

        TopicSuggestions result = service.list();

        assertThat(ids(result)).containsExactly(2L, 3L, 1L);
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.items().get(0)).isEqualTo(new TopicSuggestion(2, "Article 2", "Feed", 10));
    }

    @Test
    void equalBadgeAndEngagementFallsBackToHigherIdFirst() {
        givenCandidates(candidate(5, T), candidate(9, T), candidate(7, T));
        givenBadges(Map.of(5L, 0, 9L, 0, 7L, 0));

        assertThat(ids(service.list())).containsExactly(9L, 7L, 5L);
    }

    @Test
    void capsAtTenAndCountsTheTotal() {
        List<Candidate> twelve = IntStream.rangeClosed(1, 12).mapToObj(i -> candidate(i, T.plusSeconds(i))).toList();
        when(store.candidates(any(), anyDouble())).thenReturn(twelve);
        Map<Long, Integer> badges = new HashMap<>();
        twelve.forEach(c -> badges.put(c.articleId(), 0));
        givenBadges(badges);

        TopicSuggestions result = service.list();

        assertThat(result.items()).hasSize(TopicSuggestionService.MAX_SUGGESTIONS);
        assertThat(ids(result)).containsExactly(12L, 11L, 10L, 9L, 8L, 7L, 6L, 5L, 4L, 3L);
        assertThat(result.total()).isEqualTo(12);
    }

    @Test
    void dropsACandidateWithNoBadge() {
        givenCandidates(candidate(1, T), candidate(2, T));
        givenBadges(Map.of(1L, 20));

        TopicSuggestions result = service.list();

        assertThat(ids(result)).containsExactly(1L);
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void noCandidatesGivesAnEmptyList() {
        givenCandidates();

        TopicSuggestions result = service.list();

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
    }

    @Test
    void asksTheStoreForThirtyDaysAndTheConfiguredNearMiss() {
        givenCandidates();
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);

        service.list();

        verify(store).candidates(cutoff.capture(), eq(0.35));
        Instant expected = Instant.now().minus(Duration.ofDays(30));
        assertThat(Duration.between(cutoff.getValue(), expected).abs()).isLessThan(Duration.ofSeconds(5));
        assertThat(TopicSuggestionService.WINDOW_DAYS).isEqualTo(30);

        properties.getInterest().getSuggestions().setNearMiss(0.2);
        service.list();

        verify(store).candidates(any(), eq(0.2));
        assertThat(properties.getInterest().getSuggestions().getNearMiss()).isCloseTo(0.2, within(0.0));
    }

    private void givenCandidates(Candidate... candidates) {
        when(store.candidates(any(), anyDouble())).thenReturn(List.of(candidates));
    }

    private void givenBadges(Map<Long, Integer> badges) {
        when(scoreQueries.displayScores(anyCollection())).thenReturn(badges);
    }

    private static Candidate candidate(long id, Instant engagedAt) {
        return new Candidate(id, "Article " + id, "Feed", engagedAt);
    }

    private static List<Long> ids(TopicSuggestions suggestions) {
        return suggestions.items().stream().map(TopicSuggestion::articleId).toList();
    }
}
