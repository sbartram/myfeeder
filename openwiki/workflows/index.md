# Files

- [Feed Lifecycle Workflow (Subscribe, Poll, Backoff, Retention)](feed-lifecycle.md) - Walks through how a feed is subscribed, scheduled, polled on a recurring basis with error backoff, kept in sync via application events on save/delete, and eventually has old article content cleared by the retention job. Key file for anyone changing polling, scheduling, or article ingestion behavior.
- [Reader View / Article Content Extraction Workflow](reader-view.md) - Explains how myfeeder fetches an article's original page, extracts readable content with Readability4J, caches it on the article row, and renders it safely in the frontend — a request-driven, on-demand workflow distinct from feed polling that reuses the SSRF-guarded fetch path.
