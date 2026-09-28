package org.bartram.myfeeder.service;

import org.bartram.myfeeder.repository.InterestScoreQueries.TopicWeight;

/**
 * Why a topic's weight change is smaller than the nominal nudge (FDBK-03, D-07). Judged on the
 * 6-decimal values {@link org.bartram.myfeeder.repository.InterestScoreQueries#topicWeights} returns,
 * in this precedence:
 * <ol>
 *   <li>{@link #LEARNED_CAP}: the uncapped learned points reach the cap in either direction
 *       ({@code |learnedRaw| >= cap}; exactly the cap counts).</li>
 *   <li>{@link #SIGN_CLAMP}: {@code base + learned} crosses zero, so the effective weight is held at 0
 *       (a sum of exactly 0 is not a clamp).</li>
 *   <li>{@link #WEIGHT_RANGE}: {@code |base + learned|} is beyond {@link InterestService#MAX_WEIGHT}
 *       (exactly 50 is not).</li>
 *   <li>{@link #NONE}: otherwise.</li>
 * </ol>
 */
public enum LearnedLimit {
    NONE, LEARNED_CAP, SIGN_CLAMP, WEIGHT_RANGE;

    public static LearnedLimit of(TopicWeight w, double cap) {
        if (Math.abs(w.learnedRaw()) >= cap) {
            return LEARNED_CAP;
        }
        double sum = w.base() + w.learned();
        if ((w.base() > 0 && sum < 0) || (w.base() < 0 && sum > 0)) {
            return SIGN_CLAMP;
        }
        if (Math.abs(sum) > InterestService.MAX_WEIGHT) {
            return WEIGHT_RANGE;
        }
        return NONE;
    }
}
