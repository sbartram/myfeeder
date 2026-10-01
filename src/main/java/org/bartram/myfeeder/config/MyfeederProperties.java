package org.bartram.myfeeder.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.time.Duration;
import java.time.Instant;

@Data
@ConfigurationProperties(prefix = "myfeeder")
public class MyfeederProperties implements Validator {

    /** Fixed startup-refusal text for the engagement constants (D-02, D-03); it never echoes a bound value. */
    static final String ENGAGEMENT_INVALID = "myfeeder.interest.blend.engagement must be cap 0 (disabled), "
            + "or 0 <= open-weight < save-weight < 1 and 0 < cap < learned-cap";

    /** Fixed startup-refusal text for the gap discovery near-miss (D-09); it never echoes a bound value. */
    static final String SUGGESTIONS_INVALID = "myfeeder.interest.suggestions.near-miss must be above 0 and at most 0.5";

    private Polling polling = new Polling();
    private Retention retention = new Retention();
    private Raindrop raindrop = new Raindrop();
    private Interest interest = new Interest();

    /** Boot's binder uses a bound {@link Validator} as its own validator, so every context that binds this checks it. */
    @Override
    public boolean supports(Class<?> type) {
        return MyfeederProperties.class.isAssignableFrom(type);
    }

    @Override
    public void validate(Object target, Errors errors) {
        MyfeederProperties p = (MyfeederProperties) target;
        Interest.Blend blend = p.getInterest().getBlend();
        if (!blend.getEngagement().isValid(blend.getLearnedCap())) {
            errors.reject("engagement", ENGAGEMENT_INVALID);
        }
        if (!p.getInterest().getSuggestions().isValid()) {
            errors.reject("suggestions", SUGGESTIONS_INVALID);
        }
    }

    @Data
    public static class Polling {
        private int defaultIntervalMinutes = 15;
        private int maxIntervalMinutes = 1440;
        private int backoffThreshold = 5;
    }

    @Data
    public static class Retention {
        private int fullContentDays = 30;
        private String cleanupCron = "0 0 3 * * *";
    }

    @Data
    public static class Raindrop {
        private String apiBaseUrl = "https://api.raindrop.io/rest/v1";
        private String apiToken = "";
    }

    @Data
    public static class Interest {
        private int windowDays = 14;
        private int concurrency = 1;
        private int queueCapacity = 1000;
        private int sweepBatchSize = 50;
        private Duration sweepDelay = Duration.ofMinutes(2);
        private Duration sweepInitialDelay = Duration.ofMinutes(1);
        private Blend blend = new Blend();
        /** Gap discovery (Suggested topics). A sibling of {@code blend}: the blend bind names stay pinned. */
        private Suggestions suggestions = new Suggestions();

        /** Articles published (or fetched, when undated) after this instant are inside the scoring window. */
        public Instant eligibilityCutoff() {
            return Instant.now().minus(Duration.ofDays(windowDays));
        }

        @Data
        public static class Blend {
            /** Points for a full profile match (R1): the profile contributes profile_score / profile_max_level x profilePoints. */
            private int profilePoints = 100;
            /** Points one vote moves a topic at a full match (R2): learned = learnRate x SUM(vote x hinge). Phase 7 tunes it. */
            private double learnRate = 2;
            /** Bound on a topic's learned adjustment in points, either direction (FDBK-03). */
            private int learnedCap = 20;
            /** Badge tier thresholds served on /api/interest/status (D-13). */
            private Tiers tiers = new Tiers();
            /** Engagement learning constants (LRN-05, D-01). Phase 12 calibrates them. */
            private Engagement engagement = new Engagement();

            @Data
            public static class Engagement {
                /** Strength of an OPEN_ORIGINAL engagement, as a fraction of one vote. */
                private double openWeight = 0.25;
                /** One strength for every save kind (STAR, BOARD and RAINDROP), as a fraction of one vote. */
                private double saveWeight = 0.5;
                /** Bound on the engagement points one topic can gain; 0 disables engagement learning. */
                private double cap = 8;

                /**
                 * D-02/D-03: cap 0 disables engagement learning whatever the weights are, so it is always
                 * valid; otherwise 0 <= open < save < 1 and 0 < cap < learnedCap. NaN and negative values fail.
                 */
                public boolean isValid(int learnedCap) {
                    return cap == 0 || (0 <= openWeight && openWeight < saveWeight && saveWeight < 1
                            && 0 < cap && cap < learnedCap);
                }
            }

            @Data
            public static class Tiers {
                /** Display score at or above which a badge is high (inclusive). Phase 7 tunes it. */
                private int high = 70;
                /** Display score at or above which a badge is neutral (inclusive); below it the badge is low. */
                private int neutral = 40;
            }
        }

        @Data
        public static class Suggestions {
            /**
             * D-09: an engaged article whose best noul (any topic, either weight sign) is at or above this
             * is left out of Suggested topics. Applied at query time. Phase 12 tunes it.
             */
            private double nearMiss = 0.35;

            /**
             * At most 0.5 keeps every matched article (noul above 0.5) excluded; above 0 keeps the list
             * possible. NaN fails both comparisons.
             */
            public boolean isValid() {
                return 0 < nearMiss && nearMiss <= 0.5;
            }
        }
    }
}
