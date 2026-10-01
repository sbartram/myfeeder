package org.bartram.myfeeder.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Startup validation of the engagement constants (LRN-05, D-02, D-03): cap 0 disables engagement learning
 * and always starts; otherwise {@code 0 <= open-weight < save-weight < 1} and {@code 0 < cap < learned-cap},
 * and anything else refuses to start with the fixed text {@link MyfeederProperties#ENGAGEMENT_INVALID}.
 * The gap discovery near-miss (D-09) must satisfy {@code 0 < near-miss <= 0.5}, or startup is refused with
 * the fixed text {@link MyfeederProperties#SUGGESTIONS_INVALID}.
 * No Docker: the context binds {@link MyfeederProperties} only.
 */
class MyfeederPropertiesValidationTest {

    private static final String CAP = "myfeeder.interest.blend.engagement.cap=";
    private static final String OPEN = "myfeeder.interest.blend.engagement.open-weight=";
    private static final String SAVE = "myfeeder.interest.blend.engagement.save-weight=";
    private static final String LEARNED_CAP = "myfeeder.interest.blend.learned-cap=";
    private static final String NEAR_MISS = "myfeeder.interest.suggestions.near-miss=";

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MyfeederProperties.class)
    static class PropsConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropsConfig.class);

    @Test
    void defaultsStartAndBindTheD01Values() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertEngagement(ctx.getBean(MyfeederProperties.class), 0.25, 0.5, 8.0);
        });
    }

    @Test
    void shippedMainYamlStarts() throws Exception {
        // Loaded from disk: src/test/resources/application.yaml shadows the main one on the classpath.
        PropertySource<?> mainYaml = new YamlPropertySourceLoader()
                .load("main-application-yaml", new FileSystemResource("src/main/resources/application.yaml"))
                .getFirst();

        runner.withInitializer(ctx -> ctx.getEnvironment().getPropertySources().addFirst(mainYaml))
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertEngagement(ctx.getBean(MyfeederProperties.class), 0.25, 0.5, 8.0);
                    assertThat(ctx.getBean(MyfeederProperties.class).getInterest().getSuggestions().getNearMiss())
                            .isEqualTo(0.35);
                });
    }

    @Test
    void defaultsBindTheNearMiss() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(MyfeederProperties.class).getInterest().getSuggestions().getNearMiss())
                    .isEqualTo(0.35);
        });
    }

    @Test
    void nearMissOutOfRangeIsRefused() {
        refusedNearMiss(NEAR_MISS + "0");
        refusedNearMiss(NEAR_MISS + "-0.1");
        refusedNearMiss(NEAR_MISS + "0.51");
        refusedNearMiss(NEAR_MISS + "NaN");
    }

    @Test
    void nearMissInRangeStarts() {
        starts(NEAR_MISS + "0.5");
        starts(NEAR_MISS + "0.01");
    }

    /** The near-miss refusal names the rule, never the bound value. */
    @Test
    void nearMissRefusalTextIsFixed() {
        runner.withPropertyValues(NEAR_MISS + "0.987654").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx).getFailure().hasStackTraceContaining(MyfeederProperties.SUGGESTIONS_INVALID);
            for (Throwable t = ctx.getStartupFailure(); t != null; t = t.getCause()) {
                assertThat(String.valueOf(t.getMessage())).doesNotContain("0.987654");
            }
        });
    }

    @Test
    void capZeroStartsWhateverTheWeights() {
        starts(CAP + "0", OPEN + "-5", SAVE + "-1");
        starts(CAP + "0", OPEN + "0", SAVE + "0");
        starts(CAP + "0", SAVE + "5");
    }

    @Test
    void capAtOrAboveTheThumbsCapIsRefused() {
        refused(CAP + "20");
        refused(CAP + "25");
        refused(LEARNED_CAP + "10", CAP + "10");
    }

    @Test
    void negativeCapIsRefused() {
        refused(CAP + "-1");
    }

    @Test
    void capBelowTheThumbsCapStarts() {
        starts(CAP + "19.5");
        starts(LEARNED_CAP + "30", CAP + "20");
    }

    @Test
    void saveMustStayBelowOne() {
        refused(SAVE + "1");
        starts(SAVE + "0.999");
    }

    @Test
    void saveMustExceedOpen() {
        refused(OPEN + "0.25", SAVE + "0.25");
        refused(OPEN + "0", SAVE + "0", CAP + "8");
        refused(OPEN + "0.3", SAVE + "0.25");
    }

    @Test
    void negativeOpenIsRefusedAndZeroOpenStarts() {
        refused(OPEN + "-0.1");
        starts(OPEN + "0", SAVE + "0.5", CAP + "8");
    }

    @Test
    void capBindsAsADouble() {
        runner.withPropertyValues(CAP + "7.5").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(MyfeederProperties.class).getInterest().getBlend().getEngagement().getCap())
                    .isEqualTo(7.5);
        });
    }

    @Test
    void nanIsRefusedUnlessCapIsZero() {
        refused(CAP + "NaN");
        refused(OPEN + "NaN", CAP + "8");
        starts(CAP + "0", OPEN + "NaN");
    }

    /** T-09-04: the refusal names the rule, never the bound value. */
    @Test
    void refusalTextIsFixed() {
        runner.withPropertyValues(CAP + "12345.5").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx).getFailure().hasStackTraceContaining(MyfeederProperties.ENGAGEMENT_INVALID);
            for (Throwable t = ctx.getStartupFailure(); t != null; t = t.getCause()) {
                assertThat(String.valueOf(t.getMessage())).doesNotContain("12345.5");
            }
        });
    }

    private void starts(String... props) {
        runner.withPropertyValues(props)
                .run(ctx -> assertThat(ctx).as(Arrays.toString(props)).hasNotFailed());
    }

    private void refused(String... props) {
        runner.withPropertyValues(props).run(ctx -> {
            assertThat(ctx).as(Arrays.toString(props)).hasFailed();
            assertThat(ctx).getFailure().as(Arrays.toString(props))
                    .hasStackTraceContaining(MyfeederProperties.ENGAGEMENT_INVALID);
        });
    }

    private void refusedNearMiss(String... props) {
        runner.withPropertyValues(props).run(ctx -> {
            assertThat(ctx).as(Arrays.toString(props)).hasFailed();
            assertThat(ctx).getFailure().as(Arrays.toString(props))
                    .hasStackTraceContaining(MyfeederProperties.SUGGESTIONS_INVALID);
        });
    }

    private static void assertEngagement(MyfeederProperties properties, double open, double save, double cap) {
        MyfeederProperties.Interest.Blend.Engagement engagement = properties.getInterest().getBlend().getEngagement();
        assertThat(engagement.getOpenWeight()).isEqualTo(open);
        assertThat(engagement.getSaveWeight()).isEqualTo(save);
        assertThat(engagement.getCap()).isEqualTo(cap);
    }
}
