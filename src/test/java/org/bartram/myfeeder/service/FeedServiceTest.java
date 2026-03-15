package org.bartram.myfeeder.service;

import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.parser.FeedParser;
import org.bartram.myfeeder.repository.FeedRepository;
import org.bartram.myfeeder.scheduler.FeedPollingScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

    @Mock private FeedRepository feedRepository;
    @Mock private FeedParser feedParser;
    @Mock private RestClient.Builder restClientBuilder;
    @Mock private MyfeederProperties properties;
    @Mock private FeedPollingScheduler feedPollingScheduler;

    @InjectMocks
    private FeedService feedService;

    @Test
    void shouldReturnAllFeeds() {
        var feed = new Feed();
        feed.setTitle("Test");
        when(feedRepository.findAll()).thenReturn(List.of(feed));

        var result = feedService.findAll();
        assertThat(result).hasSize(1);
    }

    @Test
    void shouldFindFeedById() {
        var feed = new Feed();
        feed.setId(1L);
        when(feedRepository.findById(1L)).thenReturn(Optional.of(feed));

        var result = feedService.findById(1L);
        assertThat(result).isPresent();
    }

    @Test
    void shouldDeleteFeed() {
        feedService.delete(1L);
        verify(feedPollingScheduler).cancelFeed(1L);
        verify(feedRepository).deleteById(1L);
    }

    @Test
    void shouldUpdateFeedTitle() {
        var feed = new Feed();
        feed.setId(1L);
        feed.setTitle("Old");
        feed.setPollIntervalMinutes(15);
        when(feedRepository.findById(1L)).thenReturn(Optional.of(feed));
        when(feedRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var updates = new Feed();
        updates.setTitle("New Title");
        var result = feedService.update(1L, updates);

        assertThat(result.getTitle()).isEqualTo("New Title");
        verify(feedPollingScheduler).registerFeed(result);
    }

    @Test
    void shouldCancelPollingOnDelete() {
        feedService.delete(42L);
        verify(feedPollingScheduler).cancelFeed(42L);
    }

    @Test
    void shouldRegisterFeedOnUpdate() {
        var feed = new Feed();
        feed.setId(5L);
        feed.setTitle("Existing");
        feed.setPollIntervalMinutes(30);
        when(feedRepository.findById(5L)).thenReturn(Optional.of(feed));
        when(feedRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var updates = new Feed();
        updates.setPollIntervalMinutes(60);
        var result = feedService.update(5L, updates);

        verify(feedPollingScheduler).registerFeed(result);
    }
}
