package org.bartram.myfeeder.parser;

import lombok.Builder;

import java.time.Instant;

@Builder
public record ParsedArticle(
        String guid,
        String title,
        String url,
        String author,
        String content,
        String summary,
        String imageUrl,
        Instant publishedAt) {}
