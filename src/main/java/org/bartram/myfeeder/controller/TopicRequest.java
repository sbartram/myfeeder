package org.bartram.myfeeder.controller;

/**
 * Boxed weight: a missing weight on POST means the +20 default; on PUT it is required.
 * {@code sourceArticleId} is optional on POST and marks that article's suggested topic handled (D-13);
 * PUT ignores it.
 */
public record TopicRequest(String name, String description, Integer weight, Long sourceArticleId) {}
