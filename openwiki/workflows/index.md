# Files

- [Feed Lifecycle Workflow (Subscribe, Poll, Backoff, Retention)](feed-lifecycle.md) - Walks through how a feed is subscribed, scheduled, polled on a recurring basis with error backoff, kept in sync via application events on save/delete, and eventually has old article content cleared by the retention job. Key file for anyone changing polling, scheduling, or article ingestion behavior.
- [Reader View / Content Extraction Workflow](reader-view-extraction.md) - Explains the end-to-end reader-view flow — ReadingPane's auto/manual toggle, GET /api/articles/{id}/extracted-content, ArticleExtractionService's fetch-through-FeedFetcher + Readability4J extraction + DB caching, DOMPurify sanitization of extracted HTML, and how RetentionService ages the cache out.
