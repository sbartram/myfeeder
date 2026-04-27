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
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    void shouldReorderFoldersDensely() {
        Folder a = new Folder(); a.setId(1L); a.setDisplayOrder(0);
        Folder b = new Folder(); b.setId(2L); b.setDisplayOrder(1);
        Folder c = new Folder(); c.setId(3L); c.setDisplayOrder(2);
        when(folderRepository.findAll()).thenReturn(List.of(a, b, c));
        when(folderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(folderRepository.findAllByOrderByDisplayOrderAsc())
                .thenReturn(List.of(c, a, b));

        folderService.reorder(List.of(3L, 1L, 2L));

        assertThat(c.getDisplayOrder()).isEqualTo(0);
        assertThat(a.getDisplayOrder()).isEqualTo(1);
        assertThat(b.getDisplayOrder()).isEqualTo(2);
    }

    @Test
    void shouldRejectReorderWithMissingIds() {
        Folder a = new Folder(); a.setId(1L);
        Folder b = new Folder(); b.setId(2L);
        when(folderRepository.findAll()).thenReturn(List.of(a, b));
        assertThatThrownBy(() -> folderService.reorder(List.of(1L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectReorderWithDuplicateIds() {
        assertThatThrownBy(() -> folderService.reorder(List.of(1L, 1L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectReorderWithUnknownId() {
        Folder a = new Folder(); a.setId(1L);
        when(folderRepository.findAll()).thenReturn(List.of(a));
        assertThatThrownBy(() -> folderService.reorder(List.of(99L)))
                .isInstanceOf(IllegalArgumentException.class);
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
