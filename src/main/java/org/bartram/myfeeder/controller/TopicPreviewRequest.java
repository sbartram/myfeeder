package org.bartram.myfeeder.controller;

/** A topic preview request. {@code topicId} is null for an unsaved row; there is no weight (the client does the math). */
public record TopicPreviewRequest(Long articleId, String description, Long topicId) {}
