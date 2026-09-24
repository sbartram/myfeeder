package org.bartram.myfeeder.event;

import java.util.List;

/** Published by FeedPollingService after a poll inserts new articles; the scoring listener enqueues them. */
public record ArticlesIngestedEvent(Long feedId, List<Long> articleIds) {}
