package org.bartram.myfeeder.service;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.repository.ArticleFeedbackStore;
import org.bartram.myfeeder.repository.InterestScoreQueries;
import org.bartram.myfeeder.repository.InterestScoreQueries.TopicWeight;
import org.bartram.myfeeder.service.FeedbackResult.TopicEffect;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

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

    /** Stores the article's vote, replacing any earlier one. */
    @Transactional
    public FeedbackResult vote(long articleId, Integer vote, List<Long> topicIds) {
        return applyAndReport(articleId, () -> store.upsert(articleId, vote, topicIds));
    }

    /** Removes the article's vote; with no stored vote nothing is written and before equals after. */
    @Transactional
    public FeedbackResult clear(long articleId) {
        return applyAndReport(articleId, () -> store.delete(articleId));
    }

    /** Reads the matched topics' weights, runs {@code write}, reads them again and builds the response. */
    private FeedbackResult applyAndReport(long articleId, Runnable write) {
        articleService.findById(articleId)
                .orElseThrow(() -> new NotFoundException("Article not found: " + articleId));
        List<Long> matched = queries.matchedTopicIds(articleId);
        boolean scored = queries.isScored(articleId);
        Map<Long, TopicWeight> before = queries.topicWeights(matched);
        write.run();
        Map<Long, TopicWeight> after = queries.topicWeights(matched);
        int cap = properties.getInterest().getBlend().getLearnedCap();
        List<TopicEffect> effects = matched.stream()
                .filter(id -> before.containsKey(id) && after.containsKey(id))
                .map(id -> {
                    TopicWeight a = after.get(id);
                    return new TopicEffect(id, a.name(), before.get(id).effective(), a.effective(), a.base(),
                            a.learned(), LearnedLimit.of(a, cap));
                })
                .toList();
        return new FeedbackResult(articleService.findByIdWithBreakdown(articleId).orElseThrow(), scored, effects);
    }
}
