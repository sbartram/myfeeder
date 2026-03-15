# OPML Import/Export Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add OPML import and export support so users can bulk-import/export feed subscriptions.

**Architecture:** New `OpmlService` parses/generates OPML XML using Java's built-in DOM parser. `OpmlImportService` orchestrates the import by creating/updating feeds and folders. `OpmlController` exposes REST endpoints for file upload (import) and download (export). Frontend adds buttons to the feed panel footer.

**Tech Stack:** Java 21 DOM/SAX XML, Spring Boot 4.0.3, Spring Data JDBC, React 19, TanStack Query

**Spec:** `docs/superpowers/specs/2026-03-15-opml-import-export-design.md`

---

## Chunk 1: Backend — OPML Parsing and Generation

### Task 1: OpmlFeed Record and OpmlParseException

**Files:**
- Create: `src/main/java/org/bartram/myfeeder/parser/OpmlFeed.java`
- Create: `src/main/java/org/bartram/myfeeder/parser/OpmlParseException.java`

- [ ] **Step 1: Create OpmlFeed record**

```java
package org.bartram.myfeeder.parser;

public record OpmlFeed(String title, String xmlUrl, String htmlUrl, String folderName) {}
```

- [ ] **Step 2: Create OpmlParseException**

Follow the pattern from `src/main/java/org/bartram/myfeeder/parser/FeedParseException.java`. Add a single-arg constructor too.

```java
package org.bartram.myfeeder.parser;

public class OpmlParseException extends RuntimeException {
    public OpmlParseException(String message) {
        super(message);
    }

    public OpmlParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/org/bartram/myfeeder/parser/OpmlFeed.java src/main/java/org/bartram/myfeeder/parser/OpmlParseException.java
git commit -m "feat(opml): add OpmlFeed record and OpmlParseException"
```

### Task 2: OPML Parsing — Test Fixtures

**Files:**
- Create: `src/test/resources/opml/standard.opml`
- Create: `src/test/resources/opml/flat.opml`
- Create: `src/test/resources/opml/nested.opml`
- Create: `src/test/resources/opml/empty.opml`

- [ ] **Step 1: Create standard.opml** — OPML with two folders and uncategorized feeds

```xml
<?xml version="1.0" encoding="UTF-8"?>
<opml version="2.0">
  <head><title>Test Subscriptions</title></head>
  <body>
    <outline text="Tech" title="Tech">
      <outline type="rss" text="Ars Technica" title="Ars Technica"
               xmlUrl="https://feeds.arstechnica.com/arstechnica/index"
               htmlUrl="https://arstechnica.com"/>
      <outline type="rss" text="Hacker News" title="Hacker News"
               xmlUrl="https://news.ycombinator.com/rss"
               htmlUrl="https://news.ycombinator.com"/>
    </outline>
    <outline text="News" title="News">
      <outline type="rss" text="BBC News" title="BBC News"
               xmlUrl="https://feeds.bbci.co.uk/news/rss.xml"
               htmlUrl="https://www.bbc.co.uk/news"/>
    </outline>
    <outline type="rss" text="xkcd" title="xkcd"
             xmlUrl="https://xkcd.com/rss.xml"
             htmlUrl="https://xkcd.com"/>
  </body>
</opml>
```

- [ ] **Step 2: Create flat.opml** — All feeds at top level, no folders

```xml
<?xml version="1.0" encoding="UTF-8"?>
<opml version="2.0">
  <head><title>Flat Feeds</title></head>
  <body>
    <outline type="rss" text="Feed One" title="Feed One"
             xmlUrl="https://example.com/feed1.xml"
             htmlUrl="https://example.com/1"/>
    <outline type="rss" text="Feed Two" title="Feed Two"
             xmlUrl="https://example.com/feed2.xml"
             htmlUrl="https://example.com/2"/>
  </body>
</opml>
```

- [ ] **Step 3: Create nested.opml** — Deeply nested outlines (3 levels)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<opml version="2.0">
  <head><title>Nested Feeds</title></head>
  <body>
    <outline text="Tech">
      <outline text="Programming">
        <outline type="rss" text="Deep Feed" title="Deep Feed"
                 xmlUrl="https://example.com/deep.xml"
                 htmlUrl="https://example.com/deep"/>
      </outline>
      <outline type="rss" text="Shallow Feed" title="Shallow Feed"
               xmlUrl="https://example.com/shallow.xml"
               htmlUrl="https://example.com/shallow"/>
    </outline>
  </body>
