package org.bartram.myfeeder.service;

import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.model.Folder;
import org.bartram.myfeeder.repository.FeedRepository;
import org.bartram.myfeeder.repository.FolderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FolderServiceTest {
    @Mock private FolderRepository folderRepository;
    @Mock private FeedRepository feedRepository;
    @InjectMocks private FolderService folderService;

    @Test
    void shouldCreateFolder() {
        when(folderRepository.count()).thenReturn(2L);
        when(folderRepository.save(any())).thenAnswer(inv -> {
            Folder f = inv.getArgument(0);
            f.setId(1L);
            return f;
        });
        Folder result = folderService.create("Tech");
        assertThat(result.getName()).isEqualTo("Tech");
        assertThat(result.getDisplayOrder()).isEqualTo(2);
    }

    @Test
    void shouldDeleteFolder() {
        folderService.delete(5L);
        verify(folderRepository).deleteById(5L);
    }

    @Test
    void shouldMoveFeedToFolder() {
        Feed feed = new Feed();
        feed.setId(1L);
        Folder folder = new Folder();
        folder.setId(2L);
        when(feedRepository.findById(1L)).thenReturn(Optional.of(feed));
        when(folderRepository.findById(2L)).thenReturn(Optional.of(folder));
        when(feedRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Feed result = folderService.moveFeedToFolder(1L, 2L);
        assertThat(result.getFolderId()).isEqualTo(2L);
    }
}
