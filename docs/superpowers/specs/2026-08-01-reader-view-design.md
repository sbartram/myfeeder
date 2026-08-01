# Reader View (fetch + extract original page) — Design

Date: 2026-08-01
Backlog item: "option to replace dark mode pages with light (example: https://tldr.tech/ai/2026-07-17)"

## Problem

Some feeds (e.g. TLDR) publish items with no body — just title and link — so the reading
pane is empty and reading means opening the original site, which may be dark-themed.
Other feeds ship HTML whose inline styling fights the app theme. The reader should be able
to fetch the original page, extract the readable content, and render it with the app's own
theme (dark pages become light automatically).

## Behavior

- **Auto**: when an article has no feed content and no summary, the reading pane
  automatically loads the extracted original page.
- **Manual**: a "📖 Reader View" toolbar toggle extracts any article on demand and can be
  toggled back to feed content.
- Extraction failure shows an inline message with an "Open Original" fallback.

## Backend

- Dependency: `net.dankito.readability4j:readability4j` (JVM port of Mozilla Readability;
  brings jsoup).
- `GET /api/articles/{id}/extracted-content` → `{title, contentHtml}`.
  Flow: load article → fetch `article.url` through **FeedFetcher** (reuses SSRF guard,
  10 MiB cap, User-Agent customizer, raw-bytes contract) → decode via jsoup (header charset,
  else meta sniff) → Readability4J extract → persist + return.
- Errors map to existing handler: missing article → `NotFoundException` (404); article with
  no URL → `IllegalArgumentException` (400); page HTTP error → `FeedFetchException` (422);
  extraction yields nothing → `FeedParseException` (422).
- **Cache**: Flyway `V5__article_extracted_content.sql` adds `extracted_content TEXT` to
  `articles`; first extraction persists it, later requests return the cached copy.
  RetentionService already ages out articles, so no new lifecycle.

## Frontend

- `api/articles.ts`: `getExtractedContent(id)`.
- `hooks/useArticles.ts`: `useExtractedArticle(id, enabled)`.
- ReadingPane: reader-view state is per-article ("auto" until toggled; auto resolves to on
  when the article has no content/summary). Extracted HTML is sanitized with DOMPurify
  configured with `FORBID_TAGS: ['style']`, `FORBID_ATTR: ['style']` so publisher styling is
  dropped and theme colors apply — this is the dark→light guarantee.

## Testing

- Backend: service unit test (mock fetcher returning a dark-styled HTML fixture; asserts
  extraction, caching, and single fetch), controller slice tests (200/404/422).
- Frontend: ReadingPane tests — auto-load when feed content empty, manual toggle, error
  fallback; sanitization drops style attributes.

## Alternative considered

Client-side extraction with `@mozilla/readability` behind a backend fetch-proxy: better
maintained extractor, but loses trivial DB caching and fattens the client. Rejected.