</opml>
```

- [ ] **Step 4: Create empty.opml** — Valid OPML with no feeds

```xml
<?xml version="1.0" encoding="UTF-8"?>
<opml version="2.0">
  <head><title>Empty</title></head>
  <body/>
</opml>
```

- [ ] **Step 5: Commit**

```bash
git add src/test/resources/opml/
git commit -m "test(opml): add OPML test fixture files"
```

### Task 3: OpmlService — Parse Tests

**Files:**
- Create: `src/test/java/org/bartram/myfeeder/service/OpmlServiceTest.java`

- [ ] **Step 1: Write parsing tests**

Follow the test pattern from `src/test/java/org/bartram/myfeeder/service/ArticleServiceTest.java` — plain JUnit 5, AssertJ assertions. No Mockito needed since `OpmlService` is stateless.

```java
package org.bartram.myfeeder.service;

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
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "*OpmlServiceTest*"`
Expected: Compilation failure — `OpmlService` does not exist yet.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/org/bartram/myfeeder/service/OpmlServiceTest.java
git commit -m "test(opml): add OpmlService parsing tests (red)"
```

### Task 4: OpmlService — Parse Implementation

**Files:**
- Create: `src/main/java/org/bartram/myfeeder/service/OpmlService.java`

- [ ] **Step 1: Implement OpmlService.parseOpml**

```java
package org.bartram.myfeeder.service;

import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.model.Folder;
import org.bartram.myfeeder.parser.OpmlFeed;
import org.bartram.myfeeder.parser.OpmlParseException;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.InputStream;
import java.io.StringWriter;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OpmlService {

    public List<OpmlFeed> parseOpml(InputStream inputStream) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            Document doc = factory.newDocumentBuilder().parse(inputStream);
            Element body = findBodyElement(doc);

            List<OpmlFeed> feeds = new ArrayList<>();
            NodeList children = body.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i) instanceof Element outline) {
                    if (outline.hasAttribute("xmlUrl")) {
                        feeds.add(outlineToFeed(outline, null));
                    } else {
                        collectFeedsRecursively(outline, outline.getAttribute("text"), feeds);
                    }
                }
            }
            return feeds;
        } catch (OpmlParseException e) {
            throw e;
        } catch (Exception e) {
            throw new OpmlParseException("Failed to parse OPML: " + e.getMessage(), e);
        }
    }

    private Element findBodyElement(Document doc) {
        NodeList bodyList = doc.getElementsByTagName("body");
        if (bodyList.getLength() == 0) {
            throw new OpmlParseException("Invalid OPML: missing <body> element");
        }
        return (Element) bodyList.item(0);
    }

    private void collectFeedsRecursively(Element parent, String folderName, List<OpmlFeed> feeds) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element outline) {
                if (outline.hasAttribute("xmlUrl")) {
                    feeds.add(outlineToFeed(outline, folderName));
                } else {
                    collectFeedsRecursively(outline, folderName, feeds);
                }
            }
        }
    }

    private OpmlFeed outlineToFeed(Element outline, String folderName) {
        String title = outline.getAttribute("title");
        if (title.isEmpty()) {
            title = outline.getAttribute("text");
        }
        return new OpmlFeed(
                title,
                outline.getAttribute("xmlUrl"),
                outline.getAttribute("htmlUrl"),
                folderName
        );
    }
}
```

Note: The `generateOpml` method will be added in Task 6. Keep the class focused on parsing for now.

- [ ] **Step 2: Run tests to verify they pass**

