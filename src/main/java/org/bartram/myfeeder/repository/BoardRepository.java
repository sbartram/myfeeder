package org.bartram.myfeeder.repository;
import org.bartram.myfeeder.model.Board;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;
import java.util.Optional;

public interface BoardRepository extends ListCrudRepository<Board, Long> {
    @Query("SELECT * FROM board WHERE LOWER(name) = LOWER(:name)")
    Optional<Board> findByNameIgnoreCase(String name);
}
