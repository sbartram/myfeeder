package org.bartram.myfeeder.service;

import org.bartram.myfeeder.repository.InterestScoreQueries.TopicWeight;

/** Why a topic's weight change is smaller than the nominal nudge (FDBK-03, D-07). */
public enum LearnedLimit {
    NONE, LEARNED_CAP, SIGN_CLAMP, WEIGHT_RANGE;

    public static LearnedLimit of(TopicWeight w, double cap) {
        return NONE;
    }
}
