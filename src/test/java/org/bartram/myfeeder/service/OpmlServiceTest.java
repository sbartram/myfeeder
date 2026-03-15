package org.bartram.myfeeder.service;

import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.model.Folder;
import org.bartram.myfeeder.parser.OpmlFeed;
import org.bartram.myfeeder.parser.OpmlParseException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpmlServiceTest {

    private final OpmlService opmlService = new OpmlService();

    private InputStream resource(String name) {
        return getClass().getResourceAsStream("/opml/" + name);
    }

    private InputStream streamOf(String xml) {
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void shouldParseStandardOpmlWithFoldersAndFeeds() {
        List<OpmlFeed> feeds = opmlService.parseOpml(resource("standard.opml"));

        assertThat(feeds).hasSize(4);

        assertThat(feeds).filteredOn(f -> "Tech".equals(f.folderName())).hasSize(2);
        assertThat(feeds).filteredOn(f -> "News".equals(f.folderName())).hasSize(1);
        assertThat(feeds).filteredOn(f -> f.folderName() == null).hasSize(1);

        OpmlFeed xkcd = feeds.stream().filter(f -> "xkcd".equals(f.title())).findFirst().orElseThrow();
        assertThat(xkcd.xmlUrl()).isEqualTo("https://xkcd.com/rss.xml");
        assertThat(xkcd.htmlUrl()).isEqualTo("https://xkcd.com");
        assertThat(xkcd.folderName()).isNull();
    }

    @Test
    void shouldParseFlatOpmlWithNoFolders() {
        List<OpmlFeed> feeds = opmlService.parseOpml(resource("flat.opml"));

        assertThat(feeds).hasSize(2);
        assertThat(feeds).allMatch(f -> f.folderName() == null);
    }

    @Test
    void shouldFlattenDeeplyNestedOutlines() {
        List<OpmlFeed> feeds = opmlService.parseOpml(resource("nested.opml"));

        assertThat(feeds).hasSize(2);
        // Both feeds should be assigned to top-level folder "Tech" regardless of nesting
        assertThat(feeds).allMatch(f -> "Tech".equals(f.folderName()));
    }

    @Test
    void shouldReturnEmptyListForEmptyOpml() {
        List<OpmlFeed> feeds = opmlService.parseOpml(resource("empty.opml"));
        assertThat(feeds).isEmpty();
    }

    @Test
    void shouldThrowOnMalformedXml() {
        assertThatThrownBy(() -> opmlService.parseOpml(streamOf("<not valid xml")))
                .isInstanceOf(OpmlParseException.class);
    }

    @Test
    void shouldThrowOnMissingBody() {
        assertThatThrownBy(() -> opmlService.parseOpml(streamOf(
                "<?xml version=\"1.0\"?><opml version=\"2.0\"><head/></opml>")))
                .isInstanceOf(OpmlParseException.class)
                .hasMessageContaining("body");
    }

    @Test
    void shouldGenerateOpmlWithFoldersAndFeeds() {
        var folder = new Folder();
        folder.setId(1L);
        folder.setName("Tech");

        var feed1 = new Feed();
        feed1.setUrl("https://example.com/feed1.xml");
        feed1.setTitle("Feed One");
        feed1.setSiteUrl("https://example.com/1");
        feed1.setFolderId(1L);

        var feed2 = new Feed();
        feed2.setUrl("https://example.com/feed2.xml");
        feed2.setTitle("Feed Two");
        feed2.setSiteUrl("https://example.com/2");

        String xml = opmlService.generateOpml(List.of(feed1, feed2), List.of(folder));

        assertThat(xml).contains("<opml version=\"2.0\">");
        assertThat(xml).contains("<title>myfeeder subscriptions</title>");
        // feed1 should be nested under Tech folder
        assertThat(xml).contains("text=\"Tech\"");
        assertThat(xml).contains("xmlUrl=\"https://example.com/feed1.xml\"");
        // feed2 should be at top level (no folder)
        assertThat(xml).contains("xmlUrl=\"https://example.com/feed2.xml\"");
    }

    @Test
    void shouldGenerateValidOpmlThatCanBeReparsed() {
        var feed = new Feed();
        feed.setUrl("https://example.com/feed.xml");
        feed.setTitle("Roundtrip Feed");
        feed.setSiteUrl("https://example.com");

        String xml = opmlService.generateOpml(List.of(feed), List.of());
        List<OpmlFeed> parsed = opmlService.parseOpml(streamOf(xml));

        assertThat(parsed).hasSize(1);
        assertThat(parsed.getFirst().title()).isEqualTo("Roundtrip Feed");
        assertThat(parsed.getFirst().xmlUrl()).isEqualTo("https://example.com/feed.xml");
    }
}