Run: `./gradlew test --tests "*OpmlServiceTest*"`
Expected: All 6 tests PASS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/org/bartram/myfeeder/service/OpmlService.java
git commit -m "feat(opml): implement OPML parsing with XXE protection"
```

### Task 5: OpmlService — Export Tests

**Files:**
- Modify: `src/test/java/org/bartram/myfeeder/service/OpmlServiceTest.java`

- [ ] **Step 1: Add export tests to OpmlServiceTest**

Append these tests to the existing `OpmlServiceTest` class:

```java
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
```

Add these imports at the top of the file:

```java
import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.model.Folder;
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "*OpmlServiceTest*"`
Expected: Compilation failure — `generateOpml` method does not exist yet.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/org/bartram/myfeeder/service/OpmlServiceTest.java
git commit -m "test(opml): add OpmlService export tests (red)"
```

### Task 6: OpmlService — Export Implementation

**Files:**
- Modify: `src/main/java/org/bartram/myfeeder/service/OpmlService.java`

- [ ] **Step 1: Add generateOpml method to OpmlService**

Add this method to the existing `OpmlService` class:

```java
public String generateOpml(List<Feed> feeds, List<Folder> folders) {
    try {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        Document doc = factory.newDocumentBuilder().newDocument();

        Element opml = doc.createElement("opml");
        opml.setAttribute("version", "2.0");
        doc.appendChild(opml);

        Element head = doc.createElement("head");
        opml.appendChild(head);
        Element title = doc.createElement("title");
        title.setTextContent("myfeeder subscriptions");
        head.appendChild(title);
        Element dateCreated = doc.createElement("dateCreated");
        dateCreated.setTextContent(ZonedDateTime.now().format(
                DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)));
        head.appendChild(dateCreated);

        Element body = doc.createElement("body");
        opml.appendChild(body);

        Map<Long, Folder> folderMap = folders.stream()
                .collect(Collectors.toMap(Folder::getId, f -> f));
        Map<Long, List<Feed>> feedsByFolder = feeds.stream()
                .filter(f -> f.getFolderId() != null)
                .collect(Collectors.groupingBy(Feed::getFolderId));

        // Folder outlines with their feeds
        for (Folder folder : folders) {
            List<Feed> folderFeeds = feedsByFolder.getOrDefault(folder.getId(), List.of());
            if (folderFeeds.isEmpty()) continue;

            Element folderOutline = doc.createElement("outline");
            folderOutline.setAttribute("text", folder.getName());
            folderOutline.setAttribute("title", folder.getName());
            body.appendChild(folderOutline);

            for (Feed feed : folderFeeds) {
                folderOutline.appendChild(createFeedOutline(doc, feed));
            }
        }

        // Uncategorized feeds at top level
        feeds.stream()
                .filter(f -> f.getFolderId() == null)
                .forEach(feed -> body.appendChild(createFeedOutline(doc, feed)));

        return transformToString(doc);
    } catch (Exception e) {
        throw new OpmlParseException("Failed to generate OPML: " + e.getMessage(), e);
    }
}

private Element createFeedOutline(Document doc, Feed feed) {
    Element outline = doc.createElement("outline");
    outline.setAttribute("type", "rss");
    outline.setAttribute("text", feed.getTitle() != null ? feed.getTitle() : "");
    outline.setAttribute("title", feed.getTitle() != null ? feed.getTitle() : "");
    outline.setAttribute("xmlUrl", feed.getUrl());
    if (feed.getSiteUrl() != null) {
        outline.setAttribute("htmlUrl", feed.getSiteUrl());
    }
    return outline;
}

private String transformToString(Document doc) throws Exception {
    TransformerFactory tf = TransformerFactory.newInstance();
    tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    Transformer transformer = tf.newTransformer();
    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

    StringWriter writer = new StringWriter();
    transformer.transform(new DOMSource(doc), new StreamResult(writer));
    return writer.toString();
}
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `./gradlew test --tests "*OpmlServiceTest*"`
Expected: All 8 tests PASS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/org/bartram/myfeeder/service/OpmlService.java
git commit -m "feat(opml): implement OPML export generation"
```

## Chunk 2: Backend — Import Service and Controller

### Task 7: OpmlImportResult Record

**Files:**
- Create: `src/main/java/org/bartram/myfeeder/service/OpmlImportResult.java`

- [ ] **Step 1: Create OpmlImportResult**

```java
package org.bartram.myfeeder.service;

public record OpmlImportResult(int created, int updated, int total) {}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/org/bartram/myfeeder/service/OpmlImportResult.java
git commit -m "feat(opml): add OpmlImportResult record"
```

