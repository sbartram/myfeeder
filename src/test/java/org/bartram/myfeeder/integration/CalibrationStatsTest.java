package org.bartram.myfeeder.integration;

import org.bartram.myfeeder.integration.CalibrationStats.Row;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CalibrationStatsTest {

    @Test
    void pointsFollowTheScoringModel() {
        Map<String, Integer> weights = Map.of("topic_1", 20);

        assertThat(CalibrationStats.points(3, 4, Map.of("topic_1", 0.82), weights)).isCloseTo(87.8, within(1e-9));
        assertThat(CalibrationStats.points(3, 4, Map.of("topic_1", 0.4), weights)).isCloseTo(75.0, within(1e-9));
        // No profile question: only the topic part counts
        assertThat(CalibrationStats.points(Double.NaN, -1, Map.of("topic_1", 0.82), weights))
                .isCloseTo(12.8, within(1e-9));
    }

    @Test
    void stddevAndMedianOfKnownValues() {
        List<Double> values = List.of(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0);

        assertThat(CalibrationStats.stddev(values)).isCloseTo(2.0, within(1e-9));
        assertThat(CalibrationStats.median(values)).isCloseTo(4.5, within(1e-9));
        assertThat(CalibrationStats.median(List.of(0.9, 0.1, 0.5))).isCloseTo(0.5, within(1e-9));
        assertThat(CalibrationStats.stddev(List.of())).isNaN();
    }

    @Test
    void midBandShareUsesInclusiveBounds() {
        assertThat(CalibrationStats.midBandShare(List.of(0.35, 0.65, 0.34, 0.66))).isCloseTo(0.5, within(1e-9));
        assertThat(CalibrationStats.midBandShare(List.of(0.5))).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void labelAgreementCountsPairsAndTies() {
        Row lowRow = row("low", 10);

        assertThat(CalibrationStats.labelAgreement(List.of(row("high", 50), row("high", 40), lowRow)))
                .isCloseTo(1.0, within(1e-9));
        assertThat(CalibrationStats.labelAgreement(List.of(row("high", 50), row("high", 10), lowRow)))
                .isCloseTo(0.75, within(1e-9));
        assertThat(CalibrationStats.labelAgreement(List.of(row(null, 50), row(null, 10)))).isNaN();
    }

    @Test
    void hingeClampsAtHalf() {
        assertThat(CalibrationStats.hinge(0.5)).isZero();
        assertThat(CalibrationStats.hinge(0.2)).isZero();
        assertThat(CalibrationStats.hinge(1.0)).isCloseTo(1.0, within(1e-9));
        assertThat(CalibrationStats.hinge(0.75)).isCloseTo(0.5, within(1e-9));
    }

    private static Row row(String label, double points) {
        return new Row("t", label, 0.5, 0.5, Map.of(), points);
    }
}
