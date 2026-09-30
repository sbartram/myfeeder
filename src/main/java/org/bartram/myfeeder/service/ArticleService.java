package org.bartram.myfeeder.service;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.model.EngagementKind;
import org.bartram.myfeeder.model.UnreadCount;
import org.bartram.myfeeder.repository.ArticleEngagementStore;
import org.bartram.myfeeder.repository.ArticleFeedbackStore;
import org.bartram.myfeeder.repository.ArticleRepository;
import org.bartram.myfeeder.repository.InterestScoreQueries;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final InterestScoreQueries interestScoreQueries;
    private final ArticleFeedbackStore articleFeedbackStore;
    private final ArticleEngagementStore engagementStore;

    public Optional<Article> findById(Long id) {
        return articleRepository.findById(id);
    }

    /**
     * The article with its badge and exact "Why N?" breakdown (PRIO-04), both from the blend CTE; an
     * unscored article gets a null badge and no breakdown. The stored thumbs vote rides along (FDBK-01),
     * null when there is none, scored or not. The engagement kinds ride along too (D-05), [] when there
     * are none. Only GET /api/articles/{id} and the feedback responses use this, so lists never carry the
     * vote (D-02) or the engagement; the Raindrop path keeps {@link #findById(Long)}.
     */
    public Optional<Article> findByIdWithBreakdown(Long id) {
        return articleRepository.findById(id).map(article -> {
            interestScoreQueries.breakdownInputs(id).ifPresentOrElse(inputs -> {
                article.setInterestScore(inputs.display());
                article.setInterestBreakdown(ScoreBreakdowns.build(inputs));
            }, () -> {
                article.setInterestScore(null);
                article.setInterestBreakdown(null);
            });
            article.setFeedback(articleFeedbackStore.find(id).orElse(null));
            article.setEngagement(engagementStore.kinds(id));
            return article;
        });
    }

    /**
     * Records that the user opened the article's original link (CAPT-01). Idempotent: a repeated open keeps
     * the first row. A missing article is a 404 (D-12). A database failure propagates as a 5xx rather than a
     * 204 that would claim a row was stored; the fire-and-forget client ignores it.
     */
    public void recordOpen(Long id) {
        if (!articleRepository.existsById(id)) {
            throw new NotFoundException("Article not found: " + id);
        }
        engagementStore.record(id, EngagementKind.OPEN_ORIGINAL);
    }

    public Article updateState(Long id, Boolean read, Boolean starred) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Article not found: " + id));

        if (read != null) {
            article.setRead(read);
        }
        if (starred != null) {
            article.setStarred(starred);
        }

        Article saved = articleRepository.save(article);
        withScores(List.of(saved));
        return saved;
    }

    public void markRead(List<Long> articleIds, Long feedId, Integer olderThanDays) {
        if (articleIds != null && !articleIds.isEmpty()) {
            articleRepository.markReadByIds(articleIds);
        } else if (feedId != null && olderThanDays != null) {
            if (olderThanDays < 1) {
                throw new IllegalArgumentException("olderThanDays must be >= 1");
            }
            Instant cutoff = Instant.now().minus(olderThanDays, ChronoUnit.DAYS);
            articleRepository.markReadByFeedIdOlderThan(feedId, cutoff);
        } else if (feedId != null) {
            articleRepository.markAllReadByFeedId(feedId);
        } else {
            throw new IllegalArgumentException("Either articleIds or feedId must be provided");
        }
    }

    public List<Article> findFiltered(Long feedId, Boolean read, Boolean starred, Long cursor, int limit, boolean ascending) {
        if (cursor != null) {
            Article cursorArticle = articleRepository.findById(cursor)
                    .orElseThrow(() -> new IllegalArgumentException("Cursor article not found: " + cursor));
            Instant cursorDate = cursorArticle.getPublishedAt() != null
                    ? cursorArticle.getPublishedAt() : cursorArticle.getFetchedAt();
            if (ascending) {
                return withScores(articleRepository.findFilteredAfter(feedId, read, starred, cursorDate, cursor, limit));
            }
            return withScores(articleRepository.findFilteredBefore(feedId, read, starred, cursorDate, cursor, limit));
        }
        if (ascending) {
            return withScores(articleRepository.findFilteredAsc(feedId, read, starred, limit));
        }
        return withScores(articleRepository.findFiltered(feedId, read, starred, limit));
    }

    /**
     * Sets each article's interest badge from the blend CTE (D-18: read articles included, null when
     * unscored). Returns the same list in the same order; an empty list runs no query.
     */
    private List<Article> withScores(List<Article> articles) {
        if (articles.isEmpty()) {
            return articles;
        }
        Map<Long, Integer> scores = interestScoreQueries.displayScores(
                articles.stream().map(Article::getId).toList());
        articles.forEach(a -> a.setInterestScore(scores.get(a.getId())));
        return articles;
    }

    public Map<Long, Long> countUnreadByFeed() {
        return articleRepository.countUnreadByFeed().stream()
                .filter(uc -> uc.feedId() != null)
                .collect(java.util.stream.Collectors.toMap(
                        UnreadCount::feedId,
                        UnreadCount::count
                ));
    }
}
