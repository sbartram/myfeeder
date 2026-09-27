package org.bartram.myfeeder.model;

import java.util.List;

/**
 * The stored thumbs vote on an article (FDBK-01): {@code vote} is 1 or -1. When {@code narrowed}, the
 * vote moves only the picked topics and {@code topics} lists them in topic id order (03 D-02); otherwise
 * {@code topics} is empty.
 */
public record ArticleFeedback(int vote, boolean narrowed, List<Topic> topics) {

    /** One picked topic of a narrowed vote. */
    public record Topic(long topicId, String name) {}
}
