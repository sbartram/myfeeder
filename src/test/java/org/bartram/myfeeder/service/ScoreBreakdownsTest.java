package org.bartram.myfeeder.service;

import org.bartram.myfeeder.model.InterestBreakdown;
import org.bartram.myfeeder.model.InterestBreakdown.NonMatchingTopic;
import org.bartram.myfeeder.model.InterestBreakdown.Row;
import org.bartram.myfeeder.repository.InterestScoreQueries.BreakdownInputs;
import org.bartram.myfeeder.repository.InterestScoreQueries.TopicContribution;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ScoreBreakdownsTest {

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static TopicContribution topic(long id, String name, double noul, double hinge, double weight,
                                           String exact) {
        return topic(id, name, noul, hinge, weight, exact, weight, 0);
    }

    private static TopicContribution topic(long id, String name, double noul, double hinge, double weight,
                                           String exact, double baseWeight, double learnedWeight) {
        return new TopicContribution(id, name, noul, hinge, weight, bd(exact), baseWeight, learnedWeight,
                learnedWeight, 0);
    }

    private static BreakdownInputs inputs(String raw, int total, int display, Double profileScore,
                                          Integer maxLevel, String profileExact, TopicContribution... topics) {
        return new BreakdownInputs(bd(raw), total, display, profileScore, maxLevel,
                profileExact == null ? null : bd(profileExact), List.of(topics));
    }

    private static long sum(List<Row> rows) {
        return rows.stream().mapToLong(Row::points).sum();
    }

    private static List<String> labels(List<Row> rows) {
        return rows.stream()
                .map(r -> (Row.KIND_PROFILE.equals(r.kind()) ? "PROFILE" : r.name()) + " " + r.points())
                .toList();
    }

    @Test
    void uiMockRowsAddUpToTheBadge() {
        InterestBreakdown b = ScoreBreakdowns.build(inputs("82.200000", 82, 82, 2.56, 4, "64.000000",
                topic(1, "Rust", 0.93, 0.86, 20, "17.200000"),
                topic(2, "WebAssembly", 0.75, 0.5, 14, "7.000000"),
                topic(3, "Politics", 0.6, 0.2, -30, "-6.000000"),
                topic(5, "Gardening", 0.2, 0, 10, "0.000000")));

        assertThat(labels(b.rows())).containsExactly("PROFILE 64", "Rust 17", "WebAssembly 7", "Politics -6");
        assertThat(sum(b.rows())).isEqualTo(82);
        assertThat(b.total()).isEqualTo(82);
        assertThat(b.display()).isEqualTo(82);
        assertThat(b.raw()).isEqualByComparingTo("82.2");

        Row profile = b.rows().get(0);
        assertThat(profile.kind()).isEqualTo(Row.KIND_PROFILE);
        assertThat(profile.levelIndex()).isEqualTo(3);
        assertThat(profile.topicId()).isNull();
        assertThat(profile.exact()).isEqualByComparingTo("64");

        Row rust = b.rows().get(1);
        assertThat(rust.kind()).isEqualTo(Row.KIND_TOPIC);
        assertThat(rust.topicId()).isEqualTo(1L);
        assertThat(rust.noul()).isEqualTo(0.93);
        assertThat(rust.hinge()).isEqualTo(0.86);
        assertThat(rust.weight()).isEqualTo(20.0);
        assertThat(rust.exact()).isEqualByComparingTo("17.2");
        assertThat(rust.levelIndex()).isNull();

        assertThat(b.nonMatching()).containsExactly(new NonMatchingTopic(5, "Gardening", 0.2));
    }

    @Test
    void topicRowsCarryBaseAndLearnedWeight() {
        InterestBreakdown b = ScoreBreakdowns.build(inputs("82.679200", 83, 83, 2.56, 4, "64.000000",
                topic(1, "Rust", 0.93, 0.86, 21.72, "18.679200", 20, 1.72)));

        Row profile = b.rows().get(0);
        assertThat(profile.kind()).isEqualTo(Row.KIND_PROFILE);
        assertThat(profile.baseWeight()).isNull();
        assertThat(profile.learnedWeight()).isNull();

        Row rust = b.rows().get(1);
        assertThat(rust.kind()).isEqualTo(Row.KIND_TOPIC);
        assertThat(rust.weight()).isEqualTo(21.72);
        assertThat(rust.baseWeight()).isEqualTo(20.0);
        assertThat(rust.learnedWeight()).isEqualTo(1.72);
    }

    @Test
    void rowsStillSumToTotalWithLearnedWeights() {
        InterestBreakdown b = ScoreBreakdowns.build(inputs("84.259200", 84, 84, 2.56, 4, "64.000000",
                topic(1, "Rust", 0.93, 0.86, 21.72, "18.679200", 20, 1.72),
                topic(2, "WebAssembly", 0.75, 0.5, 15.0, "7.500000", 14, 1.0),
                topic(3, "Politics", 0.6, 0.2, -29.6, "-5.920000", -30, 0.4),
                topic(5, "Gardening", 0.2, 0, 10, "0.000000", 10, 0)));

        assertThat(sum(b.rows())).isEqualTo(84);
        assertThat(b.total()).isEqualTo(84);
        b.rows().stream().filter(r -> Row.KIND_TOPIC.equals(r.kind())).forEach(r ->
                assertThat(r.baseWeight() + r.learnedWeight()).as("base + learned of %s", r.name())
                        .isCloseTo(r.weight(), within(1e-6)));
    }

    @Test
    void cappedTotalGoesToTheLargestRemainder() {
        InterestBreakdown b = ScoreBreakdowns.build(inputs("133.320000", 133, 100, 4.0, 4, "100.000000",
                topic(1, "Rust", 0.99, 0.98, 20, "19.600000"),
                topic(2, "WebAssembly", 0.99, 0.98, 14, "13.720000")));

        assertThat(labels(b.rows())).containsExactly("PROFILE 100", "Rust 19", "WebAssembly 14");
        assertThat(sum(b.rows())).isEqualTo(133);
        assertThat(b.total()).isEqualTo(133);
        assertThat(b.display()).isEqualTo(100);
    }

    @Test
    void halfPointTieFollowsTheSqlTotal() {
        InterestBreakdown b = ScoreBreakdowns.build(inputs("28.500000", 29, 29, 1.0, 4, "25.000000",
                topic(2, "WebAssembly", 0.625, 0.25, 14, "3.500000")));

        assertThat(labels(b.rows())).containsExactly("PROFILE 25", "WebAssembly 4");
        assertThat(sum(b.rows())).isEqualTo(29);
    }

    @Test
    void negativeRemaindersApportionExactly() {
        InterestBreakdown b = ScoreBreakdowns.build(inputs("-2.100000", -2, 0, 0.0, 4, "0.000000",
                topic(1, "Rust", 0.5825, 0.165, 20, "3.300000"),
                topic(3, "Politics", 0.59, 0.18, -30, "-5.400000")));

        assertThat(labels(b.rows())).containsExactly("Politics -5", "Rust 3", "PROFILE 0");
        assertThat(sum(b.rows())).isEqualTo(-2);
        assertThat(b.display()).isEqualTo(0);
    }

    @Test
    void negativeHalfTie() {
        InterestBreakdown b = ScoreBreakdowns.build(inputs("-2.500000", -3, 0, null, null, null,
                topic(3, "Politics", 0.5416666, 0.0833333, -30, "-2.500000")));

        assertThat(labels(b.rows())).containsExactly("Politics -3");
        assertThat(sum(b.rows())).isEqualTo(-3);
    }

    @Test
    void nothingContributed() {
        InterestBreakdown b = ScoreBreakdowns.build(inputs("0.000000", 0, 0, null, null, null,
                topic(1, "Rust", 0.3, 0, 20, "0.000000"),
                topic(5, "Gardening", 0.1, 0, 10, "0.000000")));

        assertThat(b.rows()).isEmpty();
        assertThat(b.total()).isZero();
        assertThat(b.nonMatching()).extracting(NonMatchingTopic::name).containsExactly("Rust", "Gardening");
    }

    @Test
    void noulAtExactlyHalfIsNonMatching() {
        InterestBreakdown b = ScoreBreakdowns.build(inputs("0.200000", 0, 0, null, null, null,
                topic(1, "Rust", 0.5, 0, 20, "0.000000"),
                topic(2, "WebAssembly", 0.51, 0.02, 10, "0.200000")));

        assertThat(b.nonMatching()).extracting(NonMatchingTopic::name).containsExactly("Rust");
        assertThat(b.rows()).extracting(Row::name).containsExactly("WebAssembly");
        assertThat(sum(b.rows())).isZero();
    }

    @Test
    void weightZeroMatchIsARowWithZeroPoints() {
        InterestBreakdown b = ScoreBreakdowns.build(inputs("0.000000", 0, 0, null, null, null,
                topic(4, "Zero", 0.9, 0.8, 0, "0.000000")));

        assertThat(b.rows()).hasSize(1);
        Row zero = b.rows().get(0);
        assertThat(zero.kind()).isEqualTo(Row.KIND_TOPIC);
        assertThat(zero.name()).isEqualTo("Zero");
        assertThat(zero.weight()).isEqualTo(0.0);
        assertThat(zero.points()).isZero();
        assertThat(b.nonMatching()).isEmpty();
    }

    @Test
    void profileRowShownAtZero() {
        InterestBreakdown b = ScoreBreakdowns.build(inputs("0.000000", 0, 0, 0.0, 4, "0.000000"));

        assertThat(b.rows()).hasSize(1);
        Row profile = b.rows().get(0);
        assertThat(profile.kind()).isEqualTo(Row.KIND_PROFILE);
        assertThat(profile.points()).isZero();
        assertThat(profile.levelIndex()).isZero();
    }

    @Test
    void levelIndexScalesToTheRubric() {
        assertThat(ScoreBreakdowns.levelIndex(2.56, 4)).isEqualTo(3);
        assertThat(ScoreBreakdowns.levelIndex(0.0, 4)).isEqualTo(0);
        assertThat(ScoreBreakdowns.levelIndex(4.0, 4)).isEqualTo(4);
        assertThat(ScoreBreakdowns.levelIndex(1.5, 3)).isEqualTo(2);
        assertThat(ScoreBreakdowns.levelIndex(2.49, 4)).isEqualTo(2);
        assertThat(ScoreBreakdowns.levelIndex(2.0, null)).isEqualTo(0);
    }

    @Test
    void rowOrderBreaksTiesByName() {
        InterestBreakdown b = ScoreBreakdowns.build(inputs("10.000000", 10, 10, null, null, null,
                topic(7, "beta", 0.75, 0.5, 10, "5.000000"),
                topic(8, "Alpha", 0.75, 0.5, 10, "5.000000")));

        assertThat(b.rows()).extracting(Row::name).containsExactly("Alpha", "beta");
    }

    @Test
    void nonMatchingOrderedByNoulThenName() {
        InterestBreakdown b = ScoreBreakdowns.build(inputs("0.000000", 0, 0, null, null, null,
                topic(1, "delta", 0.4, 0, 10, "0.000000"),
                topic(2, "Gamma", 0.45, 0, 10, "0.000000"),
                topic(3, "Bravo", 0.4, 0, 10, "0.000000")));

        assertThat(b.nonMatching()).extracting(NonMatchingTopic::name).containsExactly("Gamma", "Bravo", "delta");
    }

    @Test
    void rowsSumToTotalAcrossCases() {
        record Case(String[] exact, long total) {}
        List<Case> cases = List.of(
                new Case(new String[]{"64", "17.2", "7", "-6"}, 82),
                new Case(new String[]{"100", "19.6", "13.72"}, 133),
                new Case(new String[]{"25", "3.5"}, 29),
                new Case(new String[]{"0.5", "0.5", "0.5"}, 2),
                new Case(new String[]{"-2.5"}, -3),
                new Case(new String[]{"-1.25", "-1.25"}, -3),
                new Case(new String[]{"-5.4", "3.3", "0"}, -2),
                new Case(new String[]{"0.333333", "0.333333", "0.333334"}, 1),
                new Case(new String[]{"-0.3", "-0.3", "-0.3"}, -1),
                new Case(new String[]{"12.345678", "-0.999999", "7.654321"}, 19));

        for (Case c : cases) {
            BigDecimal[] exact = Arrays.stream(c.exact()).map(BigDecimal::new).toArray(BigDecimal[]::new);
            long[] points = ScoreBreakdowns.apportion(exact, c.total());
            assertThat(Arrays.stream(points).sum()).as("sum for %s", Arrays.toString(c.exact())).isEqualTo(c.total());
            for (int i = 0; i < exact.length; i++) {
                // Every row stays within one point of its exact contribution
                assertThat(BigDecimal.valueOf(points[i]).subtract(exact[i]).abs())
                        .as("row %d of %s", i, Arrays.toString(c.exact()))
                        .isLessThan(BigDecimal.ONE);
            }
        }
    }

    @Test
    void apportionBumpsLargestRemainders() {
        long[] points = ScoreBreakdowns.apportion(new BigDecimal[]{bd("19.6"), bd("13.72"), bd("100")}, 133);

        assertThat(points).containsExactly(19, 14, 100);
    }
}
