package org.bartram.myfeeder.service;

import java.util.List;

/**
 * The {@code GET /api/interest/suggestions} response (D-07): {@code items} holds at most
 * {@link TopicSuggestionService#MAX_SUGGESTIONS} rows, and {@code total} counts every qualifying article.
 * Later fields are appended; existing ones are never renamed.
 */
public record TopicSuggestions(List<TopicSuggestion> items, int total) {
}
