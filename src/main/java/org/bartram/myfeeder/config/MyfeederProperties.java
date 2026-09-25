package org.bartram.myfeeder.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.Instant;

@Data
@ConfigurationProperties(prefix = "myfeeder")
public class MyfeederProperties {

    private Polling polling = new Polling();
    private Retention retention = new Retention();
    private Raindrop raindrop = new Raindrop();
    private Interest interest = new Interest();

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

        /** Articles published (or fetched, when undated) after this instant are inside the scoring window. */
        public Instant eligibilityCutoff() {
            return Instant.now().minus(Duration.ofDays(windowDays));
        }

        @Data
        public static class Blend {
            /** Points for a full profile match (R1): the profile contributes profile_score / profile_max_level x profilePoints. */
            private int profilePoints = 100;
        }
    }
}