### Task 8: OpmlImportService — Tests

**Files:**
- Create: `src/test/java/org/bartram/myfeeder/service/OpmlImportServiceTest.java`

- [ ] **Step 1: Write import service tests**

Follow the Mockito pattern from `src/test/java/org/bartram/myfeeder/service/ArticleServiceTest.java`.

```java
package org.bartram.myfeeder.service;

import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.model.Folder;
import org.bartram.myfeeder.parser.OpmlFeed;
import org.bartram.myfeeder.repository.FeedRepository;
import org.bartram.myfeeder.repository.FolderRepository;
import org.bartram.myfeeder.scheduler.FeedPollingScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpmlImportServiceTest {

    @Mock private OpmlService opmlService;
    @Mock private FeedRepository feedRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private FolderService folderService;
    @Mock private FeedPollingScheduler feedPollingScheduler;

    private OpmlImportService importService;

    @BeforeEach
    void setUp() {
        // Activate transaction synchronization so registerSynchronization() works in unit tests
        TransactionSynchronizationManager.initSynchronization();
        var properties = new MyfeederProperties();
        properties.getPolling().setDefaultIntervalMinutes(15);
        importService = new OpmlImportService(
                opmlService, feedRepository, folderRepository,
                folderService, feedPollingScheduler, properties);
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    /** Helper to simulate transaction commit — triggers afterCommit callbacks. */
    private void simulateCommit() {
        TransactionSynchronizationUtils.triggerAfterCommit();
    }

    @Test
    void shouldCreateNewFeedsFromOpml() {
        var opmlFeed = new OpmlFeed("New Feed", "https://example.com/feed.xml", "https://example.com", null);
        when(opmlService.parseOpml(any(InputStream.class))).thenReturn(List.of(opmlFeed));
        when(feedRepository.findAll()).thenReturn(List.of());
        when(folderRepository.findAll()).thenReturn(List.of());
        when(feedRepository.save(any(Feed.class))).thenAnswer(i -> {
            Feed f = i.getArgument(0);
            f.setId(1L);
            return f;
        });

        OpmlImportResult result = importService.importOpml(InputStream.nullInputStream());

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(0);
        assertThat(result.total()).isEqualTo(1);

        ArgumentCaptor<Feed> captor = ArgumentCaptor.forClass(Feed.class);
        verify(feedRepository).save(captor.capture());
        Feed saved = captor.getValue();
        assertThat(saved.getUrl()).isEqualTo("https://example.com/feed.xml");
        assertThat(saved.getTitle()).isEqualTo("New Feed");
        assertThat(saved.getSiteUrl()).isEqualTo("https://example.com");
        assertThat(saved.getPollIntervalMinutes()).isEqualTo(15);

        // Scheduler registration happens after commit
        verify(feedPollingScheduler, never()).registerFeed(any());
        simulateCommit();
        verify(feedPollingScheduler).registerFeed(saved);
    }

    @Test
    void shouldUpdateExistingFeedByUrl() {
        var existing = new Feed();
        existing.setId(1L);
        existing.setUrl("https://example.com/feed.xml");
        existing.setTitle("Old Title");

        var opmlFeed = new OpmlFeed("New Title", "https://example.com/feed.xml", "https://example.com", null);
        when(opmlService.parseOpml(any(InputStream.class))).thenReturn(List.of(opmlFeed));
        when(feedRepository.findAll()).thenReturn(List.of(existing));
        when(folderRepository.findAll()).thenReturn(List.of());
        when(feedRepository.save(any(Feed.class))).thenAnswer(i -> i.getArgument(0));

        OpmlImportResult result = importService.importOpml(InputStream.nullInputStream());
        simulateCommit();

        assertThat(result.created()).isEqualTo(0);
        assertThat(result.updated()).isEqualTo(1);

        verify(feedRepository).save(existing);
        assertThat(existing.getTitle()).isEqualTo("New Title");
        // Should NOT register with scheduler for existing feeds
        verify(feedPollingScheduler, never()).registerFeed(any());
    }

    @Test
    void shouldCreateFolderWhenNotExisting() {
        var opmlFeed = new OpmlFeed("Feed", "https://example.com/feed.xml", null, "Tech");
        var newFolder = new Folder();
        newFolder.setId(1L);
        newFolder.setName("Tech");

        when(opmlService.parseOpml(any(InputStream.class))).thenReturn(List.of(opmlFeed));
        when(feedRepository.findAll()).thenReturn(List.of());
        when(folderRepository.findAll()).thenReturn(List.of());
        when(folderService.create("Tech")).thenReturn(newFolder);
        when(feedRepository.save(any(Feed.class))).thenAnswer(i -> {
            Feed f = i.getArgument(0);
            f.setId(1L);
            return f;
        });

        importService.importOpml(InputStream.nullInputStream());

        verify(folderService).create("Tech");
        ArgumentCaptor<Feed> captor = ArgumentCaptor.forClass(Feed.class);
        verify(feedRepository).save(captor.capture());
        assertThat(captor.getValue().getFolderId()).isEqualTo(1L);
    }

    @Test
    void shouldReuseFolderByNameCaseInsensitive() {
        var existingFolder = new Folder();
        existingFolder.setId(5L);
        existingFolder.setName("tech");

        var opmlFeed = new OpmlFeed("Feed", "https://example.com/feed.xml", null, "Tech");
        when(opmlService.parseOpml(any(InputStream.class))).thenReturn(List.of(opmlFeed));
        when(feedRepository.findAll()).thenReturn(List.of());
        when(folderRepository.findAll()).thenReturn(List.of(existingFolder));
        when(feedRepository.save(any(Feed.class))).thenAnswer(i -> {
            Feed f = i.getArgument(0);
            f.setId(1L);
            return f;
        });

        importService.importOpml(InputStream.nullInputStream());

        verify(folderService, never()).create(any());
        ArgumentCaptor<Feed> captor = ArgumentCaptor.forClass(Feed.class);
        verify(feedRepository).save(captor.capture());
        assertThat(captor.getValue().getFolderId()).isEqualTo(5L);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "*OpmlImportServiceTest*"`
