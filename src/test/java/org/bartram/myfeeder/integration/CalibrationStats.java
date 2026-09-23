package org.bartram.myfeeder.integration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Pure statistics and blend math for {@link InterestCalibrationSpikeTest}. No Spring, no I/O.
 *
 * <p>The thresholds are the research Assumption A6 starting heuristics. They inform the verdict in
 * 03-CALIBRATION.md; the user's judgment of the ranked output decides.
 */
final class CalibrationStats {

    static final double MIN_PROFILE_STDDEV = 0.15;
    static final double MIN_MEDIAN_CONFIDENCE = 0.5;
    static final double MAX_MID_BAND_SHARE = 0.5;
    static final double MIN_LABEL_AGREEMENT = 0.75;
    static final double MID_BAND_LOW = 0.35;
    static final double MID_BAND_HIGH = 0.65;

    /**
     * One judged article. {@code profileNormalized} and {@code profileConfidence} are NaN when the
     * rubric has no profile question; {@code nouls} is keyed {@code topic_<id>}.
     */
    record Row(String title, String label, double profileNormalized, double profileConfidence,
               Map<String, Double> nouls, double points) {}

    private CalibrationStats() {
    }

    /** Topic match: {@code max(0, (noul - 0.5) * 2)}. */
    static double hinge(double noul) {
        return Math.max(0, (noul - 0.5) * 2);
    }

    /**
     * Blended points per the REQUIREMENTS scoring model:
     * {@code 100 * profileScore / maxLevel + Σ hinge(noul) * weight}. The profile part is 0 when there
     * is no profile score (NaN score or a non-positive maxLevel); nouls without a weight add nothing.
     */
    static double points(double profileScore, int maxLevel, Map<String, Double> nouls,
                         Map<String, Integer> weightsByKey) {
        double points = Double.isNaN(profileScore) || maxLevel <= 0 ? 0 : 100 * profileScore / maxLevel;
        for (Map.Entry<String, Double> noul : nouls.entrySet()) {
            Integer weight = weightsByKey.get(noul.getKey());
            if (weight != null) {
                points += hinge(noul.getValue()) * weight;
            }
        }
        return points;
    }

    /** Population standard deviation; NaN for an empty list. */
    static double stddev(List<Double> values) {
        if (values.isEmpty()) {
            return Double.NaN;
        }
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double variance = values.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum() / values.size();
        return Math.sqrt(variance);
    }

    /** Median; NaN for an empty list. */
    static double median(List<Double> values) {
        if (values.isEmpty()) {
            return Double.NaN;
        }
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(null);
        int mid = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(mid) : (sorted.get(mid - 1) + sorted.get(mid)) / 2;
    }

    /** Share of values inside [{@link #MID_BAND_LOW}, {@link #MID_BAND_HIGH}], inclusive; NaN when empty. */
    static double midBandShare(Collection<Double> nouls) {
        if (nouls.isEmpty()) {
            return Double.NaN;
        }
        long inside = nouls.stream().filter(n -> n >= MID_BAND_LOW && n <= MID_BAND_HIGH).count();
        return (double) inside / nouls.size();
    }

    /**
     * Over every (high, low) pair of labelled rows, the share where the high row has more points.
     * Ties count 0.5. NaN when there is no pair.
     */
    static double labelAgreement(List<Row> rows) {
        List<Row> highs = rows.stream().filter(r -> "high".equals(r.label())).toList();
        List<Row> lows = rows.stream().filter(r -> "low".equals(r.label())).toList();
        if (highs.isEmpty() || lows.isEmpty()) {
            return Double.NaN;
        }
        double agree = 0;
        for (Row high : highs) {
            for (Row low : lows) {
                if (high.points() > low.points()) {
                    agree += 1;
                } else if (high.points() == low.points()) {
                    agree += 0.5;
                }
            }
        }
        return agree / (highs.size() * lows.size());
    }

    /** Largest absolute pairwise difference over the common prefix, ignoring NaN pairs; 0 when none. */
    static double maxDelta(List<Double> first, List<Double> second) {
        double max = 0;
        for (int i = 0; i < Math.min(first.size(), second.size()); i++) {
            double delta = Math.abs(first.get(i) - second.get(i));
            if (!Double.isNaN(delta)) {
                max = Math.max(max, delta);
            }
        }
        return max;
    }
}
