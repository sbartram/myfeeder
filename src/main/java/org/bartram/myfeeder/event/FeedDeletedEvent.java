package org.bartram.myfeeder.event;

/** Published after a feed is deleted; the polling scheduler cancels its task. */
public record FeedDeletedEvent(Long feedId) {}
