package org.bartram.myfeeder.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Data
@Table("folder")
public class Folder {
    @Id
    private Long id;
    private String name;
    private int displayOrder;
    private Instant createdAt;
}
