package org.bartram.myfeeder.service;

import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.model.UnreadCount;
import org.bartram.myfeeder.repository.ArticleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {

    @Mock private ArticleRepository articleRepository;
    @InjectMocks private ArticleService articleService;

    @Test
    void shouldFindArticleById() {
        var article = new Article();
        article.setId(1L);
        article.setTitle("Test");
        when(articleRepository.findById(1L)).thenReturn(Optional.of(article));

        var result = articleService.findById(1L);
        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("Test");
    }

    @Test
    void shouldMarkArticleAsRead() {
        var article = new Article();
        article.setId(1L);
        article.setRead(false);
        when(articleRepository.findById(1L)).thenReturn(Optional.of(article));
        when(articleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = articleService.updateState(1L, true, null);
        assertThat(result.isRead()).isTrue();
    }

    @Test
    void shouldToggleStarred() {
        var article = new Article();
        article.setId(1L);
        article.setStarred(false);
        when(articleRepository.findById(1L)).thenReturn(Optional.of(article));
        when(articleRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = articleService.updateState(1L, null, true);
        assertThat(result.isStarred()).isTrue();
    }

    @Test
    void shouldBulkMarkReadByIds() {
        articleService.markRead(List.of(1L, 2L), null);
        verify(articleRepository).markReadByIds(List.of(1L, 2L));
    }

    @Test
    void shouldBulkMarkReadByFeedId() {
        articleService.markRead(null, 5L);
        verify(articleRepository).markAllReadByFeedId(5L);
    }

    @Test
    void shouldFindFilteredWithoutCursor() {
        var article = new Article();
        article.setId(1L);
        when(articleRepository.findFiltered(null, false, null, 10)).thenReturn(List.of(article));

        var result = articleService.findFiltered(null, false, null, null, 10, false);
        assertThat(result).hasSize(1);
        verify(articleRepository).findFiltered(null, false, null, 10);
    }

    @Test
    void shouldFindFilteredWithCursor() {
        var article = new Article();
        article.setId(5L);
        when(articleRepository.findFilteredBefore(1L, null, null, 10L, 10)).thenReturn(List.of(article));

        var result = articleService.findFiltered(1L, null, null, 10L, 10, false);
        assertThat(result).hasSize(1);
        verify(articleRepository).findFilteredBefore(1L, null, null, 10L, 10);
    }

    @Test
    void shouldMarkReadByFeedIdOlderThanDays() {
        articleService.markRead(null, 5L, 7);
        verify(articleRepository).markReadByFeedIdOlderThan(eq(5L), any(Instant.class));
    }

    @Test
    void shouldRejectOlderThanDaysWithoutFeedId() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> articleService.markRead(null, null, 7));
    }

    @Test
    void shouldRejectOlderThanDaysZero() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> articleService.markRead(null, 5L, 0));
    }

    @Test
    void shouldRejectOlderThanDaysNegative() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> articleService.markRead(null, 5L, -3));
    }

    @Test
    void shouldCountUnreadByFeed() {
        when(articleRepository.countUnreadByFeed()).thenReturn(List.of(
                new UnreadCount(1L, 3L),
                new UnreadCount(2L, 7L)
        ));

        Map<Long, Long> counts = articleService.countUnreadByFeed();
        assertThat(counts).containsEntry(1L, 3L).containsEntry(2L, 7L);
    }
}
