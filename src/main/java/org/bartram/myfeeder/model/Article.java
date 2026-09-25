package org.bartram.myfeeder.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Data
@Table("article")
public class Article {
    @Id
    private Long id;
    private Long feedId;
    private String guid;
    private String title;
    private String url;
    private String author;
    private String content;
    private String summary;
    private String imageUrl;
    private Instant publishedAt;
    private Instant fetchedAt;
    private boolean read;
    private boolean starred;
    // 0-100 display score from the blend CTE (InterestScoreQueries); null when the article is unscored
    @Transient
    private Integer interestScore;
}
