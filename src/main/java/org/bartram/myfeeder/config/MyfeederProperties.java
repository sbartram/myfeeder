package org.bartram.myfeeder.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "myfeeder")
public class MyfeederProperties {

    private Polling polling = new Polling();
    private Retention retention = new Retention();
    private Raindrop raindrop = new Raindrop();

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
    }
}
