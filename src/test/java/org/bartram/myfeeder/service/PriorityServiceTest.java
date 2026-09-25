package org.bartram.myfeeder.service;

import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.repository.ArticleRepository;
import org.bartram.myfeeder.repository.InterestScoreQueries;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriorityServiceTest {

    @Mock private InterestScoreQueries interestScoreQueries;
    @Mock private ArticleRepository articleRepository;
    @InjectMocks private PriorityService priorityService;

    @Test
    void firstPageWithoutCursor() {
        List<Article> page = List.of(new Article());
        when(interestScoreQueries.priorityFirstPage(51)).thenReturn(page);

        assertThat(priorityService.page(null, 51)).isSameAs(page);
        verify(articleRepository, never()).existsById(anyLong());
    }

    @Test
    void afterCursorWhenCursorExists() {
        List<Article> page = List.of(new Article());
        when(articleRepository.existsById(7L)).thenReturn(true);
        when(interestScoreQueries.priorityPageAfter(7L, 51)).thenReturn(page);

        assertThat(priorityService.page(7L, 51)).isSameAs(page);
    }

    @Test
    void missingCursorThrowsNotFoundWithoutQuerying() {
        when(articleRepository.existsById(9L)).thenReturn(false);

        assertThatThrownBy(() -> priorityService.page(9L, 51))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Article not found: 9");
        verify(interestScoreQueries, never()).priorityFirstPage(anyInt());
        verify(interestScoreQueries, never()).priorityPageAfter(anyLong(), anyInt());
    }
}
