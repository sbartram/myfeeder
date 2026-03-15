package org.bartram.myfeeder.repository;
import org.bartram.myfeeder.TestcontainersConfiguration;
import org.bartram.myfeeder.model.Board;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.context.annotation.Import;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

@DataJdbcTest
@Import(TestcontainersConfiguration.class)
class BoardRepositoryTest {
    @Autowired private BoardRepository boardRepository;

    @Test
    void shouldSaveAndFindBoard() {
        Board board = new Board();
        board.setName("Must Read");
        board.setDescription("Important articles");
        board.setCreatedAt(Instant.now());
        Board saved = boardRepository.save(board);
        assertThat(saved.getId()).isNotNull();
        assertThat(boardRepository.findById(saved.getId()))
                .isPresent()
                .hasValueSatisfying(b -> assertThat(b.getName()).isEqualTo("Must Read"));
    }
}
