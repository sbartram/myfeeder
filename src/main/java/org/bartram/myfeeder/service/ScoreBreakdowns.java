package org.bartram.myfeeder.service;

import org.bartram.myfeeder.model.InterestBreakdown;
import org.bartram.myfeeder.repository.InterestScoreQueries;

import java.math.BigDecimal;
import java.util.List;

/** Placeholder for the RED run; implemented in the GREEN step. */
public final class ScoreBreakdowns {

    private ScoreBreakdowns() {
    }

    static int levelIndex(double profileScore, Integer profileMaxLevel) {
        return -1;
    }

    static long[] apportion(BigDecimal[] exact, long target) {
        return new long[exact.length];
    }

    public static InterestBreakdown build(InterestScoreQueries.BreakdownInputs in) {
        return new InterestBreakdown(in.raw(), in.total(), in.display(), List.of(), List.of());
    }
}
