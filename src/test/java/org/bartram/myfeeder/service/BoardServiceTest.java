package org.bartram.myfeeder.service;
import org.bartram.myfeeder.model.Board;
import org.bartram.myfeeder.model.BoardArticle;
import org.bartram.myfeeder.repository.BoardArticleRepository;
import org.bartram.myfeeder.repository.BoardRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BoardServiceTest {
    @Mock private BoardRepository boardRepository;
    @Mock private BoardArticleRepository boardArticleRepository;
    @InjectMocks private BoardService boardService;

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
}
