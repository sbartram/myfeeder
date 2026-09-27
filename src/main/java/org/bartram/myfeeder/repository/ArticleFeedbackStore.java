package org.bartram.myfeeder.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

/**
 * The stored thumbs votes (FDBK-01). This store writes and deletes only {@code article_feedback} and
 * {@code article_feedback_topic} rows. It never writes the topic table: a topic's learned adjustment is
 * derived from these rows by {@link InterestScoreQueries}, so the user's own weights are never rewritten.
 */
@Repository
@RequiredArgsConstructor
public class ArticleFeedbackStore {

    private final JdbcClient jdbc;

    /**
     * Stores the article's vote, replacing any earlier one. {@code topicIds} null means not narrowed
     * (every matched topic moves) and deletes every pick; non-null stores exactly those picks (03 D-02).
     * The upsert takes the row lock, so concurrent votes on one article serialize.
     */
    @Transactional
    public void upsert(long articleId, int vote, Collection<Long> topicIds) {
        jdbc.sql("INSERT INTO article_feedback (article_id, vote, topics_narrowed) VALUES (:articleId, :vote, :narrowed) "
                        + "ON CONFLICT (article_id) DO UPDATE SET vote = EXCLUDED.vote, "
                        + "topics_narrowed = EXCLUDED.topics_narrowed")
                .param("articleId", articleId)
                .param("vote", vote)
                .param("narrowed", topicIds != null)
                .update();
        jdbc.sql("DELETE FROM article_feedback_topic WHERE article_id = :articleId")
                .param("articleId", articleId)
                .update();
        if (topicIds == null) {
            return;
        }
        for (Long topicId : topicIds) {
            jdbc.sql("INSERT INTO article_feedback_topic (article_id, topic_id) VALUES (:articleId, :topicId)")
                    .param("articleId", articleId)
                    .param("topicId", topicId)
                    .update();
        }
    }

    /** Removes the article's vote; V6 cascades its picks. Returns the rows deleted (0 when there was no vote). */
    public int delete(long articleId) {
        return jdbc.sql("DELETE FROM article_feedback WHERE article_id = :articleId")
                .param("articleId", articleId)
                .update();
    }
}
