package org.bartram.myfeeder.service;

import org.bartram.myfeeder.repository.InterestScoreQueries.TopicWeight;

/**
 * Why a topic's weight change is smaller than the nominal nudge (FDBK-03, D-07). Judged on the
 * 6-decimal values {@link org.bartram.myfeeder.repository.InterestScoreQueries#topicWeights} returns,
 * in this precedence (Phase 10 D-10, which supersedes the Phase 9 D-11 order):
 * <ol>
 *   <li>{@link #LEARNED_CAP}: the uncapped thumbs points reach the learned cap in either direction
 *       ({@code |learnedRaw| >= learnedCap}; exactly the cap counts).</li>
 *   <li>{@link #SIGN_CLAMP}: {@code base + learned} crosses zero, so the effective weight is held at 0
 *       (a sum of exactly 0 is not a clamp); {@code learned} is thumbs + engagement (D-15).</li>
 *   <li>{@link #WEIGHT_RANGE}: {@code |base + learned|} is beyond {@link InterestService#MAX_WEIGHT}
 *       (exactly 50 is not).</li>
 *   <li>{@link #ENGAGEMENT_CAP}: reported only when no binding bound holds the weight and the uncapped
 *       engagement points reach the engagement cap ({@link #engagementAtCap}; exactly the cap counts). A
 *       cap of 0 disables engagement and this check. A negative-base topic never reaches it, because the
 *       SQL zeroes its {@code engagementRaw}.</li>
 *   <li>{@link #NONE}: otherwise.</li>
 * </ol>
 */
public enum LearnedLimit {
    NONE, LEARNED_CAP, SIGN_CLAMP, WEIGHT_RANGE, ENGAGEMENT_CAP;

    public static LearnedLimit of(TopicWeight w, double learnedCap, double engagementCap) {
        if (Math.abs(w.learnedRaw()) >= learnedCap) {
            return LEARNED_CAP;
        }
        double sum = w.base() + w.learned();
        if ((w.base() > 0 && sum < 0) || (w.base() < 0 && sum > 0)) {
            return SIGN_CLAMP;
        }
        if (Math.abs(sum) > InterestService.MAX_WEIGHT) {
            return WEIGHT_RANGE;
        }
        if (engagementAtCap(w, engagementCap)) {
            return ENGAGEMENT_CAP;
        }
        return NONE;
    }

    /**
     * Whether the topic's uncapped engagement points reach the engagement cap, whatever limit {@link #of}
     * reports. The single rule both {@link #of} and the learned-topics response use; a cap of 0 disables
     * engagement, so it is never at its cap.
     */
    public static boolean engagementAtCap(TopicWeight w, double engagementCap) {
        return engagementCap > 0 && w.engagementRaw() >= engagementCap;
    }
}
