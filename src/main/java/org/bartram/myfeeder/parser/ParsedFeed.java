package org.bartram.myfeeder.parser;

import lombok.Builder;
import lombok.Data;
import org.bartram.myfeeder.model.FeedType;

import java.util.List;

@Data
@Builder
public class ParsedFeed {
    private String title;
    private String description;
    private String siteUrl;
    private FeedType feedType;
    private List<ParsedArticle> articles;
}
