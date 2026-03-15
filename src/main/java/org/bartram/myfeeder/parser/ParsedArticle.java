package org.bartram.myfeeder.parser;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ParsedArticle {
    private String guid;
    private String title;
    private String url;
    private String author;
    private String content;
    private String summary;
    private Instant publishedAt;
}
