package org.bartram.myfeeder.scheduler;

import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.repository.FeedRepository;
import org.bartram.myfeeder.service.FeedPollingService;
import org.bartram.myfeeder.service.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedPollingSchedulerTest {

    @Mock private FeedRepository feedRepository;
    @Mock private FeedPollingService feedPollingService;
    @Mock private TaskScheduler taskScheduler;
    @Mock private MyfeederProperties properties;
    @Mock private ScheduledFuture<?> scheduledFuture;

    private FeedPollingScheduler scheduler;

    @BeforeEach
    void setUp() {
        var polling = new MyfeederProperties.Polling();
        polling.setMaxIntervalMinutes(1440);
        polling.setBackoffThreshold(5);
        lenient().when(properties.getPolling()).thenReturn(polling);

        scheduler = new FeedPollingScheduler(feedRepository, feedPollingService, taskScheduler, properties);
    }

    @Test
    void shouldRegisterAllFeedsOnStartup() {
        var feed = new Feed();
        feed.setId(1L);
        feed.setPollIntervalMinutes(15);
        feed.setErrorCount(0);
        when(feedRepository.findAll()).thenReturn(List.of(feed));
        when(taskScheduler.scheduleAtFixedRate(any(Runnable.class), any(Duration.class)))
                .thenAnswer(invocation -> scheduledFuture);

        scheduler.onStartup();

        verify(taskScheduler).scheduleAtFixedRate(any(Runnable.class), eq(Duration.ofMinutes(15)));
    }

    @Test
    void shouldComputeBackoffInterval() {
        var feed = new Feed();
        feed.setId(2L);
        feed.setPollIntervalMinutes(15);
        feed.setErrorCount(10); // 10 errors, threshold 5 -> 2^(10/5) = 4x
        when(taskScheduler.scheduleAtFixedRate(any(Runnable.class), any(Duration.class)))
                .thenAnswer(invocation -> scheduledFuture);

        scheduler.registerFeed(feed);

        // 15 * 4 = 60 minutes
        verify(taskScheduler).scheduleAtFixedRate(any(Runnable.class), eq(Duration.ofMinutes(60)));
    }

    @Test
    void shouldCancelExistingTaskBeforeReRegistering() {
        var feed = new Feed();
        feed.setId(3L);
        feed.setPollIntervalMinutes(15);
        feed.setErrorCount(0);
        when(taskScheduler.scheduleAtFixedRate(any(Runnable.class), any(Duration.class)))
                .thenAnswer(invocation -> scheduledFuture);

        scheduler.registerFeed(feed);
        scheduler.registerFeed(feed); // re-register

        verify(scheduledFuture).cancel(false);
    }

    @Test
    void backoffEngagesAfterErrorsCrossThreshold() {
        Feed feed = feedWith(1L, 15, 0);
        when(taskScheduler.scheduleAtFixedRate(any(Runnable.class), any(Duration.class)))
                .thenReturn(mock(ScheduledFuture.class));
        when(taskScheduler.scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class)))
                .thenReturn(mock(ScheduledFuture.class));
        when(taskScheduler.getClock()).thenReturn(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        scheduler.registerFeed(feed);

        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).scheduleAtFixedRate(taskCaptor.capture(), eq(Duration.ofMinutes(15)));

        Feed failing = feedWith(1L, 15, 5); // errorCount == backoffThreshold -> multiplier 2
        when(feedRepository.findById(1L)).thenReturn(Optional.of(failing));

        taskCaptor.getValue().run();

        verify(feedPollingService).pollFeed(1L);
        verify(taskScheduler).scheduleAtFixedRate(any(Runnable.class),
                eq(Instant.EPOCH.plus(Duration.ofMinutes(30))), eq(Duration.ofMinutes(30)));
    }

    @Test
    void noRescheduleWhenIntervalUnchanged() {
        Feed feed = feedWith(1L, 15, 0);
        doReturn(mock(ScheduledFuture.class)).when(taskScheduler)
                .scheduleAtFixedRate(any(Runnable.class), any(Duration.class));
        scheduler.registerFeed(feed);
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).scheduleAtFixedRate(taskCaptor.capture(), eq(Duration.ofMinutes(15)));
        when(feedRepository.findById(1L)).thenReturn(Optional.of(feedWith(1L, 15, 0)));

        taskCaptor.getValue().run();

        verify(feedPollingService).pollFeed(1L);
        verify(taskScheduler, never())
                .scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class));
    }

    @Test
    void cancelsWhenFeedDeletedDuringPoll() {
        Feed feed = feedWith(1L, 15, 0);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(taskScheduler)
                .scheduleAtFixedRate(any(Runnable.class), any(Duration.class));
        scheduler.registerFeed(feed);
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).scheduleAtFixedRate(taskCaptor.capture(), eq(Duration.ofMinutes(15)));
        doThrow(new NotFoundException("Feed not found: 1")).when(feedPollingService).pollFeed(1L);

        taskCaptor.getValue().run();

        verify(future).cancel(false);
        verify(feedRepository, never()).findById(anyLong());
    }

    private Feed feedWith(Long id, int intervalMinutes, int errorCount) {
        Feed feed = new Feed();
        feed.setId(id);
        feed.setTitle("Feed " + id);
        feed.setPollIntervalMinutes(intervalMinutes);
        feed.setErrorCount(errorCount);
        return feed;
    }
}
