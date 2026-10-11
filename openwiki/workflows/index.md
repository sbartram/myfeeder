# Files

- [Engagement Capture, Learned Weights, and Topic Suggestions](engagement-and-suggestions.md) - How reader actions (open/star/board/Raindrop) are recorded as engagement, how engagement blends with thumbs feedback into learned topic weights, and how the same engagement rows drive gap-discovery topic suggestions.
- [Feed Lifecycle Workflow (Subscribe, Poll, Backoff, Retention, Extraction)](feed-lifecycle.md) - Walks through how a feed is subscribed, scheduled, polled with error backoff via application events, deduplicated, and how old content is retained/cleared, plus the reader-view article extraction path that reuses the same fetch/SSRF guard.
- [Interest Scoring Workflow (Jev Ranking Pipeline)](interest-scoring.md) - How articles flow from ingestion through the scoring queue and sweep to a Jev judgment and a stored score, including profile/topic setup, cold start, eligibility windows, the rubric-change discard rule, and status polling.
