package org.bartram.myfeeder.event;

import org.bartram.myfeeder.model.Feed;

/** Published after a feed is created or updated; the polling scheduler (re-)registers it. */
public record FeedSavedEvent(Feed feed) {}