Expected: Compilation failure — `OpmlImportService` does not exist yet.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/org/bartram/myfeeder/service/OpmlImportServiceTest.java
git commit -m "test(opml): add OpmlImportService tests (red)"
```

### Task 9: OpmlImportService — Implementation

**Files:**
- Create: `src/main/java/org/bartram/myfeeder/service/OpmlImportService.java`

- [ ] **Step 1: Implement OpmlImportService**

```java
package org.bartram.myfeeder.service;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.model.Folder;
import org.bartram.myfeeder.parser.OpmlFeed;
import org.bartram.myfeeder.repository.FeedRepository;
import org.bartram.myfeeder.repository.FolderRepository;
import org.bartram.myfeeder.scheduler.FeedPollingScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OpmlImportService {

    private final OpmlService opmlService;
    private final FeedRepository feedRepository;
    private final FolderRepository folderRepository;
    private final FolderService folderService;
    private final FeedPollingScheduler feedPollingScheduler;
    private final MyfeederProperties properties;

    @Transactional
    public OpmlImportResult importOpml(InputStream inputStream) {
        List<OpmlFeed> opmlFeeds = opmlService.parseOpml(inputStream);

        Map<String, Feed> existingByUrl = feedRepository.findAll().stream()
                .collect(Collectors.toMap(Feed::getUrl, Function.identity()));
        Map<String, Folder> existingFolders = folderRepository.findAll().stream()
                .collect(Collectors.toMap(f -> f.getName().toLowerCase(), Function.identity()));

        int created = 0;
        int updated = 0;
        List<Feed> newFeedsToRegister = new ArrayList<>();

        for (OpmlFeed opmlFeed : opmlFeeds) {
            Long folderId = resolveFolder(opmlFeed.folderName(), existingFolders);
            Feed existing = existingByUrl.get(opmlFeed.xmlUrl());

            if (existing != null) {
                existing.setTitle(opmlFeed.title());
                existing.setFolderId(folderId);
                feedRepository.save(existing);
                updated++;
            } else {
                Feed feed = new Feed();
                feed.setUrl(opmlFeed.xmlUrl());
                feed.setTitle(opmlFeed.title());
                feed.setSiteUrl(opmlFeed.htmlUrl() != null && !opmlFeed.htmlUrl().isEmpty()
                        ? opmlFeed.htmlUrl() : null);
                feed.setFolderId(folderId);
                feed.setPollIntervalMinutes(properties.getPolling().getDefaultIntervalMinutes());
                feed.setCreatedAt(Instant.now());
                Feed saved = feedRepository.save(feed);
                newFeedsToRegister.add(saved);
                created++;
            }
        }

        // Register new feeds with scheduler after transaction commits
        if (!newFeedsToRegister.isEmpty()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    newFeedsToRegister.forEach(feedPollingScheduler::registerFeed);
                }
            });
        }

        return new OpmlImportResult(created, updated, opmlFeeds.size());
    }

    private Long resolveFolder(String folderName, Map<String, Folder> existingFolders) {
        if (folderName == null || folderName.isBlank()) {
            return null;
        }

        Folder existing = existingFolders.get(folderName.toLowerCase());
        if (existing != null) {
            return existing.getId();
        }

        Folder created = folderService.create(folderName);
        existingFolders.put(folderName.toLowerCase(), created);
        return created.getId();
    }
}
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `./gradlew test --tests "*OpmlImportServiceTest*"`
Expected: All 4 tests PASS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/org/bartram/myfeeder/service/OpmlImportService.java
git commit -m "feat(opml): implement import service with transactional safety"
```

### Task 10: OpmlController — Tests

**Files:**
- Create: `src/test/java/org/bartram/myfeeder/controller/OpmlControllerTest.java`

- [ ] **Step 1: Write controller tests**

Follow the pattern from `src/test/java/org/bartram/myfeeder/controller/ArticleControllerTest.java` — `@WebMvcTest`, `MockMvc`, `@MockitoBean`.

```java
package org.bartram.myfeeder.controller;

