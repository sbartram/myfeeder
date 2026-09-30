package org.bartram.myfeeder.service;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.repository.ArticleFeedbackStore;
import org.bartram.myfeeder.repository.InterestScoreQueries;
import org.bartram.myfeeder.repository.InterestScoreQueries.TopicWeight;
import org.bartram.myfeeder.service.FeedbackResult.TopicEffect;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Thumbs votes (FDBK-01..06). A vote writes or deletes one {@code article_feedback} row; the learned
 * model in {@link InterestScoreQueries} derives every topic's adjustment from those rows, so the
 * response reports each matched topic's effective weight before and after the write, read in the same
 * transaction (D-07).
 *
 * <p>This service reads stored scores only and never judges an article, and it never writes a topic's
 * weight.
 */
@Service
@RequiredArgsConstructor
public class ArticleFeedbackService {

    private final ArticleService articleService;
    private final ArticleFeedbackStore store;
    private final InterestScoreQueries queries;
    private final MyfeederProperties properties;

    /**
     * Stores the article's vote, replacing any earlier one. Everything is validated before any write,
     * with fixed-text 400s that never echo the input (D-15): the vote must be 1 or -1; {@code topicIds}
     * null means not narrowed (and clears an earlier narrowing, D-17), while a non-null list must be
     * non-empty, hold at most {@link InterestService#MAX_TOPICS} entries and name only topics this
     * article matched. Duplicate picks are stored once.
     */
    @Transactional
    public FeedbackResult vote(long articleId, Integer vote, List<Long> topicIds) {
        if (vote == null || (vote != 1 && vote != -1)) {
            throw new IllegalArgumentException("vote must be 1 or -1");
        }
        if (topicIds != null && topicIds.isEmpty()) {
            throw new IllegalArgumentException("topicIds must not be empty");
        }
        if (topicIds != null && topicIds.size() > InterestService.MAX_TOPICS) {
            throw new IllegalArgumentException("topicIds has too many entries");
        }
        return applyAndReport(articleId, matched -> {
            List<Long> picks = topicIds == null ? null : picksOf(topicIds, matched);
            return () -> store.upsert(articleId, vote, picks);
        });
    }

    /** Removes the article's vote; with no stored vote nothing is written and before equals after. */
    @Transactional
    public FeedbackResult clear(long articleId) {
        return applyAndReport(articleId, matched -> () -> store.delete(articleId));
    }

    /**
     * Every topic's base, learned and effective weight with its limit, in ascending id order (FDBK-07),
     * read from the same learned model as the ranking and the badge. No transaction and no write.
     */
    public List<TopicLearned> learnedTopics() {
        int cap = properties.getInterest().getBlend().getLearnedCap();
        double engagementCap = properties.getInterest().getBlend().getEngagement().getCap();
        return queries.allTopicWeights().stream()
                .map(w -> new TopicLearned(w.topicId(), w.base(), w.learned(), w.effective(),
                        LearnedLimit.of(w, cap, engagementCap), 0, 0))
                .toList();
    }

    /** The picks de-duplicated in order; every one must be a topic the article matched. */
    private static List<Long> picksOf(List<Long> topicIds, List<Long> matched) {
        Set<Long> picks = new LinkedHashSet<>(topicIds);
        if (picks.contains(null) || !matched.containsAll(picks)) {
            throw new IllegalArgumentException("topicIds must be topics this article matched");
        }
        return List.copyOf(picks);
    }

    /**
     * Looks up the article (404), hands its matched topics to {@code writeFor}, which validates and
     * returns the write, then reads the matched topics' weights, runs the write, reads them again and
     * builds the response.
     */
    private FeedbackResult applyAndReport(long articleId, Function<List<Long>, Runnable> writeFor) {
        articleService.findById(articleId)
                .orElseThrow(() -> new NotFoundException("Article not found: " + articleId));
        List<Long> matched = queries.matchedTopicIds(articleId);
        Runnable write = writeFor.apply(matched);
        boolean scored = queries.isScored(articleId);
        Map<Long, TopicWeight> before = queries.topicWeights(matched);
        write.run();
        Map<Long, TopicWeight> after = queries.topicWeights(matched);
        int cap = properties.getInterest().getBlend().getLearnedCap();
        double engagementCap = properties.getInterest().getBlend().getEngagement().getCap();
        List<TopicEffect> effects = matched.stream()
                .filter(id -> before.containsKey(id) && after.containsKey(id))
                .map(id -> {
                    TopicWeight a = after.get(id);
                    return new TopicEffect(id, a.name(), before.get(id).effective(), a.effective(), a.base(),
                            a.learned(), LearnedLimit.of(a, cap, engagementCap));
                })
                .toList();
        return new FeedbackResult(articleService.findByIdWithBreakdown(articleId).orElseThrow(), scored, effects);
    }
}
