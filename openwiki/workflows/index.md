# Files

- [Feed Lifecycle Workflow (Subscribe, Poll, Backoff, Retention, Extraction)](feed-lifecycle.md) - Walks through subscribing, event-driven scheduling, conditional polling with dedup, self-adjusting backoff, deletion, retention, and reader-view content extraction — the core article-ingestion pipeline that feeds the interest-ranking workflow.
- [Interest Ranking Workflow (Jev Scoring, Learned Weights, Engagement, Gap Discovery)](interest-ranking.md) - End-to-end walk through the second major pipeline in myfeeder — ingest to scoring-sweep enqueue, the Jev judge call, the learned-weight blend of thumbs feedback and engagement at query time, the post-engagement UI refresh, and gap-discovery topic suggestions.