import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.model.Folder;
import org.bartram.myfeeder.parser.OpmlParseException;
import org.bartram.myfeeder.service.FeedService;
import org.bartram.myfeeder.service.FolderService;
import org.bartram.myfeeder.service.OpmlImportResult;
import org.bartram.myfeeder.service.OpmlImportService;
import org.bartram.myfeeder.service.OpmlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OpmlController.class)
class OpmlControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private OpmlImportService opmlImportService;
    @MockitoBean private OpmlService opmlService;
    @MockitoBean private FeedService feedService;
    @MockitoBean private FolderService folderService;

    @Test
    void shouldImportOpmlFile() throws Exception {
        var file = new MockMultipartFile("file", "feeds.opml",
                "application/xml", "<opml/>".getBytes());
        when(opmlImportService.importOpml(any(InputStream.class)))
                .thenReturn(new OpmlImportResult(3, 1, 4));

        mockMvc.perform(multipart("/api/opml/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(3))
                .andExpect(jsonPath("$.updated").value(1))
                .andExpect(jsonPath("$.total").value(4));
    }

    @Test
    void shouldReturn400ForInvalidOpml() throws Exception {
        var file = new MockMultipartFile("file", "bad.opml",
                "application/xml", "not xml".getBytes());
        when(opmlImportService.importOpml(any(InputStream.class)))
                .thenThrow(new OpmlParseException("Invalid OPML"));

        mockMvc.perform(multipart("/api/opml/import").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldExportOpml() throws Exception {
        when(feedService.findAll()).thenReturn(List.of(new Feed()));
        when(folderService.findAll()).thenReturn(List.of());
        when(opmlService.generateOpml(any(), any())).thenReturn("<opml>test</opml>");

        mockMvc.perform(get("/api/opml/export"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/xml"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"myfeeder-export.opml\""))
                .andExpect(content().string("<opml>test</opml>"));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "*OpmlControllerTest*"`
Expected: Compilation failure — `OpmlController` does not exist yet.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/org/bartram/myfeeder/controller/OpmlControllerTest.java
git commit -m "test(opml): add OpmlController tests (red)"
```

### Task 11: OpmlController — Implementation

**Files:**
- Create: `src/main/java/org/bartram/myfeeder/controller/OpmlController.java`

- [ ] **Step 1: Implement OpmlController**

```java
package org.bartram.myfeeder.controller;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.parser.OpmlParseException;
import org.bartram.myfeeder.service.FeedService;
import org.bartram.myfeeder.service.FolderService;
import org.bartram.myfeeder.service.OpmlImportResult;
import org.bartram.myfeeder.service.OpmlImportService;
import org.bartram.myfeeder.service.OpmlService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/opml")
@RequiredArgsConstructor
public class OpmlController {

    private final OpmlImportService opmlImportService;
    private final OpmlService opmlService;
    private final FeedService feedService;
    private final FolderService folderService;

    @PostMapping("/import")
    public ResponseEntity<OpmlImportResult> importOpml(@RequestParam("file") MultipartFile file) {
        try {
            OpmlImportResult result = opmlImportService.importOpml(file.getInputStream());
            return ResponseEntity.ok(result);
        } catch (OpmlParseException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/export")
    public ResponseEntity<String> exportOpml() {
        String opml = opmlService.generateOpml(feedService.findAll(), folderService.findAll());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"myfeeder-export.opml\"")
                .body(opml);
    }
}
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `./gradlew test --tests "*OpmlControllerTest*"`
Expected: All 3 tests PASS.

- [ ] **Step 3: Run all backend tests to verify nothing is broken**

Run: `./gradlew test`
Expected: All tests PASS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/org/bartram/myfeeder/controller/OpmlController.java
git commit -m "feat(opml): add import/export REST endpoints"
```

## Chunk 3: Frontend — API, Hook, and UI

### Task 12: Frontend API Client

**Files:**
- Create: `src/main/frontend/src/api/opml.ts`

- [ ] **Step 1: Create OPML API client**

Follow the pattern from `src/main/frontend/src/api/feeds.ts` and `src/main/frontend/src/api/client.ts`. The import endpoint uses `multipart/form-data` (not JSON), so it needs raw `fetch` instead of the `apiPost` helper. The export triggers a file download via blob URL.

```typescript
const BASE_URL = '/api'

export interface OpmlImportResult {
  created: number
  updated: number
  total: number
}

export const opmlApi = {
  importFeeds: async (file: File): Promise<OpmlImportResult> => {
    const formData = new FormData()
    formData.append('file', file)
    const res = await fetch(`${BASE_URL}/opml/import`, {
      method: 'POST',
      body: formData,
    })
    if (!res.ok) throw new Error(`OPML import failed: ${res.status}`)
    return res.json()
  },

  export: async (): Promise<void> => {
    const res = await fetch(`${BASE_URL}/opml/export`)
    if (!res.ok) throw new Error(`OPML export failed: ${res.status}`)
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'myfeeder-export.opml'
    a.click()
    URL.revokeObjectURL(url)
  },
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/frontend/src/api/opml.ts
git commit -m "feat(opml): add frontend API client for import/export"
```

### Task 13: Frontend Hook

**Files:**
- Create: `src/main/frontend/src/hooks/useOpml.ts`

- [ ] **Step 1: Create OPML hooks**

Follow the pattern from `src/main/frontend/src/hooks/useArticles.ts` — `useMutation` with `onSuccess` invalidation. Use the toast store from `src/main/frontend/src/components/Toast.tsx`.

```typescript
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { opmlApi } from '../api/opml'
import { useToastStore } from '../components/Toast'

export function useImportOpml() {
  const qc = useQueryClient()
  const addToast = useToastStore((s) => s.addToast)

  return useMutation({
    mutationFn: (file: File) => opmlApi.importFeeds(file),
    onSuccess: (result) => {
      qc.invalidateQueries({ queryKey: ['feeds'] })
      qc.invalidateQueries({ queryKey: ['folders'] })
      addToast(
        `Imported ${result.created} new feeds, updated ${result.updated} existing`,
        'success',
      )
    },
    onError: () => {
      addToast('Failed to import OPML file')
    },
  })
}

export async function exportOpml() {
  try {
    await opmlApi.export()
  } catch {
    useToastStore.getState().addToast('Failed to export feeds')
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/frontend/src/hooks/useOpml.ts
git commit -m "feat(opml): add frontend hook for import/export"
```

### Task 14: Feed Panel UI — Import/Export Buttons

**Files:**
- Modify: `src/main/frontend/src/components/FeedPanel.tsx` (lines 136-139, the footer area)

- [ ] **Step 1: Add import/export buttons to FeedPanel footer**

Add imports at the top of the file:

```typescript
import { useRef } from 'react'
import { useImportOpml, exportOpml } from '../hooks/useOpml'
```

Inside the `FeedPanel` component function (before the `return`), add:

```typescript
const importMutation = useImportOpml()
const fileInputRef = useRef<HTMLInputElement>(null)

const handleImportClick = () => fileInputRef.current?.click()
const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
  const file = e.target.files?.[0]
  if (file) {
    importMutation.mutate(file)
    e.target.value = ''
  }
}
```

Replace the existing footer div (lines 136-139):

```tsx
<div className="feed-panel-footer">
  <button className="footer-btn" onClick={onAddFeed}>+ Add Feed</button>
  <button className="footer-btn" onClick={handleImportClick}>Import</button>
  <button className="footer-btn" onClick={exportOpml}>Export</button>
  <button className="footer-btn" onClick={onSettings}>Settings</button>
  <input
    ref={fileInputRef}
    type="file"
    accept=".opml,.xml"
    style={{ display: 'none' }}
    onChange={handleFileChange}
  />
</div>
```

- [ ] **Step 2: Run frontend tests to verify nothing is broken**

Run: `cd src/main/frontend && npm test`
Expected: All existing tests PASS.

- [ ] **Step 3: Build frontend to verify compilation**

Run: `cd src/main/frontend && npm run build`
Expected: Build succeeds.

- [ ] **Step 4: Commit**

```bash
git add src/main/frontend/src/components/FeedPanel.tsx
git commit -m "feat(opml): add import/export buttons to feed panel"
```

### Task 15: Frontend Hook Test

**Files:**
- Create: `src/main/frontend/src/hooks/useOpml.test.ts`

- [ ] **Step 1: Write hook test verifying query invalidation on import success**

```typescript
import { describe, it, expect, vi, beforeEach } from 'vitest'

// Mock the API and toast modules before importing the hook
vi.mock('../api/opml', () => ({
  opmlApi: {
    importFeeds: vi.fn(),
  },
}))

vi.mock('../components/Toast', () => ({
  useToastStore: Object.assign(
    (selector: (s: { addToast: ReturnType<typeof vi.fn> }) => unknown) =>
      selector({ addToast: vi.fn() }),
    { getState: () => ({ addToast: vi.fn() }) },
  ),
}))

import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { useImportOpml } from './useOpml'
import { opmlApi } from '../api/opml'

function createWrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return {
    qc,
    wrapper: ({ children }: { children: React.ReactNode }) =>
      createElement(QueryClientProvider, { client: qc }, children),
  }
}

describe('useImportOpml', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('should invalidate feeds and folders queries on success', async () => {
    vi.mocked(opmlApi.importFeeds).mockResolvedValue({ created: 2, updated: 1, total: 3 })
    const { qc, wrapper } = createWrapper()
    const invalidateSpy = vi.spyOn(qc, 'invalidateQueries')

    const { result } = renderHook(() => useImportOpml(), { wrapper })
    result.current.mutate(new File(['<opml/>'], 'test.opml'))

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['feeds'] })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['folders'] })
  })
})
```

- [ ] **Step 2: Run frontend tests**

Run: `cd src/main/frontend && npm test`
Expected: All tests PASS.

- [ ] **Step 3: Commit**

```bash
git add src/main/frontend/src/hooks/useOpml.test.ts
git commit -m "test(opml): add frontend hook test for query invalidation"
```

### Task 16: Full Build Verification

- [ ] **Step 1: Run full Gradle build (includes frontend build and all backend tests)**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Manual smoke test (optional)**

Run: `./gradlew bootTestRun`

1. Navigate to the app in browser
2. Click "Export" — should download a `.opml` file
3. Add a feed manually, then export again — verify the feed appears in the OPML
4. Import the exported file — should show "0 new, N updated" toast
5. Import an OPML file from another reader (e.g. Feedly export) — should create feeds and folders

- [ ] **Step 3: Final commit if any fixups were needed**

```bash
git add -A
git commit -m "fix(opml): address issues found during smoke testing"
```
