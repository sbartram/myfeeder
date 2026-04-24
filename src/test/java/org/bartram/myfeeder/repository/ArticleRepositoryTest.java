package org.bartram.myfeeder.repository;

import org.bartram.myfeeder.TestcontainersConfiguration;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.model.FeedType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJdbcTest
@Import(TestcontainersConfiguration.class)
class ArticleRepositoryTest {

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private FeedRepository feedRepository;

    private Feed savedFeed;

    @BeforeEach
    void setUp() {
        var feed = new Feed();
        feed.setUrl("https://example.com/feed.xml");
        feed.setTitle("Test Feed");
        feed.setFeedType(FeedType.RSS);
        feed.setCreatedAt(Instant.now());
        savedFeed = feedRepository.save(feed);
    }

    @Test
    void shouldDeduplicateByFeedIdAndGuid() {
        var article = new Article();
        article.setFeedId(savedFeed.getId());
        article.setGuid("unique-guid-1");
        article.setTitle("Article 1");
        article.setUrl("https://example.com/1");
        article.setFetchedAt(Instant.now());
        articleRepository.save(article);

        assertThat(articleRepository.existsByFeedIdAndGuid(savedFeed.getId(), "unique-guid-1")).isTrue();
        assertThat(articleRepository.existsByFeedIdAndGuid(savedFeed.getId(), "nonexistent")).isFalse();
    }

    @Test
    void shouldBulkMarkReadByIds() {
        var a1 = createArticle("guid-1", "Article 1");
        var a2 = createArticle("guid-2", "Article 2");
        a1 = articleRepository.save(a1);
        a2 = articleRepository.save(a2);

        articleRepository.markReadByIds(List.of(a1.getId(), a2.getId()));

        assertThat(articleRepository.findById(a1.getId()).get().isRead()).isTrue();
        assertThat(articleRepository.findById(a2.getId()).get().isRead()).isTrue();
    }

    @Test
    void shouldMarkReadByFeedIdOlderThan() {
        var old = createArticle("old-guid", "Old Article");
        old.setPublishedAt(Instant.now().minus(10, java.time.temporal.ChronoUnit.DAYS));
        old = articleRepository.save(old);

        var recent = createArticle("recent-guid", "Recent Article");
        recent.setPublishedAt(Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS));
        recent = articleRepository.save(recent);

        Instant cutoff = Instant.now().minus(7, java.time.temporal.ChronoUnit.DAYS);
        articleRepository.markReadByFeedIdOlderThan(savedFeed.getId(), cutoff);

        assertThat(articleRepository.findById(old.getId()).get().isRead()).isTrue();
        assertThat(articleRepository.findById(recent.getId()).get().isRead()).isFalse();
    }

    @Test
    void shouldClearOldContent() {
        var old = createArticle("old-guid", "Old Article");
        old.setFetchedAt(Instant.now().minusSeconds(60 * 60 * 24 * 60)); // 60 days ago
        old.setContent("<p>Old content</p>");
        old.setSummary("Old summary");
        old = articleRepository.save(old);

        var recent = createArticle("recent-guid", "Recent Article");
        recent.setContent("<p>Recent content</p>");
        recent.setSummary("Recent summary");
        recent = articleRepository.save(recent);

        articleRepository.clearContentOlderThan(Instant.now().minusSeconds(60 * 60 * 24 * 30));

        assertThat(articleRepository.findById(old.getId()).get().getContent()).isNull();
        assertThat(articleRepository.findById(old.getId()).get().getSummary()).isEqualTo("Old summary");
        assertThat(articleRepository.findById(recent.getId()).get().getContent()).isEqualTo("<p>Recent content</p>");
    }

    @Test
    void shouldSortByPublishedAtNotById() {
        // Create articles where ID order does NOT match published_at order.
        // This reproduces the bug: a batch-fetched feed inserts old articles
        // with sequential IDs, then a newer article arrives with a higher ID.
        var oldArticle = createArticle("old", "Old Article");
        oldArticle.setPublishedAt(Instant.parse("2026-04-19T12:00:00Z"));
        oldArticle = articleRepository.save(oldArticle);

        var newerArticle = createArticle("newer", "Newer Article");
        newerArticle.setPublishedAt(Instant.parse("2026-04-20T12:00:00Z"));
        newerArticle = articleRepository.save(newerArticle);

        // Insert a third article with a HIGHER ID but OLDEST published_at
        var oldestArticle = createArticle("oldest", "Oldest Article");
        oldestArticle.setPublishedAt(Instant.parse("2026-04-18T12:00:00Z"));
        oldestArticle = articleRepository.save(oldestArticle);

        // DESC: should be newest published_at first, NOT highest ID first
        var descResults = articleRepository.findFiltered(null, null, null, 10);
        assertThat(descResults).extracting(Article::getTitle)
                .containsExactly("Newer Article", "Old Article", "Oldest Article");

        // ASC: should be oldest published_at first
        var ascResults = articleRepository.findFilteredAsc(null, null, null, 10);
        assertThat(ascResults).extracting(Article::getTitle)
                .containsExactly("Oldest Article", "Old Article", "Newer Article");
    }

    @Test
    void shouldPaginateByPublishedAtWithCursor() {
        var a1 = createArticle("a1", "Article 1");
        a1.setPublishedAt(Instant.parse("2026-04-18T12:00:00Z"));
        a1 = articleRepository.save(a1);

        var a2 = createArticle("a2", "Article 2");
        a2.setPublishedAt(Instant.parse("2026-04-19T12:00:00Z"));
        a2 = articleRepository.save(a2);

        var a3 = createArticle("a3", "Article 3");
        a3.setPublishedAt(Instant.parse("2026-04-20T12:00:00Z"));
        a3 = articleRepository.save(a3);

        // DESC pagination: first page returns a3, cursor at a3's publishedAt/id
        var page1Desc = articleRepository.findFiltered(null, null, null, 1);
        assertThat(page1Desc).extracting(Article::getTitle).containsExactly("Article 3");

        // Second page: articles before a3's published_at
        var page2Desc = articleRepository.findFilteredBefore(
                null, null, null, a3.getPublishedAt(), a3.getId(), 1);
        assertThat(page2Desc).extracting(Article::getTitle).containsExactly("Article 2");

        // ASC pagination: first page returns a1
        var page1Asc = articleRepository.findFilteredAsc(null, null, null, 1);
        assertThat(page1Asc).extracting(Article::getTitle).containsExactly("Article 1");

        // Second page: articles after a1's published_at
        var page2Asc = articleRepository.findFilteredAfter(
                null, null, null, a1.getPublishedAt(), a1.getId(), 1);
        assertThat(page2Asc).extracting(Article::getTitle).containsExactly("Article 2");
    }

    private Article createArticle(String guid, String title) {
        var article = new Article();
        article.setFeedId(savedFeed.getId());
        article.setGuid(guid);
        article.setTitle(title);
        article.setUrl("https://example.com/" + guid);
        article.setFetchedAt(Instant.now());
        return article;
    }
}
