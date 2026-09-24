package org.bartram.myfeeder.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.integration.JevApiClient;
import org.bartram.myfeeder.integration.JevJudgment;
import org.bartram.myfeeder.model.InterestProfile;
import org.bartram.myfeeder.model.InterestTopic;
import org.bartram.myfeeder.repository.ArticleScoreStore;
import org.bartram.myfeeder.repository.ArticleScoreStore.Candidate;
import org.bartram.myfeeder.repository.ArticleScoreStore.ScoredRow;
import org.bartram.myfeeder.repository.ArticleScoreStore.TopicNoul;
import org.springaicommunity.typesafe.question.Question;
import org.springframework.stereotype.Service;

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
        Map<String, Object> state = ArticleStateBuilder.build(candidate.get().feedTitle(), candidate.get().article());

        // Snapshot BEFORE the call, so the stored versions are the ones that were sent
        InterestProfile profile = interestService.getProfile();
        List<InterestTopic> topics = interestService.listTopics();
        Map<String, Question> questions = InterestQuestions.forRubric(profile.getProfileText(), topics);
        if (questions.isEmpty()) {
            return; // cold-start race: never judge an empty map
        }

        JevJudgment judgment = jevApiClient.judge(state, questions);

        store.writeScored(articleId, toRow(judgment, profile, topics));
    }

    private static ScoredRow toRow(JevJudgment judgment, InterestProfile profile, List<InterestTopic> topics) {
        Double profileScore = null;
        Integer profileMaxLevel = null;
        Double profileConfidence = null;
        Integer profileVersion = null;
        JevJudgment.JevScore score = judgment.scores().get(InterestQuestions.PROFILE_KEY);
        if (score != null) {
            profileScore = score.value();
            profileMaxLevel = score.maxLevel() >= 0 ? score.maxLevel() : InterestQuestions.PROFILE_MAX_LEVEL;
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
