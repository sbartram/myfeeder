package org.bartram.myfeeder.service;

import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.model.Folder;
import org.bartram.myfeeder.parser.OpmlFeed;
import org.bartram.myfeeder.repository.FeedRepository;
import org.bartram.myfeeder.repository.FolderRepository;
import org.bartram.myfeeder.scheduler.FeedPollingScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpmlImportServiceTest {

    @Mock private OpmlService opmlService;
    @Mock private FeedRepository feedRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private FolderService folderService;
    @Mock private FeedPollingScheduler feedPollingScheduler;

    private OpmlImportService importService;

    @BeforeEach
    void setUp() {
        // Activate transaction synchronization so registerSynchronization() works in unit tests
        TransactionSynchronizationManager.initSynchronization();
        var properties = new MyfeederProperties();
        properties.getPolling().setDefaultIntervalMinutes(15);
        importService = new OpmlImportService(
                opmlService, feedRepository, folderRepository,
                folderService, feedPollingScheduler, properties);
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    /** Helper to simulate transaction commit — triggers afterCommit callbacks. */
    private void simulateCommit() {
        TransactionSynchronizationUtils.triggerAfterCommit();
    }

    @Test
    void shouldCreateNewFeedsFromOpml() {
        var opmlFeed = new OpmlFeed("New Feed", "https://example.com/feed.xml", "https://example.com", null);
        when(opmlService.parseOpml(any(InputStream.class))).thenReturn(List.of(opmlFeed));
        when(feedRepository.findAll()).thenReturn(List.of());
        when(folderRepository.findAll()).thenReturn(List.of());
        when(feedRepository.save(any(Feed.class))).thenAnswer(i -> {
            Feed f = i.getArgument(0);
            f.setId(1L);
            return f;
        });

        OpmlImportResult result = importService.importOpml(InputStream.nullInputStream());

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(0);
        assertThat(result.total()).isEqualTo(1);

        ArgumentCaptor<Feed> captor = ArgumentCaptor.forClass(Feed.class);
        verify(feedRepository).save(captor.capture());
        Feed saved = captor.getValue();
        assertThat(saved.getUrl()).isEqualTo("https://example.com/feed.xml");
        assertThat(saved.getTitle()).isEqualTo("New Feed");
        assertThat(saved.getSiteUrl()).isEqualTo("https://example.com");
        assertThat(saved.getPollIntervalMinutes()).isEqualTo(15);

        // Scheduler registration happens after commit
        verify(feedPollingScheduler, never()).registerFeed(any());
        simulateCommit();
        verify(feedPollingScheduler).registerFeed(saved);
    }

    @Test
    void shouldUpdateExistingFeedByUrl() {
        var existing = new Feed();
        existing.setId(1L);
        existing.setUrl("https://example.com/feed.xml");
        existing.setTitle("Old Title");

        var opmlFeed = new OpmlFeed("New Title", "https://example.com/feed.xml", "https://example.com", null);
        when(opmlService.parseOpml(any(InputStream.class))).thenReturn(List.of(opmlFeed));
        when(feedRepository.findAll()).thenReturn(List.of(existing));
        when(folderRepository.findAll()).thenReturn(List.of());
        when(feedRepository.save(any(Feed.class))).thenAnswer(i -> i.getArgument(0));

        OpmlImportResult result = importService.importOpml(InputStream.nullInputStream());
        simulateCommit();

        assertThat(result.created()).isEqualTo(0);
        assertThat(result.updated()).isEqualTo(1);

        verify(feedRepository).save(existing);
        assertThat(existing.getTitle()).isEqualTo("New Title");
        // Should NOT register with scheduler for existing feeds
        verify(feedPollingScheduler, never()).registerFeed(any());
    }

    @Test
    void shouldCreateFolderWhenNotExisting() {
        var opmlFeed = new OpmlFeed("Feed", "https://example.com/feed.xml", null, "Tech");
        var newFolder = new Folder();
        newFolder.setId(1L);
        newFolder.setName("Tech");

        when(opmlService.parseOpml(any(InputStream.class))).thenReturn(List.of(opmlFeed));
        when(feedRepository.findAll()).thenReturn(List.of());
        when(folderRepository.findAll()).thenReturn(List.of());
        when(folderService.create("Tech")).thenReturn(newFolder);
        when(feedRepository.save(any(Feed.class))).thenAnswer(i -> {
            Feed f = i.getArgument(0);
            f.setId(1L);
            return f;
        });

        importService.importOpml(InputStream.nullInputStream());

        verify(folderService).create("Tech");
        ArgumentCaptor<Feed> captor = ArgumentCaptor.forClass(Feed.class);
        verify(feedRepository).save(captor.capture());
        assertThat(captor.getValue().getFolderId()).isEqualTo(1L);
    }

    @Test
    void shouldReuseFolderByNameCaseInsensitive() {
        var existingFolder = new Folder();
        existingFolder.setId(5L);
        existingFolder.setName("tech");

        var opmlFeed = new OpmlFeed("Feed", "https://example.com/feed.xml", null, "Tech");
        when(opmlService.parseOpml(any(InputStream.class))).thenReturn(List.of(opmlFeed));
        when(feedRepository.findAll()).thenReturn(List.of());
        when(folderRepository.findAll()).thenReturn(List.of(existingFolder));
        when(feedRepository.save(any(Feed.class))).thenAnswer(i -> {
            Feed f = i.getArgument(0);
            f.setId(1L);
            return f;
        });

        importService.importOpml(InputStream.nullInputStream());

        verify(folderService, never()).create(any());
        ArgumentCaptor<Feed> captor = ArgumentCaptor.forClass(Feed.class);
        verify(feedRepository).save(captor.capture());
        assertThat(captor.getValue().getFolderId()).isEqualTo(5L);
    }
}
