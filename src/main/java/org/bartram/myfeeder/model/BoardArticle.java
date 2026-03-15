package org.bartram.myfeeder.model;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import java.time.Instant;

@Data
@Table("board_article")
public class BoardArticle {
    @Id
    private Long id;
    private Long boardId;
    private Long articleId;
    private Instant addedAt;
}
