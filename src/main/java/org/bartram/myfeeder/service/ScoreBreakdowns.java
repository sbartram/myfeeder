package org.bartram.myfeeder.service;

import org.bartram.myfeeder.model.InterestBreakdown;
import org.bartram.myfeeder.model.InterestBreakdown.NonMatchingTopic;
import org.bartram.myfeeder.model.InterestBreakdown.Row;
import org.bartram.myfeeder.repository.InterestScoreQueries.BreakdownInputs;
import org.bartram.myfeeder.repository.InterestScoreQueries.TopicContribution;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Turns the blend CTE's stored inputs into the exact "Why N?" breakdown. Pure: no Spring, no I/O.
 *
 * <p>D-02 contract: {@code total} and {@code display} come from SQL ({@code ROUND(raw)} and the clamped
 * badge) and are never recomputed here. The rows are the profile row (when a profile question was
 * asked, shown even at 0) plus every matched topic (hinge above 0); their integer points are
 * apportioned by largest remainder so they sum exactly to {@code total}. Rows are ordered by absolute
 * points, then absolute exact contribution, profile before topics, topic name A-Z ignoring case, then
 * topic id. Topics with hinge 0 go to {@code nonMatching}, ordered by noul descending then name.
 */
public final class ScoreBreakdowns {

    /** Display order before apportionment: |exact| desc, profile first, name A-Z ignoring case, id. */
    private static final Comparator<Candidate> CANDIDATE_ORDER =
            Comparator.comparing((Candidate c) -> c.exact().abs(), Comparator.reverseOrder())
                    .thenComparing(c -> c.topic() == null ? 0 : 1)
                    .thenComparing(Candidate::name, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingLong(Candidate::topicId);

    private static final Comparator<TopicContribution> NON_MATCHING_ORDER =
            Comparator.comparingDouble(TopicContribution::noul).reversed()
                    .thenComparing(TopicContribution::name, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingLong(TopicContribution::topicId);

    private ScoreBreakdowns() {
    }

    /** One apportionment candidate: the profile row ({@code topic == null}) or a matched topic. */
    private record Candidate(TopicContribution topic, BigDecimal exact, int levelIndex) {
        String name() {
            return topic == null ? "" : topic.name();
        }

        long topicId() {
            return topic == null ? 0L : topic.topicId();
        }
    }

    public static InterestBreakdown build(BreakdownInputs in) {
        List<Candidate> candidates = new ArrayList<>();
        if (in.profileScore() != null) {
            candidates.add(new Candidate(null, in.profileExact(),
                    levelIndex(in.profileScore(), in.profileMaxLevel())));
        }
        List<TopicContribution> nonMatchingTopics = new ArrayList<>();
        for (TopicContribution t : in.topics()) {
            if (t.hinge() > 0) {
                candidates.add(new Candidate(t, t.exact(), 0));
            } else {
                nonMatchingTopics.add(t);
            }
        }
        candidates.sort(CANDIDATE_ORDER);

        long[] points = apportion(candidates.stream().map(Candidate::exact).toArray(BigDecimal[]::new), in.total());
        List<Row> rows = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            Candidate c = candidates.get(i);
            TopicContribution t = c.topic();
            rows.add(t == null
                    ? Row.profile(c.levelIndex(), c.exact(), points[i])
                    : Row.topic(t.topicId(), t.name(), t.noul(), t.hinge(), t.weight(), c.exact(), points[i],
                            t.baseWeight(), t.learnedWeight(), t.thumbsWeight(), t.engagementWeight()));
        }
        // Stable: rows with equal |points| keep the candidate order
        rows.sort(Comparator.comparingLong((Row r) -> Math.abs(r.points())).reversed());

        List<NonMatchingTopic> nonMatching = nonMatchingTopics.stream()
                .sorted(NON_MATCHING_ORDER)
                .map(t -> new NonMatchingTopic(t.topicId(), t.name(), t.noul()))
                .toList();
        return new InterestBreakdown(in.raw(), in.total(), in.display(), List.copyOf(rows), nonMatching);
    }

    /**
     * Integer points per entry that sum exactly to {@code target}. Floors each value (the remainder is
     * then in [0, 1) whatever the sign), and adds 1 to the {@code target - sum(floors)} entries with the
     * largest remainder, ties by lower index. Callers pass entries in display order, so ties follow it.
     */
    static long[] apportion(BigDecimal[] exact, long target) {
        int n = exact.length;
        long[] out = new long[n];
        BigDecimal[] remainder = new BigDecimal[n];
        long floorSum = 0;
        for (int i = 0; i < n; i++) {
            BigDecimal floor = exact[i].setScale(0, RoundingMode.FLOOR);
            out[i] = floor.longValueExact();
            remainder[i] = exact[i].subtract(floor);
            floorSum += out[i];
        }
        long k = Math.max(0, Math.min(n, target - floorSum));
        int[] order = IntStream.range(0, n).boxed()
                .sorted(Comparator.comparing((Integer i) -> remainder[i], Comparator.reverseOrder())
                        .thenComparingInt(i -> i))
                .mapToInt(Integer::intValue)
                .toArray();
        for (int j = 0; j < k; j++) {
            out[order[j]]++;
        }
        return out;
    }

    /**
     * The profile level on the {@link InterestQuestions#PROFILE_MAX_LEVEL} rubric: the stored score
     * scaled to the current legend, rounded and clamped, so the index stays in range if a stored
     * {@code profile_max_level} ever differs. 0 when the max level is missing or not positive.
     */
    static int levelIndex(double profileScore, Integer profileMaxLevel) {
        if (profileMaxLevel == null || profileMaxLevel <= 0) {
            return 0;
        }
        long scaled = Math.round(profileScore / profileMaxLevel * InterestQuestions.PROFILE_MAX_LEVEL);
        return (int) Math.max(0, Math.min(InterestQuestions.PROFILE_MAX_LEVEL, scaled));
    }
}
