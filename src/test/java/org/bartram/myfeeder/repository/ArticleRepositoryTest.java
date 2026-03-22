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
