package org.bartram.myfeeder.repository;

import org.bartram.myfeeder.model.Folder;
import org.springframework.data.repository.ListCrudRepository;
import java.util.List;

public interface FolderRepository extends ListCrudRepository<Folder, Long> {
    List<Folder> findAllByOrderByDisplayOrderAsc();
}
