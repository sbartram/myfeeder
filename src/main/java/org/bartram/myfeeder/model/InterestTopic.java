package org.bartram.myfeeder.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * An interest topic. {@code name} is a short display label (Phase 5/6 chips) that is never
 * sent to Jev; {@code description} is what the Noul question judges; {@code weight} is in
 * points, -50..+50 (R1). {@code version} is a plain column, not an optimistic-locking field:
 * the service bumps it only when the description changes.
 */
@Data
@Table("interest_topic")
public class InterestTopic {
    @Id
    private Long id;
    private String name;
    private String description;
    private int weight;
    private int version;
    private Instant createdAt;
    private Instant updatedAt;
}
