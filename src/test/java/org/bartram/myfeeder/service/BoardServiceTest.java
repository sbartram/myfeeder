package org.bartram.myfeeder.service;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.model.Board;
import org.bartram.myfeeder.model.BoardArticle;
import org.bartram.myfeeder.model.EngagementKind;
import org.bartram.myfeeder.repository.ArticleEngagementStore;
import org.bartram.myfeeder.repository.BoardArticleRepository;
import org.bartram.myfeeder.repository.BoardRepository;
import org.bartram.myfeeder.repository.InterestScoreQueries;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DataIntegrityViolationException;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BoardServiceTest {
    @Mock private BoardRepository boardRepository;
    @Mock private BoardArticleRepository boardArticleRepository;
    @Mock private InterestScoreQueries interestScoreQueries;
    @Mock private ArticleEngagementStore engagementStore;
    @InjectMocks private BoardService boardService;

    @Test
    void findArticlesSetsInterestScore() {
        Article newer = new Article();
        newer.setId(9L);
        Article older = new Article();
        older.setId(4L);
        when(boardArticleRepository.findArticlesByBoardId(1L, 51)).thenReturn(List.of(newer, older));
        when(interestScoreQueries.displayScores(List.of(9L, 4L))).thenReturn(Map.of(4L, 100));

        List<Article> result = boardService.findArticles(1L, null, 51);

        assertThat(result).extracting(Article::getId).containsExactly(9L, 4L);
        assertThat(result).extracting(Article::getInterestScore).containsExactly(null, 100);
    }

    @Test
    void shouldCreateBoard() {
        when(boardRepository.save(any())).thenAnswer(inv -> { Board b = inv.getArgument(0); b.setId(1L); return b; });
        Board result = boardService.create("Must Read", "Important stuff");
        assertThat(result.getName()).isEqualTo("Must Read");
    }

    @Test
    void shouldNotDuplicateArticleInBoard() {
        when(boardArticleRepository.existsByBoardIdAndArticleId(1L, 2L)).thenReturn(true);
        boardService.addArticle(1L, 2L);
        verify(boardArticleRepository, never()).save(any());
    }

    @Test
    void shouldAddArticleToBoard() {
        when(boardArticleRepository.existsByBoardIdAndArticleId(1L, 2L)).thenReturn(false);
        boardService.addArticle(1L, 2L);
        verify(boardArticleRepository).save(any(BoardArticle.class));
    }

    @Test
    void addingRecordsBoardAfterTheSave() {
        when(boardArticleRepository.existsByBoardIdAndArticleId(1L, 2L)).thenReturn(false);

        boardService.addArticle(1L, 2L);

        InOrder inOrder = inOrder(boardArticleRepository, engagementStore);
        inOrder.verify(boardArticleRepository).save(any(BoardArticle.class));
        inOrder.verify(engagementStore).recordQuietly(2L, EngagementKind.BOARD);
    }

    @Test
    void reAddingAnArticleAlreadyOnTheBoardStillRecordsBoard() {
        when(boardArticleRepository.existsByBoardIdAndArticleId(1L, 2L)).thenReturn(true);

        boardService.addArticle(1L, 2L);

        verify(boardArticleRepository, never()).save(any());
        verify(engagementStore).recordQuietly(2L, EngagementKind.BOARD);
    }

    @Test
    void aFailedSaveRecordsNothing() {
        when(boardArticleRepository.existsByBoardIdAndArticleId(99L, 2L)).thenReturn(false);
        when(boardArticleRepository.save(any())).thenThrow(new DataIntegrityViolationException("fk"));

        assertThatThrownBy(() -> boardService.addArticle(99L, 2L))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(engagementStore, never()).recordQuietly(anyLong(), any());
    }

    @Test
    void removingAndDeletingTouchNoEngagement() {
        boardService.removeArticle(1L, 2L);
        boardService.delete(1L);

        verify(boardArticleRepository).removeArticleFromBoard(1L, 2L);
        verify(boardRepository).deleteById(1L);
        verifyNoInteractions(engagementStore);
    }

    @Test
    void shouldReturnExistingBoardByName() {
        Board existing = new Board();
        existing.setId(1L);
        existing.setName("Read Later");
        when(boardRepository.findByNameIgnoreCase("Read Later")).thenReturn(Optional.of(existing));

        Board result = boardService.getOrCreateByName("Read Later");

        assertThat(result.getId()).isEqualTo(1L);
        verify(boardRepository, never()).save(any());
    }

    @Test
    void shouldCreateBoardWhenNameNotFound() {
        when(boardRepository.findByNameIgnoreCase("Read Later")).thenReturn(Optional.empty());
        when(boardRepository.save(any())).thenAnswer(inv -> { Board b = inv.getArgument(0); b.setId(2L); return b; });

        Board result = boardService.getOrCreateByName("Read Later");

        assertThat(result.getName()).isEqualTo("Read Later");
        verify(boardRepository).save(any());
    }
}
