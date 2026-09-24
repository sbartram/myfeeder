package org.bartram.myfeeder.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.integration.JevApiClient;
import org.bartram.myfeeder.integration.JevJudgment;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.model.InterestProfile;
import org.bartram.myfeeder.model.InterestTopic;
import org.bartram.myfeeder.repository.ArticleScoreStore;
import org.bartram.myfeeder.repository.ArticleScoreStore.Candidate;
import org.bartram.myfeeder.repository.ArticleScoreStore.ScoredRow;
import org.bartram.myfeeder.repository.ArticleScoreStore.TopicNoul;
import org.springaicommunity.typesafe.question.Question;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Scores one article with a single Jev call (SCOR-01), using the same state and question builders
 * as the topic preview, and stores the raw outputs write-once (SCOR-03).
 *
 * <p>No transaction: a DB connection must never be held across the HTTP call and its retries.
 * Only {@link ArticleScoreStore#writeScored} is transactional. The service never retries; only the
 * client's jev retry applies. It never checks the circuit breaker itself, so {@code judge()} can
 * probe a HALF_OPEN breaker (research Pitfall 5).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleScoringService {

    private final JevApiClient jevApiClient;
    private final InterestService interestService;
    private final ArticleScoreStore store;
    private final MyfeederProperties properties;

    public void score(long articleId) {
        // SCOR-06: the single predicates, never reimplemented
        if (!jevApiClient.isConfigured() || interestService.isColdStart()) {
            return;
        }
        Instant cutoff = properties.getInterest().eligibilityCutoff();
        Optional<Candidate> candidate = store.loadCandidate(articleId, cutoff);
        if (candidate.isEmpty()) {
            return; // read, aged out, or already scored: the dispatch recheck
        }
        Article article = candidate.get().article();
        if (!StringUtils.hasText(article.getGuid())) {
            store.writeSkipped(articleId, "no guid"); // SCOR-08
            return;
        }
        Map<String, Object> state = ArticleStateBuilder.build(candidate.get().feedTitle(), article);
        if (!ArticleStateBuilder.hasJudgeableText(state)) {
            store.writeSkipped(articleId, "no text");
            return;
        }

        // Snapshot BEFORE the call, so the stored versions are the ones that were sent
        InterestProfile profile = interestService.getProfile();
        List<InterestTopic> topics = interestService.listTopics();
        Map<String, Question> questions = InterestQuestions.forRubric(profile.getProfileText(), topics);
        if (questions.isEmpty()) {
            return; // cold-start race: never judge an empty map
        }

        JevJudgment judgment;
        try {
            judgment = jevApiClient.judge(state, questions);
        } catch (RuntimeException e) {
            if (ScoringFailure.isTransient(e)) {
                // No row, no attempt: the breaker pauses the sweep during an outage
                log.debug("Transient scoring failure for article {}: {}", articleId, e.getClass().getSimpleName());
            } else {
                String description = ScoringFailure.describe(e);
                store.writeFailed(articleId, description);
                log.info("Scoring article {} failed: {}", articleId, description);
            }
            return;
        }

        // Pitfall 11: an answer the schema would reject is a failed attempt, not a write that loops
        if (!isValid(judgment, questions)) {
            store.writeFailed(articleId, "invalid answer");
            log.info("Scoring article {} failed: invalid answer", articleId);
            return;
        }

        try {
            store.writeScored(articleId, toRow(judgment, questions, profile, topics));
        } catch (RuntimeException e) {
            // The call was billed; recording an attempt keeps the 3-attempt bound
            log.warn("Storing the score for article {} failed; recording a failed attempt", articleId);
            store.writeFailed(articleId, "write failed");
        }
    }

    /**
     * Every requested noul is present, finite and within [0, 1]; a requested profile score is
     * finite, within [0, maxLevel], with a finite confidence. Values are stored exactly as
     * returned, never clamped or rounded.
     */
    private static boolean isValid(JevJudgment judgment, Map<String, Question> questions) {
        for (String key : questions.keySet()) {
            if (InterestQuestions.PROFILE_KEY.equals(key)) {
                JevJudgment.JevScore score = judgment.scores().get(key);
                if (score == null) {
                    return false;
                }
                int maxLevel = profileMaxLevel(score);
                if (!Double.isFinite(score.value()) || score.value() < 0 || score.value() > maxLevel
                        || !Double.isFinite(score.confidence())) {
                    return false;
                }
            } else {
                Double noul = judgment.nouls().get(key);
                if (noul == null || !Double.isFinite(noul) || noul < 0.0 || noul > 1.0) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The response's legend max, or the rubric's when the response has no legend (-1). */
    private static int profileMaxLevel(JevJudgment.JevScore score) {
        return score.maxLevel() >= 0 ? score.maxLevel() : InterestQuestions.PROFILE_MAX_LEVEL;
    }

    /** Profile columns stay NULL when no profile question was sent (V6), whatever the response carries. */
    private static ScoredRow toRow(JevJudgment judgment, Map<String, Question> questions, InterestProfile profile,
                                   List<InterestTopic> topics) {
        Double profileScore = null;
        Integer profileMaxLevel = null;
        Double profileConfidence = null;
        Integer profileVersion = null;
        if (questions.containsKey(InterestQuestions.PROFILE_KEY)) {
            JevJudgment.JevScore score = judgment.scores().get(InterestQuestions.PROFILE_KEY);
            profileScore = score.value();
            profileMaxLevel = profileMaxLevel(score);
            profileConfidence = score.confidence();
            profileVersion = profile.getVersion();
        }
        List<TopicNoul> nouls = new ArrayList<>(topics.size());
        for (InterestTopic topic : topics) {
            nouls.add(new TopicNoul(topic.getId(),
                    judgment.nouls().get(InterestQuestions.topicKey(topic.getId())), topic.getVersion()));
        }
        return new ScoredRow(profileScore, profileMaxLevel, profileConfidence, profileVersion,
                judgment.model(), judgment.requestId(), nouls);
    }
}
