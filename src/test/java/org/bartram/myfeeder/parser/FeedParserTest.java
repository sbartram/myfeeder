package org.bartram.myfeeder.parser;

import org.bartram.myfeeder.model.FeedType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FeedParserTest {

    private final FeedParser parser = new FeedParser();

    @Test
    void shouldParseRssFeed() {
        String xml = loadResource("/feeds/sample-rss.xml");
        ParsedFeed result = parser.parse(xml);

        assertThat(result.getFeedType()).isEqualTo(FeedType.RSS);
        assertThat(result.getTitle()).isEqualTo("Example RSS Feed");
        assertThat(result.getSiteUrl()).isEqualTo("https://example.com");
        assertThat(result.getArticles()).hasSize(2);

        var first = result.getArticles().get(0);
        assertThat(first.getGuid()).isEqualTo("https://example.com/first");
        assertThat(first.getTitle()).isEqualTo("First Post");
        assertThat(first.getPublishedAt()).isNotNull();
    }

    @Test
    void shouldParseAtomFeed() {
        String xml = loadResource("/feeds/sample-atom.xml");
        ParsedFeed result = parser.parse(xml);

        assertThat(result.getFeedType()).isEqualTo(FeedType.ATOM);
        assertThat(result.getTitle()).isEqualTo("Example Atom Feed");
        assertThat(result.getArticles()).hasSize(1);

        var entry = result.getArticles().get(0);
        assertThat(entry.getGuid()).isEqualTo("urn:uuid:atom-entry-1");
        assertThat(entry.getContent()).isEqualTo("<p>Full content</p>");
        assertThat(entry.getAuthor()).isEqualTo("Atom Author");
    }

    @Test
    void shouldParseJsonFeed() {
        String json = loadResource("/feeds/sample-json-feed.json");
        ParsedFeed result = parser.parse(json);

        assertThat(result.getFeedType()).isEqualTo(FeedType.JSON_FEED);
        assertThat(result.getTitle()).isEqualTo("Example JSON Feed");
        assertThat(result.getArticles()).hasSize(1);

        var item = result.getArticles().get(0);
        assertThat(item.getGuid()).isEqualTo("json-item-1");
        assertThat(item.getContent()).isEqualTo("<p>JSON feed content</p>");
        assertThat(item.getAuthor()).isEqualTo("JSON Author");
    }

    @Test
    void shouldDetectFeedType() {
        assertThat(parser.detectFeedType(loadResource("/feeds/sample-rss.xml"))).isEqualTo(FeedType.RSS);
        assertThat(parser.detectFeedType(loadResource("/feeds/sample-atom.xml"))).isEqualTo(FeedType.ATOM);
        assertThat(parser.detectFeedType(loadResource("/feeds/sample-json-feed.json"))).isEqualTo(FeedType.JSON_FEED);
    }

    private String loadResource(String path) {
        try (var is = getClass().getResourceAsStream(path)) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
