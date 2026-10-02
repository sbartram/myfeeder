package org.bartram.myfeeder.service;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.model.SuggestionDismissalReason;
import org.bartram.myfeeder.repository.ArticleRepository;
import org.bartram.myfeeder.repository.InterestScoreQueries;
import org.bartram.myfeeder.repository.TopicSuggestionStore;
import org.bartram.myfeeder.repository.TopicSuggestionStore.Candidate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Suggested topics (GAP-01, GAP-04, GAP-05). {@link TopicSuggestionStore} returns the D-08 candidates, and
 * {@link InterestScoreQueries#displayScores} supplies each one's badge, the number {@code InterestBadge}
 * shows, from the unchanged blend. Every suggestion's best noul is below 0.5, so its badge is the profile
 * part only. Reads stored scores only: nothing here calls Jev, and there is no transaction.
 */
@Service
@RequiredArgsConstructor
public class TopicSuggestionService {

    /** D-07: at most this many rows are listed. */
    public static final int MAX_SUGGESTIONS = 10;

    /** D-06: the latest engagement must be inside this many days. */
    public static final int WINDOW_DAYS = 30;

    private final TopicSuggestionStore store;
    private final InterestScoreQueries scoreQueries;
    private final MyfeederProperties properties;
    private final ArticleRepository articleRepository;

    /**
     * Badge ascending ("surprise" order), then latest engagement newest first, then id descending (D-05);
     * the first {@link #MAX_SUGGESTIONS} as items and every qualifying article as the total. A candidate
     * whose SCORED row vanished before the badge lookup has no badge and is dropped, not counted.
     */
    public TopicSuggestions list() {
        List<Candidate> candidates = store.candidates(Instant.now().minus(Duration.ofDays(WINDOW_DAYS)),
                properties.getInterest().getSuggestions().getNearMiss());
        Map<Long, Integer> badges = scoreQueries.displayScores(
                candidates.stream().map(Candidate::articleId).toList());
        List<TopicSuggestion> all = candidates.stream()
                .filter(c -> badges.get(c.articleId()) != null)
                .sorted(Comparator.<Candidate>comparingInt(c -> badges.get(c.articleId()))
                        .thenComparing(Candidate::engagedAt, Comparator.reverseOrder())
                        .thenComparing(Candidate::articleId, Comparator.reverseOrder()))
                .map(c -> new TopicSuggestion(c.articleId(), c.title(), c.feedTitle(), badges.get(c.articleId())))
                .toList();
        return new TopicSuggestions(all.stream().limit(MAX_SUGGESTIONS).toList(), all.size());
    }

    /**
     * Dismisses the article's suggestion (GAP-03, D-17): immediate and permanent, with no un-dismiss. A repeat
     * is a no-op that keeps the first reason. A missing article is a 404. Never calls Jev.
     */
    public void dismiss(Long articleId) {
        if (!articleRepository.existsById(articleId)) {
            throw new NotFoundException("Article not found: " + articleId);
        }
        store.handle(articleId, SuggestionDismissalReason.DISMISSED);
    }
}
