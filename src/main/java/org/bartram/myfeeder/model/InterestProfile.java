package org.bartram.myfeeder.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * The singleton interest profile (row id = 1, seeded by V6). {@code version} is a plain
 * column, not an optimistic-locking field: the service bumps it only when the text changes.
 */
@Data
@Table("interest_profile")
public class InterestProfile {
    @Id
    private Integer id;
    private String profileText;
    private int version;
    private Instant updatedAt;
}
