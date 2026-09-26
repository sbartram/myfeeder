package org.bartram.myfeeder.service;

import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.repository.ArticleRepository;
import org.bartram.myfeeder.repository.InterestScoreQueries;
import org.bartram.myfeeder.repository.InterestScoreQueries.PriorityRow;
import org.bartram.myfeeder.repository.InterestScoreQueries.SortKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriorityServiceTest {

    private static final Instant DATE = Instant.parse("2026-09-25T12:00:00.123456Z");

    @Mock private InterestScoreQueries interestScoreQueries;
    @Mock private ArticleRepository articleRepository;
    @InjectMocks private PriorityService priorityService;

    @Test
    void firstPageWithoutCursor() {
        List<PriorityRow> page = List.of(new PriorityRow(new Article(), new SortKey(82.2, DATE, 1L)));
        when(interestScoreQueries.priorityFirstPage(51)).thenReturn(page);

        assertThat(priorityService.page(null, 51)).isSameAs(page);
        verify(articleRepository, never()).existsById(anyLong());
    }

    @Test
    void afterCursorWhenCursorExists() {
        SortKey key = new SortKey(82.2, DATE, 7L);
        List<PriorityRow> page = List.of(new PriorityRow(new Article(), new SortKey(75.0, DATE, 3L)));
        when(articleRepository.existsById(7L)).thenReturn(true);
        when(interestScoreQueries.priorityPageAfter(key, 51)).thenReturn(page);

        assertThat(priorityService.page(key, 51)).isSameAs(page);
    }

    @Test
    void missingCursorThrowsNotFoundWithoutQuerying() {
        when(articleRepository.existsById(9L)).thenReturn(false);

        assertThatThrownBy(() -> priorityService.page(new SortKey(82.2, DATE, 9L), 51))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Article not found: 9");
        verify(interestScoreQueries, never()).priorityFirstPage(anyInt());
        verify(interestScoreQueries, never()).priorityPageAfter(any(), anyInt());
    }
}
