package org.bartram.myfeeder.service;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.model.UnreadCount;
import org.bartram.myfeeder.repository.ArticleRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ArticleService {

    private final ArticleRepository articleRepository;

    public Optional<Article> findById(Long id) {
        return articleRepository.findById(id);
    }

    public List<Article> findByFeedId(Long feedId) {
        return articleRepository.findByFeedId(feedId);
    }

    public List<Article> findAll() {
        return articleRepository.findAll();
    }

    public List<Article> findUnread() {
        return articleRepository.findByReadFalse();
    }

    public List<Article> findStarred() {
        return articleRepository.findByStarredTrue();
    }

    public Article updateState(Long id, Boolean read, Boolean starred) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Article not found: " + id));

        if (read != null) {
            article.setRead(read);
        }
        if (starred != null) {
            article.setStarred(starred);
        }

        return articleRepository.save(article);
    }

    public void markRead(List<Long> articleIds, Long feedId) {
        if (articleIds != null && !articleIds.isEmpty()) {
            articleRepository.markReadByIds(articleIds);
        } else if (feedId != null) {
            articleRepository.markAllReadByFeedId(feedId);
        } else {
            throw new IllegalArgumentException("Either articleIds or feedId must be provided");
        }
    }

    public List<Article> findFiltered(Long feedId, Boolean read, Boolean starred, Long before, int limit) {
        if (before != null) {
            return articleRepository.findFilteredBefore(feedId, read, starred, before, limit);
        }
        return articleRepository.findFiltered(feedId, read, starred, limit);
    }

    public Map<Long, Long> countUnreadByFeed() {
        return articleRepository.countUnreadByFeed().stream()
                .collect(java.util.stream.Collectors.toMap(
                        UnreadCount::feedId,
                        UnreadCount::count
                ));
    }
}
