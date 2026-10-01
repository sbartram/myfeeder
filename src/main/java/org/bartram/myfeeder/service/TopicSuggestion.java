package org.bartram.myfeeder.service;

/**
 * One Suggested topics row (GAP-01): an engaged article no topic covers, with its feed's title and the
 * badge {@code InterestBadge} shows for it.
 */
public record TopicSuggestion(long articleId, String title, String feedTitle, int interestScore) {
}
