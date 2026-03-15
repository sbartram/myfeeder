package org.bartram.myfeeder.repository;
import org.bartram.myfeeder.model.Board;
import org.springframework.data.repository.ListCrudRepository;

public interface BoardRepository extends ListCrudRepository<Board, Long> {}
