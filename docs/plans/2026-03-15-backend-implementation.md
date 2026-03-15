# MyFeeder Backend Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build the backend for a single-user Feedly-like feed reader supporting RSS, Atom, and JSON Feed with per-feed polling, article state tracking, and Raindrop.io integration.

**Architecture:** Layered Spring Boot 4.0.3 monolith (Controller → Service → Repository) with Spring Data JDBC for persistence, dynamic Spring scheduling for per-feed polling, ROME for RSS/Atom parsing, and RestClient for outbound HTTP. Redis provides optional caching.

**Tech Stack:** Java 21, Spring Boot 4.0.3, Spring Data JDBC, PostgreSQL, Redis, Flyway, ROME 2.1.0, Resilience4j, Lombok, Testcontainers

**Spec:** `docs/superpowers/specs/2026-03-15-backend-design.md`

---

### Task 1: Add Dependencies

**Files:**
- Modify: `build.gradle.kts`

**Step 1: Add ROME and Flyway dependencies**

Add to the `dependencies` block in `build.gradle.kts`:

```kotlin
implementation("com.rometools:rome:2.1.0")
implementation("org.flywaydb:flyway-core")
implementation("org.flywaydb:flyway-database-postgresql")
```

**Step 2: Verify build compiles**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add build.gradle.kts
git commit -m "build: add ROME and Flyway dependencies"
```

---

### Task 2: Configuration Properties

**Files:**
- Create: `src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java`
- Modify: `src/main/resources/application.yaml`

**Step 1: Write MyfeederProperties**

```java
package org.bartram.myfeeder.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "myfeeder")
public class MyfeederProperties {

    private Polling polling = new Polling();
    private Retention retention = new Retention();
    private Raindrop raindrop = new Raindrop();

    @Data
    public static class Polling {
        private int defaultIntervalMinutes = 15;
        private int maxIntervalMinutes = 1440;
        private int backoffThreshold = 5;
    }

    @Data
    public static class Retention {
        private int fullContentDays = 30;
        private String cleanupCron = "0 0 3 * * *";
    }

    @Data
    public static class Raindrop {
        private String apiBaseUrl = "https://api.raindrop.io/rest/v1";
    }
}
```

**Step 2: Update application.yaml**

Replace the entire contents of `src/main/resources/application.yaml` with:

```yaml
spring:
  application:
    name: myfeeder

myfeeder:
  polling:
    default-interval-minutes: 15
    max-interval-minutes: 1440
    backoff-threshold: 5
  retention:
    full-content-days: 30
    cleanup-cron: "0 0 3 * * *"
  raindrop:
    api-base-url: https://api.raindrop.io/rest/v1
