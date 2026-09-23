package org.bartram.myfeeder.service;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.integration.JevApiClient;
import org.bartram.myfeeder.integration.JevJudgment;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.repository.ArticleRepository;
import org.bartram.myfeeder.repository.FeedRepository;
import org.springaicommunity.typesafe.question.Noul;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Judges one topic description against one article with a single Jev call (D-12), using the same
 * state and question builders as the Phase 4 scorer. Nothing is persisted: the only collaborators
 * are two read-only lookups and the Jev client.
 *
 * <p>No transaction: a DB connection must never be held across the HTTP call and its retries.
 * All validation happens before {@code judge()}, so caller bugs never reach the circuit breaker
 * (Pitfall 11 / Phase 2 WR-01). The service never retries (D-14); only the client's jev retry
 * applies.
 */
@Service
@RequiredArgsConstructor
public class InterestPreviewService {

    private final ArticleRepository articleRepository;
    private final FeedRepository feedRepository;
    private final JevApiClient jevApiClient;

    /**
     * @throws IllegalArgumentException when articleId is missing, the description is blank or
     *                                  longer than {@link InterestService#MAX_DESCRIPTION_CHARS},
     *                                  or the article has no text to judge (400)
     * @throws NotFoundException        when the article does not exist (404)
     * @throws org.bartram.myfeeder.integration.JevNotConfiguredException when no TypeSafe key is set (503)
     * @throws io.github.resilience4j.circuitbreaker.CallNotPermittedException when the jev breaker is open (503)
     * @throws org.springaicommunity.typesafe.exception.TypeSafeException for any other Jev failure (422/503)
     */
    public TopicPreviewResponse preview(Long articleId, String description, Long topicId) {
        if (articleId == null) {
            throw new IllegalArgumentException("articleId is required");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Write a description first");
        }
        String trimmed = description.trim();
        if (trimmed.length() > InterestService.MAX_DESCRIPTION_CHARS) {
            throw new IllegalArgumentException("description can be at most 500 characters");
        }
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new NotFoundException("Article not found: " + articleId));
        String feedTitle = feedRepository.findById(article.getFeedId()).map(Feed::getTitle).orElse(null);

        Map<String, Object> state = ArticleStateBuilder.build(feedTitle, article);
        if (!ArticleStateBuilder.hasJudgeableText(state)) {
            throw new IllegalArgumentException("This article has no text to judge");
        }
        String key = topicId == null ? InterestQuestions.PREVIEW_DRAFT_KEY : InterestQuestions.topicKey(topicId);
        Noul question = InterestQuestions.topic(trimmed);

        JevJudgment judgment = jevApiClient.judge(state, Map.of(key, question));
        return new TopicPreviewResponse(judgment.nouls().get(key), judgment.model());
    }
}
