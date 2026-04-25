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
    void shouldExtractImageFromMediaRss() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
                  <channel>
                    <title>Media Feed</title>
                    <link>https://example.com</link>
                    <description>desc</description>
                    <item>
                      <title>Post With Media</title>
                      <link>https://example.com/post</link>
                      <guid>https://example.com/post</guid>
                      <description>Just text, no img</description>
                      <media:content url="https://cdn.example.com/lead.jpg" medium="image" />
                    </item>
                  </channel>
                </rss>
                """;
        ParsedFeed result = parser.parse(xml);
        assertThat(result.getArticles().get(0).getImageUrl())
                .isEqualTo("https://cdn.example.com/lead.jpg");
    }

    @Test
    void shouldExtractImageFromGizmodoStyleEnclosureWithoutLength() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/">
                  <channel>
                    <title>Gizmodo</title>
                    <link>https://gizmodo.com/</link>
                    <description>The Future Is Here</description>
                    <item>
                      <title>Some Post</title>
                      <link>https://gizmodo.com/post-1</link>
                      <guid isPermaLink="false">https://gizmodo.com/?p=1</guid>
                      <description><![CDATA[Some text.]]></description>
                      <content:encoded><![CDATA[Some text.]]></content:encoded>
                      <enclosure url="https://gizmodo.com/app/uploads/2026/04/post.jpg" type="image/jpeg" />
                    </item>
                  </channel>
                </rss>
                """;
        ParsedFeed result = parser.parse(xml);
        assertThat(result.getArticles().get(0).getImageUrl())
                .isEqualTo("https://gizmodo.com/app/uploads/2026/04/post.jpg");
    }

    @Test
    void shouldExtractImageFromEnclosure() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0">
                  <channel>
                    <title>Enclosure Feed</title>
                    <link>https://example.com</link>
                    <description>desc</description>
                    <item>
                      <title>Post</title>
                      <link>https://example.com/post</link>
                      <guid>https://example.com/post</guid>
                      <description>text</description>
                      <enclosure url="https://cdn.example.com/encl.png" length="1234" type="image/png" />
                    </item>
                  </channel>
                </rss>
                """;
        ParsedFeed result = parser.parse(xml);
        assertThat(result.getArticles().get(0).getImageUrl())
                .isEqualTo("https://cdn.example.com/encl.png");
    }

    @Test
    void shouldFallBackToFirstImgInContent() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/">
                  <channel>
                    <title>Inline Feed</title>
                    <link>https://example.com</link>
                    <description>desc</description>
                    <item>
                      <title>Post</title>
                      <link>https://example.com/post</link>
                      <guid>https://example.com/post</guid>
                      <description>text only</description>
                      <content:encoded><![CDATA[<p>hi</p><img src="https://cdn.example.com/inline.jpg" alt="x" /><p>more</p>]]></content:encoded>
                    </item>
                  </channel>
                </rss>
                """;
        ParsedFeed result = parser.parse(xml);
        assertThat(result.getArticles().get(0).getImageUrl())
                .isEqualTo("https://cdn.example.com/inline.jpg");
    }

    @Test
    void shouldExtractImageFromJsonFeed() {
        String json = """
                {
                  "version": "https://jsonfeed.org/version/1.1",
                  "title": "JSON",
                  "home_page_url": "https://example.com",
                  "items": [
                    {
                      "id": "x",
                      "title": "t",
                      "url": "https://example.com/x",
                      "content_html": "<p>hi</p>",
                      "image": "https://cdn.example.com/json.jpg",
                      "date_published": "2026-03-01T12:00:00Z"
                    }
                  ]
                }
                """;
        ParsedFeed result = parser.parse(json);
        assertThat(result.getArticles().get(0).getImageUrl())
                .isEqualTo("https://cdn.example.com/json.jpg");
    }

    @Test
    void shouldReturnNullImageWhenAbsent() {
        String xml = loadResource("/feeds/sample-rss.xml");
        ParsedFeed result = parser.parse(xml);
        assertThat(result.getArticles().get(0).getImageUrl()).isNull();
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
