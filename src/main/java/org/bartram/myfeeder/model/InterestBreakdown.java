package org.bartram.myfeeder.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;

/**
 * The exact "Why N?" breakdown of an article's interest score (PRIO-04, D-02). {@code display} equals
 * the badge; the rows' {@code points} sum exactly to {@code total}, which is SQL {@code ROUND(raw)}
 * and may lie above 100 or below 0 (the client prints the capped or floored line). {@code rows} are
 * ordered by absolute points; {@code nonMatching} lists judged topics that did not count (hinge 0).
 */
public record InterestBreakdown(BigDecimal raw, int total, int display, List<Row> rows,
                                List<NonMatchingTopic> nonMatching) {

    /**
     * One breakdown line, discriminated by {@code kind}: a PROFILE row carries {@code levelIndex};
     * a TOPIC row carries {@code topicId}, {@code name}, {@code noul}, {@code hinge} and the effective
     * {@code weight}. {@code exact} is the unrounded contribution (6 decimals); null fields are omitted.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Row(String kind, Long topicId, String name, Integer levelIndex, Double noul, Double hinge,
                      Double weight, BigDecimal exact, long points, Double baseWeight, Double learnedWeight) {

        public static final String KIND_PROFILE = "PROFILE";
        public static final String KIND_TOPIC = "TOPIC";

        public static Row profile(int levelIndex, BigDecimal exact, long points) {
            return new Row(KIND_PROFILE, null, null, levelIndex, null, null, null, exact, points, null, null);
        }

        public static Row topic(long topicId, String name, double noul, double hinge, double weight,
                                BigDecimal exact, long points, double baseWeight, double learnedWeight) {
            return new Row(KIND_TOPIC, topicId, name, null, noul, hinge, weight, exact, points, baseWeight,
                    learnedWeight);
        }
    }

    /** A topic judged for this article that did not match (noul at or below 0.5, hinge 0). */
    public record NonMatchingTopic(long topicId, String name, double noul) {}
}
