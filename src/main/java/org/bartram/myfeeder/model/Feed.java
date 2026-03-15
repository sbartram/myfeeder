package org.bartram.myfeeder.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Data
@Table("feed")
public class Feed {
    @Id
    private Long id;
    private String url;
    private String title;
    private String description;
    private String siteUrl;
    private FeedType feedType;
    private int pollIntervalMinutes = 15;
    private Instant lastPolledAt;
    private Instant lastSuccessfulPollAt;
    private int errorCount;
    private String lastError;
    private String etag;
    private String lastModifiedHeader;
    private Instant createdAt;
}