```

**Step 3: Enable configuration properties on the app**

Add `@ConfigurationPropertiesScan` to `MyfeederApplication.java`:

```java
@SpringBootApplication
@ConfigurationPropertiesScan
public class MyfeederApplication {
```

**Step 4: Verify build compiles**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add src/main/java/org/bartram/myfeeder/config/MyfeederProperties.java \
        src/main/resources/application.yaml \
        src/main/java/org/bartram/myfeeder/MyfeederApplication.java
git commit -m "feat: add MyfeederProperties configuration"
```

---

### Task 3: Entity Models and Enums

**Files:**
- Create: `src/main/java/org/bartram/myfeeder/model/FeedType.java`
- Create: `src/main/java/org/bartram/myfeeder/model/IntegrationType.java`
- Create: `src/main/java/org/bartram/myfeeder/model/Feed.java`
- Create: `src/main/java/org/bartram/myfeeder/model/Article.java`
- Create: `src/main/java/org/bartram/myfeeder/model/IntegrationConfig.java`

**Step 1: Create enums**

`FeedType.java`:
```java
package org.bartram.myfeeder.model;

public enum FeedType {
    RSS, ATOM, JSON_FEED
}
```

`IntegrationType.java`:
```java
package org.bartram.myfeeder.model;

public enum IntegrationType {
    RAINDROP
}
```

**Step 2: Create Feed entity**

```java
package org.bartram.myfeeder.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Data
@Table("feed")
public class Feed {
    @Id
    private Long id;
    private String url;
    private String title;
    private String description;
    private String siteUrl;
    private FeedType feedType;
    private int pollIntervalMinutes = 15;
    private Instant lastPolledAt;
    private Instant lastSuccessfulPollAt;
    private int errorCount;
    private String lastError;
    private String etag;
    private String lastModifiedHeader;
    private Instant createdAt;
}
```

**Step 3: Create Article entity**

```java
package org.bartram.myfeeder.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
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
    private Instant publishedAt;
    private Instant fetchedAt;
    private boolean read;
    private boolean starred;
}
```

**Step 4: Create IntegrationConfig entity**

```java
package org.bartram.myfeeder.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("integration_config")
public class IntegrationConfig {
    @Id
    private Long id;
    private IntegrationType type;
    private String config;
    private boolean enabled;
}
```

**Step 5: Verify build compiles**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add src/main/java/org/bartram/myfeeder/model/
git commit -m "feat: add entity models and enums"
```

---

### Task 4: Flyway Schema Migration

**Files:**
- Create: `src/main/resources/db/migration/V1__initial_schema.sql`

**Step 1: Write the migration**

```sql
CREATE TABLE feed (
    id BIGSERIAL PRIMARY KEY,
    url TEXT NOT NULL,
    title TEXT NOT NULL,
    description TEXT,
    site_url TEXT,
    feed_type TEXT NOT NULL,
    poll_interval_minutes INTEGER NOT NULL DEFAULT 15,
    last_polled_at TIMESTAMPTZ,
    last_successful_poll_at TIMESTAMPTZ,
    error_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    etag TEXT,
    last_modified_header TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE article (
    id BIGSERIAL PRIMARY KEY,
    feed_id BIGINT NOT NULL REFERENCES feed(id) ON DELETE CASCADE,
    guid TEXT NOT NULL,
    title TEXT NOT NULL,
    url TEXT NOT NULL,
    author TEXT,
    content TEXT,
    summary TEXT,
    published_at TIMESTAMPTZ,
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    read BOOLEAN NOT NULL DEFAULT FALSE,
    starred BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (feed_id, guid)
);

CREATE INDEX idx_article_feed_id ON article(feed_id);
CREATE INDEX idx_article_published_at ON article(published_at DESC);
CREATE INDEX idx_article_read ON article(read) WHERE read = FALSE;
CREATE INDEX idx_article_starred ON article(starred) WHERE starred = TRUE;
CREATE INDEX idx_article_fetched_at ON article(fetched_at);

CREATE TABLE integration_config (
    id BIGSERIAL PRIMARY KEY,
    type TEXT NOT NULL UNIQUE,
    config TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);
```

**Step 2: Run tests to verify Flyway migration works with Testcontainers**

Run: `./gradlew test --tests "org.bartram.myfeeder.MyfeederApplicationTests"`
Expected: PASS (context loads with Flyway running the migration against Testcontainers Postgres)

**Step 3: Commit**

```bash
git add src/main/resources/db/migration/V1__initial_schema.sql
git commit -m "feat: add Flyway initial schema migration"
```

---

### Task 5: Repositories

**Files:**
- Create: `src/main/java/org/bartram/myfeeder/repository/FeedRepository.java`
- Create: `src/main/java/org/bartram/myfeeder/repository/ArticleRepository.java`
- Create: `src/main/java/org/bartram/myfeeder/repository/IntegrationConfigRepository.java`
- Create: `src/test/java/org/bartram/myfeeder/repository/FeedRepositoryTest.java`
- Create: `src/test/java/org/bartram/myfeeder/repository/ArticleRepositoryTest.java`
- Create: `src/test/java/org/bartram/myfeeder/repository/IntegrationConfigRepositoryTest.java`

**Step 1: Write FeedRepository**

```java
package org.bartram.myfeeder.repository;

import org.bartram.myfeeder.model.Feed;
import org.springframework.data.repository.ListCrudRepository;

public interface FeedRepository extends ListCrudRepository<Feed, Long> {
}
```

**Step 2: Write ArticleRepository**

```java
package org.bartram.myfeeder.repository;

import org.bartram.myfeeder.model.Article;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ArticleRepository extends ListCrudRepository<Article, Long> {

    List<Article> findByFeedId(Long feedId);

    Optional<Article> findByFeedIdAndGuid(Long feedId, String guid);

    List<Article> findByStarredTrue();

    List<Article> findByReadFalse();

    @Modifying
    @Query("UPDATE article SET read = true WHERE id IN (:ids)")
    void markReadByIds(List<Long> ids);

    @Modifying
    @Query("UPDATE article SET read = true WHERE feed_id = :feedId")
    void markAllReadByFeedId(Long feedId);

    @Modifying
    @Query("UPDATE article SET content = NULL WHERE fetched_at < :cutoff AND content IS NOT NULL")
    void clearContentOlderThan(Instant cutoff);

    @Query("SELECT EXISTS(SELECT 1 FROM article WHERE feed_id = :feedId AND guid = :guid)")
    boolean existsByFeedIdAndGuid(Long feedId, String guid);
}
```

**Step 3: Write IntegrationConfigRepository**

```java
package org.bartram.myfeeder.repository;

import org.bartram.myfeeder.model.IntegrationConfig;
import org.bartram.myfeeder.model.IntegrationType;
import org.springframework.data.repository.ListCrudRepository;

import java.util.Optional;

public interface IntegrationConfigRepository extends ListCrudRepository<IntegrationConfig, Long> {

    Optional<IntegrationConfig> findByType(IntegrationType type);

    void deleteByType(IntegrationType type);
}
```

**Step 4: Write FeedRepositoryTest**

```java
package org.bartram.myfeeder.repository;

import org.bartram.myfeeder.TestcontainersConfiguration;
import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.model.FeedType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJdbcTest
@Import(TestcontainersConfiguration.class)
class FeedRepositoryTest {

    @Autowired
    private FeedRepository feedRepository;

    @Test
    void shouldSaveAndRetrieveFeed() {
        var feed = new Feed();
        feed.setUrl("https://example.com/feed.xml");
        feed.setTitle("Example Feed");
        feed.setFeedType(FeedType.RSS);
        feed.setCreatedAt(Instant.now());

        var saved = feedRepository.save(feed);

        assertThat(saved.getId()).isNotNull();
        var found = feedRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Example Feed");
    }
}
```

**Step 5: Write ArticleRepositoryTest**

```java
package org.bartram.myfeeder.repository;

import org.bartram.myfeeder.TestcontainersConfiguration;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.model.FeedType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
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
```

**Step 6: Write IntegrationConfigRepositoryTest**

```java
package org.bartram.myfeeder.repository;

import org.bartram.myfeeder.TestcontainersConfiguration;
import org.bartram.myfeeder.model.IntegrationConfig;
import org.bartram.myfeeder.model.IntegrationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJdbcTest
@Import(TestcontainersConfiguration.class)
class IntegrationConfigRepositoryTest {

    @Autowired
    private IntegrationConfigRepository repository;

    @Test
    void shouldFindByType() {
        var config = new IntegrationConfig();
        config.setType(IntegrationType.RAINDROP);
        config.setConfig("{\"apiToken\":\"test\",\"collectionId\":12345}");
        config.setEnabled(true);
        repository.save(config);

        var found = repository.findByType(IntegrationType.RAINDROP);
        assertThat(found).isPresent();
        assertThat(found.get().getConfig()).contains("test");
    }
}
```

**Step 7: Run all repository tests**

Run: `./gradlew test --tests "org.bartram.myfeeder.repository.*"`
Expected: ALL PASS

**Step 8: Commit**

```bash
git add src/main/java/org/bartram/myfeeder/repository/ \
        src/test/java/org/bartram/myfeeder/repository/
git commit -m "feat: add repositories with tests"
```

---

### Task 6: Feed Parser

**Files:**
- Create: `src/main/java/org/bartram/myfeeder/parser/ParsedFeed.java`
- Create: `src/main/java/org/bartram/myfeeder/parser/ParsedArticle.java`
- Create: `src/main/java/org/bartram/myfeeder/parser/FeedParser.java`
- Create: `src/test/java/org/bartram/myfeeder/parser/FeedParserTest.java`
- Create: `src/test/resources/feeds/sample-rss.xml`
- Create: `src/test/resources/feeds/sample-atom.xml`
- Create: `src/test/resources/feeds/sample-json-feed.json`

**Step 1: Create ParsedFeed and ParsedArticle DTOs**

`ParsedFeed.java`:
```java
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
```

`ParsedArticle.java`:
```java
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
```

**Step 2: Create test fixture files**

`src/test/resources/feeds/sample-rss.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0">
  <channel>
    <title>Example RSS Feed</title>
    <link>https://example.com</link>
    <description>An example RSS feed</description>
    <item>
      <title>First Post</title>
      <link>https://example.com/first</link>
      <guid>https://example.com/first</guid>
      <description>Summary of the first post</description>
      <author>author@example.com</author>
      <pubDate>Sat, 01 Mar 2026 12:00:00 GMT</pubDate>
    </item>
    <item>
      <title>Second Post</title>
      <link>https://example.com/second</link>
      <guid>https://example.com/second</guid>
      <description>Summary of the second post</description>
      <pubDate>Sun, 02 Mar 2026 12:00:00 GMT</pubDate>
    </item>
  </channel>
</rss>
```

`src/test/resources/feeds/sample-atom.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns="http://www.w3.org/2005/Atom">
  <title>Example Atom Feed</title>
  <link href="https://example.com"/>
  <subtitle>An example Atom feed</subtitle>
  <entry>
    <title>Atom Entry</title>
    <link href="https://example.com/atom-entry"/>
    <id>urn:uuid:atom-entry-1</id>
    <summary>Summary of atom entry</summary>
    <content type="html">&lt;p&gt;Full content&lt;/p&gt;</content>
    <author><name>Atom Author</name></author>
    <updated>2026-03-01T12:00:00Z</updated>
  </entry>
</feed>
```

`src/test/resources/feeds/sample-json-feed.json`:
```json
{
  "version": "https://jsonfeed.org/version/1.1",
  "title": "Example JSON Feed",
  "home_page_url": "https://example.com",
  "description": "An example JSON Feed",
  "items": [
    {
      "id": "json-item-1",
      "title": "JSON Feed Entry",
      "url": "https://example.com/json-entry",
      "content_html": "<p>JSON feed content</p>",
      "summary": "Summary of JSON entry",
      "authors": [{"name": "JSON Author"}],
      "date_published": "2026-03-01T12:00:00Z"
    }
  ]
}
```

**Step 3: Write FeedParserTest**

```java
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
```

**Step 4: Run tests to verify they fail**

Run: `./gradlew test --tests "org.bartram.myfeeder.parser.FeedParserTest"`
Expected: FAIL (FeedParser class does not exist)

**Step 5: Implement FeedParser**

```java
package org.bartram.myfeeder.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndPerson;
import com.rometools.rome.io.SyndFeedInput;
import org.bartram.myfeeder.model.FeedType;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Component
public class FeedParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ParsedFeed parse(String rawContent) {
        FeedType type = detectFeedType(rawContent);
        return switch (type) {
            case RSS, ATOM -> parseWithRome(rawContent, type);
            case JSON_FEED -> parseJsonFeed(rawContent);
        };
    }

    public FeedType detectFeedType(String rawContent) {
        String trimmed = rawContent.trim();
        if (trimmed.startsWith("{")) {
            return FeedType.JSON_FEED;
        }
        if (trimmed.contains("<feed") && trimmed.contains("http://www.w3.org/2005/Atom")) {
            return FeedType.ATOM;
        }
        return FeedType.RSS;
    }

    private ParsedFeed parseWithRome(String rawContent, FeedType type) {
        try {
            SyndFeedInput input = new SyndFeedInput();
            SyndFeed syndFeed = input.build(new StringReader(rawContent));

            List<ParsedArticle> articles = syndFeed.getEntries().stream()
                    .map(this::toArticle)
                    .toList();

            return ParsedFeed.builder()
                    .title(syndFeed.getTitle())
                    .description(syndFeed.getDescription())
                    .siteUrl(syndFeed.getLink())
                    .feedType(type)
                    .articles(articles)
                    .build();
        } catch (Exception e) {
            throw new FeedParseException("Failed to parse " + type + " feed", e);
        }
    }

    private ParsedArticle toArticle(SyndEntry entry) {
        String content = Optional.ofNullable(entry.getContents())
                .filter(c -> !c.isEmpty())
                .map(c -> c.get(0))
                .map(SyndContent::getValue)
                .orElse(null);

        String summary = Optional.ofNullable(entry.getDescription())
                .map(SyndContent::getValue)
                .orElse(null);

        String author = Optional.ofNullable(entry.getAuthors())
                .filter(a -> !a.isEmpty())
                .map(a -> a.get(0))
                .map(SyndPerson::getName)
                .orElse(entry.getAuthor());

        Instant publishedAt = Optional.ofNullable(entry.getPublishedDate())
                .or(() -> Optional.ofNullable(entry.getUpdatedDate()))
                .map(Date::toInstant)
                .orElse(null);

        String guid = entry.getUri() != null ? entry.getUri() : entry.getLink();

        return ParsedArticle.builder()
                .guid(guid)
                .title(entry.getTitle())
                .url(entry.getLink())
                .author(author)
                .content(content)
                .summary(summary)
                .publishedAt(publishedAt)
                .build();
    }

    private ParsedFeed parseJsonFeed(String rawContent) {
        try {
            JsonNode root = objectMapper.readTree(rawContent);
            List<ParsedArticle> articles = new ArrayList<>();

            JsonNode items = root.get("items");
            if (items != null && items.isArray()) {
                for (JsonNode item : items) {
                    articles.add(parseJsonItem(item));
                }
            }

            return ParsedFeed.builder()
                    .title(textOrNull(root, "title"))
                    .description(textOrNull(root, "description"))
                    .siteUrl(textOrNull(root, "home_page_url"))
                    .feedType(FeedType.JSON_FEED)
                    .articles(articles)
                    .build();
        } catch (Exception e) {
            throw new FeedParseException("Failed to parse JSON Feed", e);
        }
    }

    private ParsedArticle parseJsonItem(JsonNode item) {
        String author = null;
        JsonNode authors = item.get("authors");
        if (authors != null && authors.isArray() && !authors.isEmpty()) {
            author = textOrNull(authors.get(0), "name");
        }

        Instant publishedAt = null;
        String dateStr = textOrNull(item, "date_published");
        if (dateStr != null) {
            publishedAt = Instant.parse(dateStr);
        }

        return ParsedArticle.builder()
                .guid(textOrNull(item, "id"))
                .title(textOrNull(item, "title"))
                .url(textOrNull(item, "url"))
                .author(author)
                .content(textOrNull(item, "content_html"))
                .summary(textOrNull(item, "summary"))
                .publishedAt(publishedAt)
                .build();
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        return (child != null && !child.isNull()) ? child.asText() : null;
    }
}
```

**Step 6: Create FeedParseException**

```java
package org.bartram.myfeeder.parser;

public class FeedParseException extends RuntimeException {
    public FeedParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

**Step 7: Run tests to verify they pass**

Run: `./gradlew test --tests "org.bartram.myfeeder.parser.FeedParserTest"`
Expected: ALL PASS

**Step 8: Commit**

```bash
git add src/main/java/org/bartram/myfeeder/parser/ \
        src/test/java/org/bartram/myfeeder/parser/ \
        src/test/resources/feeds/
git commit -m "feat: add feed parser with RSS, Atom, and JSON Feed support"
```

---

### Task 7: FeedService

**Files:**
- Create: `src/main/java/org/bartram/myfeeder/service/FeedService.java`
- Create: `src/test/java/org/bartram/myfeeder/service/FeedServiceTest.java`

**Step 1: Write FeedServiceTest**

```java
package org.bartram.myfeeder.service;

import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.model.FeedType;
import org.bartram.myfeeder.parser.FeedParser;
import org.bartram.myfeeder.parser.ParsedFeed;
import org.bartram.myfeeder.repository.FeedRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

    @Mock
    private FeedRepository feedRepository;

    @Mock
    private FeedParser feedParser;

    @Mock
    private RestClient restClient;

    @Mock
    private MyfeederProperties properties;

    @InjectMocks
    private FeedService feedService;

    @Test
    void shouldReturnAllFeeds() {
        var feed = new Feed();
        feed.setTitle("Test");
        when(feedRepository.findAll()).thenReturn(List.of(feed));

        var result = feedService.findAll();

        assertThat(result).hasSize(1);
    }

    @Test
    void shouldFindFeedById() {
        var feed = new Feed();
        feed.setId(1L);
        when(feedRepository.findById(1L)).thenReturn(Optional.of(feed));

        var result = feedService.findById(1L);

        assertThat(result).isPresent();
    }

    @Test
    void shouldDeleteFeed() {
        feedService.delete(1L);
        verify(feedRepository).deleteById(1L);
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "org.bartram.myfeeder.service.FeedServiceTest"`
Expected: FAIL (FeedService does not exist)

**Step 3: Implement FeedService**

```java
package org.bartram.myfeeder.service;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.parser.FeedParser;
import org.bartram.myfeeder.parser.ParsedFeed;
import org.bartram.myfeeder.repository.FeedRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FeedService {

    private final FeedRepository feedRepository;
    private final FeedParser feedParser;
    private final RestClient.Builder restClientBuilder;
    private final MyfeederProperties properties;

    public List<Feed> findAll() {
        return feedRepository.findAll();
    }

    public Optional<Feed> findById(Long id) {
        return feedRepository.findById(id);
    }

    public Feed subscribe(String feedUrl) {
        String rawContent = restClientBuilder.build()
                .get()
                .uri(feedUrl)
                .retrieve()
                .body(String.class);

        ParsedFeed parsed = feedParser.parse(rawContent);

        var feed = new Feed();
        feed.setUrl(feedUrl);
        feed.setTitle(parsed.getTitle());
        feed.setDescription(parsed.getDescription());
        feed.setSiteUrl(parsed.getSiteUrl());
        feed.setFeedType(parsed.getFeedType());
        feed.setPollIntervalMinutes(properties.getPolling().getDefaultIntervalMinutes());
        feed.setCreatedAt(Instant.now());

        return feedRepository.save(feed);
    }

    public Feed update(Long id, Feed updates) {
        Feed feed = feedRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Feed not found: " + id));

        if (updates.getTitle() != null) {
            feed.setTitle(updates.getTitle());
        }
        if (updates.getPollIntervalMinutes() > 0) {
            feed.setPollIntervalMinutes(updates.getPollIntervalMinutes());
        }

        return feedRepository.save(feed);
    }

    public void delete(Long id) {
        feedRepository.deleteById(id);
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "org.bartram.myfeeder.service.FeedServiceTest"`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add src/main/java/org/bartram/myfeeder/service/FeedService.java \
        src/test/java/org/bartram/myfeeder/service/FeedServiceTest.java
git commit -m "feat: add FeedService with subscribe, update, delete"
```

---

### Task 8: ArticleService

**Files:**
- Create: `src/main/java/org/bartram/myfeeder/service/ArticleService.java`
- Create: `src/test/java/org/bartram/myfeeder/service/ArticleServiceTest.java`

**Step 1: Write ArticleServiceTest**

```java
package org.bartram.myfeeder.service;

import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.repository.ArticleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {

    @Mock
    private ArticleRepository articleRepository;

    @InjectMocks
    private ArticleService articleService;

    @Test
    void shouldFindArticleById() {
        var article = new Article();
        article.setId(1L);
        article.setTitle("Test");
        when(articleRepository.findById(1L)).thenReturn(Optional.of(article));

        var result = articleService.findById(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("Test");
    }

    @Test
    void shouldMarkArticleAsRead() {
        var article = new Article();
        article.setId(1L);
        article.setRead(false);
        when(articleRepository.findById(1L)).thenReturn(Optional.of(article));
        when(articleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = articleService.updateState(1L, true, null);

        assertThat(result.isRead()).isTrue();
    }

    @Test
    void shouldToggleStarred() {
        var article = new Article();
        article.setId(1L);
        article.setStarred(false);
        when(articleRepository.findById(1L)).thenReturn(Optional.of(article));
        when(articleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = articleService.updateState(1L, null, true);

        assertThat(result.isStarred()).isTrue();
    }

    @Test
    void shouldBulkMarkReadByIds() {
        articleService.markRead(List.of(1L, 2L), null);
        verify(articleRepository).markReadByIds(List.of(1L, 2L));
    }

    @Test
    void shouldBulkMarkReadByFeedId() {
        articleService.markRead(null, 5L);
        verify(articleRepository).markAllReadByFeedId(5L);
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "org.bartram.myfeeder.service.ArticleServiceTest"`
Expected: FAIL

**Step 3: Implement ArticleService**

```java
package org.bartram.myfeeder.service;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.repository.ArticleRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ArticleService {

    private final ArticleRepository articleRepository;

    public Optional<Article> findById(Long id) {
        return articleRepository.findById(id);
    }

    public List<Article> findByFeedId(Long feedId) {
        return articleRepository.findByFeedId(feedId);
    }

    public List<Article> findAll() {
        return articleRepository.findAll();
    }

    public List<Article> findUnread() {
        return articleRepository.findByReadFalse();
    }

    public List<Article> findStarred() {
        return articleRepository.findByStarredTrue();
    }

    public Article updateState(Long id, Boolean read, Boolean starred) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Article not found: " + id));

        if (read != null) {
            article.setRead(read);
        }
        if (starred != null) {
            article.setStarred(starred);
        }

        return articleRepository.save(article);
    }

    public void markRead(List<Long> articleIds, Long feedId) {
        if (articleIds != null && !articleIds.isEmpty()) {
            articleRepository.markReadByIds(articleIds);
        } else if (feedId != null) {
            articleRepository.markAllReadByFeedId(feedId);
        } else {
            throw new IllegalArgumentException("Either articleIds or feedId must be provided");
        }
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "org.bartram.myfeeder.service.ArticleServiceTest"`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add src/main/java/org/bartram/myfeeder/service/ArticleService.java \
        src/test/java/org/bartram/myfeeder/service/ArticleServiceTest.java
git commit -m "feat: add ArticleService with state management and bulk mark-read"
```

---

### Task 9: FeedPollingService

**Files:**
- Create: `src/main/java/org/bartram/myfeeder/service/FeedPollingService.java`
- Create: `src/test/java/org/bartram/myfeeder/service/FeedPollingServiceTest.java`

**Step 1: Write FeedPollingServiceTest**

```java
package org.bartram.myfeeder.service;

import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.model.FeedType;
import org.bartram.myfeeder.parser.FeedParser;
import org.bartram.myfeeder.parser.ParsedArticle;
import org.bartram.myfeeder.parser.ParsedFeed;
import org.bartram.myfeeder.repository.ArticleRepository;
import org.bartram.myfeeder.repository.FeedRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedPollingServiceTest {

    @Mock private FeedRepository feedRepository;
    @Mock private ArticleRepository articleRepository;
    @Mock private FeedParser feedParser;
    @Mock private RestClient.Builder restClientBuilder;

    private FeedPollingService pollingService;

    @BeforeEach
    void setUp() {
        pollingService = new FeedPollingService(feedRepository, articleRepository, feedParser, restClientBuilder);
    }

    @Test
    void shouldSkipExistingArticlesDuringPoll() {
        var feed = new Feed();
        feed.setId(1L);
        feed.setUrl("https://example.com/feed.xml");
        feed.setFeedType(FeedType.RSS);
        when(feedRepository.findById(1L)).thenReturn(Optional.of(feed));

        var parsedFeed = ParsedFeed.builder()
                .title("Test")
                .feedType(FeedType.RSS)
                .articles(List.of(
                        ParsedArticle.builder().guid("existing").title("Old").url("https://example.com/old").build(),
                        ParsedArticle.builder().guid("new-one").title("New").url("https://example.com/new").build()
                ))
                .build();

        // Mock RestClient chain
        var restClient = mock(RestClient.class);
        var requestHeadersUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        var requestHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
        var responseSpec = mock(RestClient.ResponseSpec.class);
        when(restClientBuilder.build()).thenReturn(restClient);
        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(String.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.headers(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.exchange(any())).thenAnswer(invocation -> {
            // Simulate exchange returning the raw content
            return "<rss>...</rss>";
        });

        when(feedParser.parse("<rss>...</rss>")).thenReturn(parsedFeed);
        when(articleRepository.existsByFeedIdAndGuid(1L, "existing")).thenReturn(true);
        when(articleRepository.existsByFeedIdAndGuid(1L, "new-one")).thenReturn(false);
        when(articleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        pollingService.pollFeed(1L);

        // Should only save the new article, not the existing one
        var captor = ArgumentCaptor.forClass(org.bartram.myfeeder.model.Article.class);
        verify(articleRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getGuid()).isEqualTo("new-one");
    }

    @Test
    void shouldIncrementErrorCountOnFailure() {
        var feed = new Feed();
        feed.setId(1L);
        feed.setUrl("https://example.com/feed.xml");
        feed.setErrorCount(0);
        when(feedRepository.findById(1L)).thenReturn(Optional.of(feed));

        var restClient = mock(RestClient.class);
        when(restClientBuilder.build()).thenReturn(restClient);
        when(restClient.get()).thenThrow(new RuntimeException("Connection refused"));

        pollingService.pollFeed(1L);

        var captor = ArgumentCaptor.forClass(Feed.class);
        verify(feedRepository).save(captor.capture());
        assertThat(captor.getValue().getErrorCount()).isEqualTo(1);
        assertThat(captor.getValue().getLastError()).contains("Connection refused");
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "org.bartram.myfeeder.service.FeedPollingServiceTest"`
Expected: FAIL

**Step 3: Implement FeedPollingService**

```java
package org.bartram.myfeeder.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.parser.FeedParser;
import org.bartram.myfeeder.parser.ParsedArticle;
import org.bartram.myfeeder.parser.ParsedFeed;
import org.bartram.myfeeder.repository.ArticleRepository;
import org.bartram.myfeeder.repository.FeedRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedPollingService {

    private final FeedRepository feedRepository;
    private final ArticleRepository articleRepository;
    private final FeedParser feedParser;
    private final RestClient.Builder restClientBuilder;

    public void pollFeed(Long feedId) {
        Feed feed = feedRepository.findById(feedId)
                .orElseThrow(() -> new IllegalArgumentException("Feed not found: " + feedId));

        try {
            String rawContent = fetchFeedContent(feed);
            if (rawContent == null) {
                // 304 Not Modified
                feed.setLastPolledAt(Instant.now());
                feedRepository.save(feed);
                return;
            }

            ParsedFeed parsed = feedParser.parse(rawContent);
            int newCount = 0;

            for (ParsedArticle parsedArticle : parsed.getArticles()) {
                if (!articleRepository.existsByFeedIdAndGuid(feed.getId(), parsedArticle.getGuid())) {
                    Article article = toArticle(parsedArticle, feed.getId());
                    articleRepository.save(article);
                    newCount++;
                }
            }

            feed.setLastPolledAt(Instant.now());
            feed.setLastSuccessfulPollAt(Instant.now());
            feed.setErrorCount(0);
            feed.setLastError(null);
            feedRepository.save(feed);

            log.info("Polled feed '{}': {} new articles", feed.getTitle(), newCount);
        } catch (Exception e) {
            feed.setLastPolledAt(Instant.now());
            feed.setErrorCount(feed.getErrorCount() + 1);
            feed.setLastError(e.getMessage());
            feedRepository.save(feed);
            log.warn("Failed to poll feed '{}': {}", feed.getTitle(), e.getMessage());
        }
    }

    private String fetchFeedContent(Feed feed) {
        RestClient client = restClientBuilder.build();
        return client.get()
                .uri(feed.getUrl())
                .headers(headers -> {
                    if (feed.getEtag() != null) {
                        headers.setIfNoneMatch(feed.getEtag());
                    }
                    if (feed.getLastModifiedHeader() != null) {
                        headers.set("If-Modified-Since", feed.getLastModifiedHeader());
                    }
                })
                .exchange((request, response) -> {
                    if (response.getStatusCode().value() == 304) {
                        return null;
                    }
                    String etag = response.getHeaders().getETag();
                    String lastModified = response.getHeaders().getFirst("Last-Modified");
                    if (etag != null) feed.setEtag(etag);
                    if (lastModified != null) feed.setLastModifiedHeader(lastModified);

                    return new String(response.getBody().readAllBytes());
                });
    }

    private Article toArticle(ParsedArticle parsed, Long feedId) {
        var article = new Article();
        article.setFeedId(feedId);
        article.setGuid(parsed.getGuid());
        article.setTitle(parsed.getTitle());
        article.setUrl(parsed.getUrl());
        article.setAuthor(parsed.getAuthor());
        article.setContent(parsed.getContent());
        article.setSummary(parsed.getSummary());
        article.setPublishedAt(parsed.getPublishedAt() != null ? parsed.getPublishedAt() : Instant.now());
        article.setFetchedAt(Instant.now());
        return article;
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "org.bartram.myfeeder.service.FeedPollingServiceTest"`
Expected: ALL PASS (tests may need adjustment based on RestClient mock chain — fix as needed)

**Step 5: Commit**

```bash
git add src/main/java/org/bartram/myfeeder/service/FeedPollingService.java \
        src/test/java/org/bartram/myfeeder/service/FeedPollingServiceTest.java
git commit -m "feat: add FeedPollingService with dedup and conditional GET"
```

---

### Task 10: RaindropService

**Files:**
- Create: `src/main/java/org/bartram/myfeeder/integration/RaindropConfig.java`
- Create: `src/main/java/org/bartram/myfeeder/integration/RaindropService.java`
- Create: `src/test/java/org/bartram/myfeeder/integration/RaindropServiceTest.java`

**Step 1: Write RaindropConfig DTO**

```java
package org.bartram.myfeeder.integration;

import lombok.Data;

@Data
public class RaindropConfig {
    private String apiToken;
    private Long collectionId;
}
```

**Step 2: Write RaindropServiceTest**

```java
package org.bartram.myfeeder.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.model.IntegrationConfig;
import org.bartram.myfeeder.model.IntegrationType;
import org.bartram.myfeeder.repository.IntegrationConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaindropServiceTest {

    @Mock private IntegrationConfigRepository configRepository;
    @Mock private RestClient.Builder restClientBuilder;
    @Mock private MyfeederProperties properties;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private RaindropService raindropService;

    @Test
    void shouldThrowWhenConfigMissing() {
        when(configRepository.findByType(IntegrationType.RAINDROP)).thenReturn(Optional.empty());

        var article = new Article();
        article.setTitle("Test");
        article.setUrl("https://example.com");

        assertThatThrownBy(() -> raindropService.saveToRaindrop(article))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void shouldThrowWhenConfigDisabled() {
        var config = new IntegrationConfig();
        config.setType(IntegrationType.RAINDROP);
        config.setConfig("{\"apiToken\":\"tok\",\"collectionId\":123}");
        config.setEnabled(false);
        when(configRepository.findByType(IntegrationType.RAINDROP)).thenReturn(Optional.of(config));

        var article = new Article();
        article.setTitle("Test");
        article.setUrl("https://example.com");

        assertThatThrownBy(() -> raindropService.saveToRaindrop(article))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");
    }
}
```

**Step 3: Run tests to verify they fail**

Run: `./gradlew test --tests "org.bartram.myfeeder.integration.RaindropServiceTest"`
Expected: FAIL

**Step 4: Implement RaindropService**

```java
package org.bartram.myfeeder.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.model.IntegrationType;
import org.bartram.myfeeder.repository.IntegrationConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RaindropService {

    private final IntegrationConfigRepository configRepository;
    private final RestClient.Builder restClientBuilder;
    private final MyfeederProperties properties;
    private final ObjectMapper objectMapper;

    public void saveToRaindrop(Article article) {
        var integrationConfig = configRepository.findByType(IntegrationType.RAINDROP)
                .orElseThrow(() -> new IllegalStateException("Raindrop.io is not configured"));

        if (!integrationConfig.isEnabled()) {
            throw new IllegalStateException("Raindrop.io integration is disabled");
        }

        RaindropConfig config;
        try {
            config = objectMapper.readValue(integrationConfig.getConfig(), RaindropConfig.class);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid Raindrop configuration", e);
        }

        var body = Map.of(
                "link", article.getUrl(),
                "title", article.getTitle(),
                "collection", Map.of("$id", config.getCollectionId())
        );

        restClientBuilder.build()
                .post()
                .uri(properties.getRaindrop().getApiBaseUrl() + "/raindrop")
                .header("Authorization", "Bearer " + config.getApiToken())
                .body(body)
                .retrieve()
                .toBodilessEntity();

        log.info("Saved article '{}' to Raindrop.io", article.getTitle());
    }
}
```

**Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests "org.bartram.myfeeder.integration.RaindropServiceTest"`
Expected: ALL PASS

**Step 6: Commit**

```bash
git add src/main/java/org/bartram/myfeeder/integration/ \
        src/test/java/org/bartram/myfeeder/integration/
git commit -m "feat: add RaindropService for saving articles to Raindrop.io"
```

---

### Task 11: RetentionService

**Files:**
- Create: `src/main/java/org/bartram/myfeeder/service/RetentionService.java`
- Create: `src/test/java/org/bartram/myfeeder/service/RetentionServiceTest.java`

**Step 1: Write RetentionServiceTest**

```java
package org.bartram.myfeeder.service;

import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.repository.ArticleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetentionServiceTest {

    @Mock private ArticleRepository articleRepository;
    @Mock private MyfeederProperties properties;

    @InjectMocks
    private RetentionService retentionService;

    @Test
    void shouldClearContentOlderThanConfiguredDays() {
        var retention = new MyfeederProperties.Retention();
        retention.setFullContentDays(30);
        when(properties.getRetention()).thenReturn(retention);

        Instant before = Instant.now().minus(30, ChronoUnit.DAYS);
        retentionService.cleanupOldContent();
        Instant after = Instant.now().minus(30, ChronoUnit.DAYS);

        var captor = ArgumentCaptor.forClass(Instant.class);
        verify(articleRepository).clearContentOlderThan(captor.capture());

        // Cutoff should be approximately 30 days ago
        assertThat(captor.getValue()).isBetween(before, after.plusSeconds(1));
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "org.bartram.myfeeder.service.RetentionServiceTest"`
Expected: FAIL

**Step 3: Implement RetentionService**

```java
package org.bartram.myfeeder.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.repository.ArticleRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetentionService {

    private final ArticleRepository articleRepository;
    private final MyfeederProperties properties;

    @Scheduled(cron = "${myfeeder.retention.cleanup-cron}")
    public void cleanupOldContent() {
        int days = properties.getRetention().getFullContentDays();
        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
        articleRepository.clearContentOlderThan(cutoff);
        log.info("Retention cleanup: cleared content older than {} days", days);
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "org.bartram.myfeeder.service.RetentionServiceTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/org/bartram/myfeeder/service/RetentionService.java \
        src/test/java/org/bartram/myfeeder/service/RetentionServiceTest.java
git commit -m "feat: add RetentionService for scheduled content cleanup"
```

---

### Task 12: FeedController

**Files:**
- Create: `src/main/java/org/bartram/myfeeder/controller/FeedController.java`
- Create: `src/test/java/org/bartram/myfeeder/controller/FeedControllerTest.java`

**Step 1: Write FeedControllerTest**

```java
package org.bartram.myfeeder.controller;

import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.model.FeedType;
import org.bartram.myfeeder.service.FeedPollingService;
import org.bartram.myfeeder.service.FeedService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FeedController.class)
class FeedControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FeedService feedService;

    @MockitoBean
    private FeedPollingService feedPollingService;

    @Test
    void shouldListFeeds() throws Exception {
        var feed = new Feed();
        feed.setId(1L);
        feed.setTitle("Test Feed");
        feed.setUrl("https://example.com/feed.xml");
        feed.setFeedType(FeedType.RSS);
        feed.setCreatedAt(Instant.now());
        when(feedService.findAll()).thenReturn(List.of(feed));

        mockMvc.perform(get("/api/feeds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Test Feed"));
    }

    @Test
    void shouldSubscribeToFeed() throws Exception {
        var feed = new Feed();
        feed.setId(1L);
        feed.setTitle("New Feed");
        feed.setFeedType(FeedType.RSS);
        when(feedService.subscribe("https://example.com/feed.xml")).thenReturn(feed);

        mockMvc.perform(post("/api/feeds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/feed.xml\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("New Feed"));
    }

    @Test
    void shouldReturn404ForUnknownFeed() throws Exception {
        when(feedService.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/feeds/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldDeleteFeed() throws Exception {
        mockMvc.perform(delete("/api/feeds/1"))
                .andExpect(status().isNoContent());
        verify(feedService).delete(1L);
    }

    @Test
    void shouldTriggerManualPoll() throws Exception {
        mockMvc.perform(post("/api/feeds/1/poll"))
                .andExpect(status().isAccepted());
        verify(feedPollingService).pollFeed(1L);
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "org.bartram.myfeeder.controller.FeedControllerTest"`
Expected: FAIL

**Step 3: Create a DTO for subscribe request**

```java
package org.bartram.myfeeder.controller;

import lombok.Data;

@Data
public class SubscribeRequest {
    private String url;
}
```

Save as `src/main/java/org/bartram/myfeeder/controller/SubscribeRequest.java`.

**Step 4: Implement FeedController**

```java
package org.bartram.myfeeder.controller;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.service.FeedPollingService;
import org.bartram.myfeeder.service.FeedService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/feeds")
@RequiredArgsConstructor
public class FeedController {

    private final FeedService feedService;
    private final FeedPollingService feedPollingService;

    @GetMapping
    public List<Feed> listFeeds() {
        return feedService.findAll();
    }

    @PostMapping
    public ResponseEntity<Feed> subscribe(@RequestBody SubscribeRequest request) {
        Feed feed = feedService.subscribe(request.getUrl());
        return ResponseEntity.status(HttpStatus.CREATED).body(feed);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Feed> getFeed(@PathVariable Long id) {
        return feedService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Feed> updateFeed(@PathVariable Long id, @RequestBody Feed updates) {
        return ResponseEntity.ok(feedService.update(id, updates));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFeed(@PathVariable Long id) {
        feedService.delete(id);
    }

    @PostMapping("/{id}/poll")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void triggerPoll(@PathVariable Long id) {
        feedPollingService.pollFeed(id);
    }
}
```

**Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests "org.bartram.myfeeder.controller.FeedControllerTest"`
Expected: ALL PASS

**Step 6: Commit**

```bash
git add src/main/java/org/bartram/myfeeder/controller/FeedController.java \
        src/main/java/org/bartram/myfeeder/controller/SubscribeRequest.java \
        src/test/java/org/bartram/myfeeder/controller/FeedControllerTest.java
git commit -m "feat: add FeedController with CRUD and manual poll endpoints"
```

---

### Task 13: ArticleController

**Files:**
- Create: `src/main/java/org/bartram/myfeeder/controller/ArticleController.java`
- Create: `src/main/java/org/bartram/myfeeder/controller/ArticleStateRequest.java`
- Create: `src/main/java/org/bartram/myfeeder/controller/MarkReadRequest.java`
- Create: `src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java`

**Step 1: Write request DTOs**

`ArticleStateRequest.java`:
```java
package org.bartram.myfeeder.controller;

import lombok.Data;

@Data
public class ArticleStateRequest {
    private Boolean read;
    private Boolean starred;
}
```

`MarkReadRequest.java`:
```java
package org.bartram.myfeeder.controller;

import lombok.Data;

import java.util.List;

@Data
public class MarkReadRequest {
    private List<Long> articleIds;
    private Long feedId;
}
```

**Step 2: Write ArticleControllerTest**

```java
package org.bartram.myfeeder.controller;

import org.bartram.myfeeder.integration.RaindropService;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.service.ArticleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ArticleController.class)
class ArticleControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ArticleService articleService;
    @MockitoBean private RaindropService raindropService;

    @Test
    void shouldListArticles() throws Exception {
        var article = new Article();
        article.setId(1L);
        article.setTitle("Test Article");
        article.setFetchedAt(Instant.now());
        when(articleService.findAll()).thenReturn(List.of(article));

        mockMvc.perform(get("/api/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Test Article"));
    }

    @Test
    void shouldGetArticleById() throws Exception {
        var article = new Article();
        article.setId(1L);
        article.setTitle("Test");
        article.setContent("<p>Full content</p>");
        when(articleService.findById(1L)).thenReturn(Optional.of(article));

        mockMvc.perform(get("/api/articles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("<p>Full content</p>"));
    }

    @Test
    void shouldPatchArticleState() throws Exception {
        var article = new Article();
        article.setId(1L);
        article.setRead(true);
        when(articleService.updateState(1L, true, null)).thenReturn(article);

        mockMvc.perform(patch("/api/articles/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"read\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
    }

    @Test
    void shouldBulkMarkRead() throws Exception {
        mockMvc.perform(post("/api/articles/mark-read")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"articleIds\":[1,2,3]}"))
                .andExpect(status().isNoContent());

        verify(articleService).markRead(List.of(1L, 2L, 3L), null);
    }

    @Test
    void shouldSaveToRaindrop() throws Exception {
        var article = new Article();
        article.setId(1L);
        article.setTitle("Test");
        article.setUrl("https://example.com");
        when(articleService.findById(1L)).thenReturn(Optional.of(article));

        mockMvc.perform(post("/api/articles/1/raindrop"))
                .andExpect(status().isOk());

        verify(raindropService).saveToRaindrop(article);
    }
}
```

**Step 3: Run tests to verify they fail**

Run: `./gradlew test --tests "org.bartram.myfeeder.controller.ArticleControllerTest"`
Expected: FAIL

**Step 4: Implement ArticleController**

```java
package org.bartram.myfeeder.controller;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.integration.RaindropService;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.service.ArticleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;
    private final RaindropService raindropService;

    @GetMapping
    public List<Article> listArticles(
            @RequestParam(required = false) Long feedId,
            @RequestParam(required = false) Boolean read,
            @RequestParam(required = false) Boolean starred) {

        if (feedId != null) {
            return articleService.findByFeedId(feedId);
        }
        if (Boolean.TRUE.equals(starred)) {
            return articleService.findStarred();
        }
        if (Boolean.FALSE.equals(read)) {
            return articleService.findUnread();
        }
        return articleService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Article> getArticle(@PathVariable Long id) {
        return articleService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Article> updateState(@PathVariable Long id, @RequestBody ArticleStateRequest request) {
        Article updated = articleService.updateState(id, request.getRead(), request.getStarred());
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/mark-read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@RequestBody MarkReadRequest request) {
        articleService.markRead(request.getArticleIds(), request.getFeedId());
    }

    @PostMapping("/{id}/raindrop")
    public ResponseEntity<Void> saveToRaindrop(@PathVariable Long id) {
        Article article = articleService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Article not found: " + id));
        raindropService.saveToRaindrop(article);
        return ResponseEntity.ok().build();
    }
}
```

**Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests "org.bartram.myfeeder.controller.ArticleControllerTest"`
Expected: ALL PASS

**Step 6: Commit**

```bash
git add src/main/java/org/bartram/myfeeder/controller/ArticleController.java \
        src/main/java/org/bartram/myfeeder/controller/ArticleStateRequest.java \
        src/main/java/org/bartram/myfeeder/controller/MarkReadRequest.java \
        src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java
git commit -m "feat: add ArticleController with state, bulk mark-read, and Raindrop save"
```

---

### Task 14: IntegrationConfigController

**Files:**
- Create: `src/main/java/org/bartram/myfeeder/controller/IntegrationConfigController.java`
- Create: `src/test/java/org/bartram/myfeeder/controller/IntegrationConfigControllerTest.java`

**Step 1: Write IntegrationConfigControllerTest**

```java
package org.bartram.myfeeder.controller;

import org.bartram.myfeeder.model.IntegrationConfig;
import org.bartram.myfeeder.model.IntegrationType;
import org.bartram.myfeeder.repository.IntegrationConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IntegrationConfigController.class)
class IntegrationConfigControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private IntegrationConfigRepository configRepository;

    @Test
    void shouldListIntegrations() throws Exception {
        var config = new IntegrationConfig();
        config.setType(IntegrationType.RAINDROP);
        config.setEnabled(true);
        when(configRepository.findAll()).thenReturn(List.of(config));

        mockMvc.perform(get("/api/integrations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("RAINDROP"));
    }

    @Test
    void shouldUpsertRaindropConfig() throws Exception {
        when(configRepository.findByType(IntegrationType.RAINDROP)).thenReturn(Optional.empty());
        when(configRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        mockMvc.perform(put("/api/integrations/raindrop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apiToken\":\"my-token\",\"collectionId\":12345}"))
                .andExpect(status().isOk());

        verify(configRepository).save(any(IntegrationConfig.class));
    }

    @Test
    void shouldDeleteRaindropConfig() throws Exception {
        mockMvc.perform(delete("/api/integrations/raindrop"))
                .andExpect(status().isNoContent());

        verify(configRepository).deleteByType(IntegrationType.RAINDROP);
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "org.bartram.myfeeder.controller.IntegrationConfigControllerTest"`
Expected: FAIL

**Step 3: Implement IntegrationConfigController**

```java
package org.bartram.myfeeder.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.integration.RaindropConfig;
import org.bartram.myfeeder.model.IntegrationConfig;
import org.bartram.myfeeder.model.IntegrationType;
import org.bartram.myfeeder.repository.IntegrationConfigRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/integrations")
@RequiredArgsConstructor
public class IntegrationConfigController {

    private final IntegrationConfigRepository configRepository;
    private final ObjectMapper objectMapper;

    @GetMapping
    public List<IntegrationConfig> listIntegrations() {
        return configRepository.findAll();
    }

    @PutMapping("/raindrop")
    public IntegrationConfig upsertRaindrop(@RequestBody RaindropConfig raindropConfig) {
        try {
            String configJson = objectMapper.writeValueAsString(raindropConfig);

            IntegrationConfig config = configRepository.findByType(IntegrationType.RAINDROP)
                    .orElseGet(() -> {
                        var c = new IntegrationConfig();
                        c.setType(IntegrationType.RAINDROP);
                        c.setEnabled(true);
                        return c;
                    });

            config.setConfig(configJson);
            return configRepository.save(config);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize Raindrop config", e);
        }
    }

    @DeleteMapping("/raindrop")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRaindrop() {
        configRepository.deleteByType(IntegrationType.RAINDROP);
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "org.bartram.myfeeder.controller.IntegrationConfigControllerTest"`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add src/main/java/org/bartram/myfeeder/controller/IntegrationConfigController.java \
        src/test/java/org/bartram/myfeeder/controller/IntegrationConfigControllerTest.java
git commit -m "feat: add IntegrationConfigController with Raindrop upsert"
```

---

### Task 15: FeedPollingScheduler

**Files:**
- Create: `src/main/java/org/bartram/myfeeder/scheduler/FeedPollingScheduler.java`
- Create: `src/test/java/org/bartram/myfeeder/scheduler/FeedPollingSchedulerTest.java`

**Step 1: Write FeedPollingSchedulerTest**

```java
package org.bartram.myfeeder.scheduler;

import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.model.FeedType;
import org.bartram.myfeeder.repository.FeedRepository;
import org.bartram.myfeeder.service.FeedPollingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedPollingSchedulerTest {

    @Mock private FeedRepository feedRepository;
    @Mock private FeedPollingService feedPollingService;
    @Mock private TaskScheduler taskScheduler;
    @Mock private MyfeederProperties properties;
    @Mock private ScheduledFuture<?> scheduledFuture;

    private FeedPollingScheduler scheduler;

    @BeforeEach
    void setUp() {
        var polling = new MyfeederProperties.Polling();
        polling.setMaxIntervalMinutes(1440);
        polling.setBackoffThreshold(5);
        when(properties.getPolling()).thenReturn(polling);

        scheduler = new FeedPollingScheduler(feedRepository, feedPollingService, taskScheduler, properties);
    }

    @Test
    void shouldRegisterAllFeedsOnStartup() {
        var feed = new Feed();
        feed.setId(1L);
        feed.setPollIntervalMinutes(15);
        feed.setErrorCount(0);
        when(feedRepository.findAll()).thenReturn(List.of(feed));
        when(taskScheduler.scheduleAtFixedRate(any(Runnable.class), any(Duration.class)))
                .thenReturn(scheduledFuture);

        scheduler.onStartup();

        verify(taskScheduler).scheduleAtFixedRate(any(Runnable.class), eq(Duration.ofMinutes(15)));
    }

    @Test
    void shouldComputeBackoffInterval() {
        var feed = new Feed();
        feed.setId(2L);
        feed.setPollIntervalMinutes(15);
        feed.setErrorCount(10); // 10 errors, threshold 5 -> 2^(10/5) = 4x
        when(taskScheduler.scheduleAtFixedRate(any(Runnable.class), any(Duration.class)))
                .thenReturn(scheduledFuture);

        scheduler.registerFeed(feed);

        // 15 * 4 = 60 minutes
        verify(taskScheduler).scheduleAtFixedRate(any(Runnable.class), eq(Duration.ofMinutes(60)));
    }

    @Test
    void shouldCancelExistingTaskBeforeReRegistering() {
        var feed = new Feed();
        feed.setId(3L);
        feed.setPollIntervalMinutes(15);
        feed.setErrorCount(0);
        when(taskScheduler.scheduleAtFixedRate(any(Runnable.class), any(Duration.class)))
                .thenReturn(scheduledFuture);

        scheduler.registerFeed(feed);
        scheduler.registerFeed(feed); // re-register

        verify(scheduledFuture).cancel(false);
    }
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "org.bartram.myfeeder.scheduler.FeedPollingSchedulerTest"`
Expected: FAIL

**Step 3: Implement FeedPollingScheduler**

```java
package org.bartram.myfeeder.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.repository.FeedRepository;
import org.bartram.myfeeder.service.FeedPollingService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeedPollingScheduler {

    private final FeedRepository feedRepository;
    private final FeedPollingService feedPollingService;
    private final TaskScheduler taskScheduler;
    private final MyfeederProperties properties;

    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        feedRepository.findAll().forEach(this::registerFeed);
        log.info("Registered polling tasks for {} feeds", scheduledTasks.size());
    }

    public void registerFeed(Feed feed) {
        cancelFeed(feed.getId());

        Duration interval = computeEffectiveInterval(feed);
        ScheduledFuture<?> future = taskScheduler.scheduleAtFixedRate(
                () -> feedPollingService.pollFeed(feed.getId()),
                interval
        );

        scheduledTasks.put(feed.getId(), future);
        log.info("Scheduled polling for feed '{}' every {} minutes", feed.getTitle(), interval.toMinutes());
    }

    public void cancelFeed(Long feedId) {
        ScheduledFuture<?> existing = scheduledTasks.remove(feedId);
        if (existing != null) {
            existing.cancel(false);
        }
    }

    private Duration computeEffectiveInterval(Feed feed) {
        int threshold = properties.getPolling().getBackoffThreshold();
        int maxMinutes = properties.getPolling().getMaxIntervalMinutes();

        if (feed.getErrorCount() >= threshold) {
            int multiplier = (int) Math.pow(2, feed.getErrorCount() / threshold);
            int backoffMinutes = Math.min(feed.getPollIntervalMinutes() * multiplier, maxMinutes);
            return Duration.ofMinutes(backoffMinutes);
        }

        return Duration.ofMinutes(feed.getPollIntervalMinutes());
    }
}
```

**Step 4: Enable scheduling on the application**

Add `@EnableScheduling` to `MyfeederApplication.java`:

```java
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class MyfeederApplication {
```

Add the import: `import org.springframework.scheduling.annotation.EnableScheduling;`

**Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests "org.bartram.myfeeder.scheduler.FeedPollingSchedulerTest"`
Expected: ALL PASS

**Step 6: Commit**

```bash
git add src/main/java/org/bartram/myfeeder/scheduler/FeedPollingScheduler.java \
        src/test/java/org/bartram/myfeeder/scheduler/FeedPollingSchedulerTest.java \
        src/main/java/org/bartram/myfeeder/MyfeederApplication.java
git commit -m "feat: add FeedPollingScheduler with dynamic registration and backoff"
```

---

### Task 16: Wire Scheduler into FeedService

**Files:**
- Modify: `src/main/java/org/bartram/myfeeder/service/FeedService.java`
- Modify: `src/test/java/org/bartram/myfeeder/service/FeedServiceTest.java`

**Step 1: Update FeedServiceTest to verify scheduler interactions**

Add these tests to `FeedServiceTest`:

```java
@Mock
private FeedPollingScheduler feedPollingScheduler;

@Test
void shouldRegisterSchedulerOnSubscribe() {
    // Setup RestClient mock chain for subscribe
    // ... (mock RestClient to return feed content)
    // After subscribe, verify:
    verify(feedPollingScheduler).registerFeed(any(Feed.class));
}

@Test
void shouldCancelSchedulerOnDelete() {
    feedService.delete(1L);
    verify(feedPollingScheduler).cancelFeed(1L);
}

@Test
void shouldReRegisterSchedulerOnUpdate() {
    var feed = new Feed();
    feed.setId(1L);
    feed.setPollIntervalMinutes(15);
    when(feedRepository.findById(1L)).thenReturn(Optional.of(feed));
    when(feedRepository.save(any())).thenAnswer(i -> i.getArgument(0));

    var updates = new Feed();
    updates.setPollIntervalMinutes(30);
    feedService.update(1L, updates);

    verify(feedPollingScheduler).registerFeed(any(Feed.class));
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "org.bartram.myfeeder.service.FeedServiceTest"`
Expected: FAIL

**Step 3: Update FeedService to wire in scheduler**

Add `FeedPollingScheduler` as a dependency and call it in `subscribe()`, `update()`, and `delete()`:

```java
@Service
@RequiredArgsConstructor
public class FeedService {

    private final FeedRepository feedRepository;
    private final FeedParser feedParser;
    private final RestClient.Builder restClientBuilder;
    private final MyfeederProperties properties;
    private final FeedPollingScheduler feedPollingScheduler;

    // ... existing methods ...

    public Feed subscribe(String feedUrl) {
        // ... existing logic ...
        Feed saved = feedRepository.save(feed);
        feedPollingScheduler.registerFeed(saved);
        return saved;
    }

    public Feed update(Long id, Feed updates) {
        // ... existing logic ...
        Feed saved = feedRepository.save(feed);
        feedPollingScheduler.registerFeed(saved);
        return saved;
    }

    public void delete(Long id) {
        feedPollingScheduler.cancelFeed(id);
        feedRepository.deleteById(id);
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew test --tests "org.bartram.myfeeder.service.FeedServiceTest"`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add src/main/java/org/bartram/myfeeder/service/FeedService.java \
        src/test/java/org/bartram/myfeeder/service/FeedServiceTest.java
git commit -m "feat: wire FeedPollingScheduler into FeedService CRUD operations"
```

---

### Task 17: Full Integration Test

**Files:**
- Modify: `src/test/java/org/bartram/myfeeder/MyfeederApplicationTests.java`

**Step 1: Expand the context load test to verify wiring**

```java
package org.bartram.myfeeder;

import org.bartram.myfeeder.controller.FeedController;
import org.bartram.myfeeder.controller.ArticleController;
import org.bartram.myfeeder.controller.IntegrationConfigController;
import org.bartram.myfeeder.scheduler.FeedPollingScheduler;
import org.bartram.myfeeder.service.RetentionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MyfeederApplicationTests {

    @Autowired private FeedController feedController;
    @Autowired private ArticleController articleController;
    @Autowired private IntegrationConfigController integrationConfigController;
    @Autowired private FeedPollingScheduler feedPollingScheduler;
    @Autowired private RetentionService retentionService;

    @Test
    void contextLoads() {
        assertThat(feedController).isNotNull();
        assertThat(articleController).isNotNull();
        assertThat(integrationConfigController).isNotNull();
        assertThat(feedPollingScheduler).isNotNull();
        assertThat(retentionService).isNotNull();
    }
}
```

**Step 2: Run the full test suite**

Run: `./gradlew test`
Expected: ALL PASS

**Step 3: Commit**

```bash
git add src/test/java/org/bartram/myfeeder/MyfeederApplicationTests.java
git commit -m "test: expand integration test to verify full application wiring"
```

---

### Task 18: Update CLAUDE.md

**Files:**
- Modify: `CLAUDE.md`

**Step 1: Update CLAUDE.md to reflect the implemented architecture**

Add the package structure, key components, and any conventions established during implementation to `CLAUDE.md`.

**Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: update CLAUDE.md with implemented architecture"
```
