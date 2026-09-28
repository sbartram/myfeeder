package org.bartram.myfeeder.service;

import org.bartram.myfeeder.model.Article;

import java.util.List;

/**
 * The response to a thumbs vote or its removal (D-07): the article with its re-blended badge and
 * breakdown, whether it was scored, and the effect on every topic it matched.
 *
 * <p>{@code scored} false means the vote is stored and counts once the article is scored (D-03);
 * {@code scored} true with empty {@code effects} means the article matched no topic (D-18).
 */
public record FeedbackResult(Article article, boolean scored, List<TopicEffect> effects) {

    /**
     * One matched topic's effective weight before and after the write, both read inside the write's
     * transaction and rounded to 6 decimals. {@code learned} is the capped learned value after the write.
     * The client prints {@code after - before} and never recomputes it. {@code limit} names why the change
     * is smaller than the nominal nudge ({@link LearnedLimit}).
     */
    public record TopicEffect(long topicId, String name, double before, double after, double baseWeight,
                              double learned, LearnedLimit limit) {}
}
