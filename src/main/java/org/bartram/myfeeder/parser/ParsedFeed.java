package org.bartram.myfeeder.parser;

import lombok.Builder;
import org.bartram.myfeeder.model.FeedType;

import java.util.List;

@Builder
public record ParsedFeed(
        String title,
        String description,
        String siteUrl,
        FeedType feedType,
        List<ParsedArticle> articles) {}
