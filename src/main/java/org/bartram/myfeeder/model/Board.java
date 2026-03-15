package org.bartram.myfeeder.model;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.Instant;

@Data
@Table("board")
public class Board {
    @Id
    private Long id;
    private String name;
    private String description;
    private Instant createdAt;
}
