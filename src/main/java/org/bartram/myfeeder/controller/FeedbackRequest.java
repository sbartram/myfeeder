package org.bartram.myfeeder.controller;

import java.util.List;

/**
 * A thumbs vote. The vote is boxed so a missing vote reaches the service's 400. {@code topicIds} null
 * means not narrowed (D-17); non-null is the picked subset of the article's matched topics (D-15).
 */
public record FeedbackRequest(Integer vote, List<Long> topicIds) {}
