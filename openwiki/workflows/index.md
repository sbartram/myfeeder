# Files

- [Feed Lifecycle Workflow (Subscribe, Poll, Backoff, Retention)](feed-lifecycle.md) - Walks through how a feed is subscribed, scheduled, polled on a recurring basis with error backoff, kept in sync via application events on save/delete, and eventually has old article content cleared by the retention job. Key file for anyone changing polling, scheduling, or article ingestion behavior.
- [Interest Ranking & Engagement Learning Workflow](interest-scoring.md) - Explains the Jev-powered interest-scoring pipeline — how new articles are queued and scored against a user rubric, how the blended/learned Priority ranking is computed at query time, how engagement and thumbs feedback adjust learned weights, and how gap discovery surfaces topics the rubric is missing.
