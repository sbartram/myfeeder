package org.bartram.myfeeder.service;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.model.UnreadCount;
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

    public Optional<Article> findById(Long id) {
        return articleRepository.findById(id);
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
