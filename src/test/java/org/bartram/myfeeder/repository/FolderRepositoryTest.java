package org.bartram.myfeeder.repository;

import org.bartram.myfeeder.TestcontainersConfiguration;
import org.bartram.myfeeder.model.Folder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.context.annotation.Import;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@DataJdbcTest
@Import(TestcontainersConfiguration.class)
class FolderRepositoryTest {

    @Autowired
    private FolderRepository folderRepository;

    @Test
    void shouldSaveAndFindFolder() {
        Folder folder = new Folder();
        folder.setName("Tech");
        folder.setDisplayOrder(0);
        folder.setCreatedAt(Instant.now());
        Folder saved = folderRepository.save(folder);
        assertThat(saved.getId()).isNotNull();
        assertThat(folderRepository.findById(saved.getId())).isPresent();
    }

    @Test
    void shouldReturnFoldersOrderedByDisplayOrder() {
        Folder second = new Folder();
        second.setName("Science");
        second.setDisplayOrder(1);
        second.setCreatedAt(Instant.now());
        Folder first = new Folder();
        first.setName("Tech");
        first.setDisplayOrder(0);
        first.setCreatedAt(Instant.now());
        folderRepository.save(second);
        folderRepository.save(first);
        List<Folder> folders = folderRepository.findAllByOrderByDisplayOrderAsc();
        assertThat(folders).extracting(Folder::getName).containsExactly("Tech", "Science");
    }
}
