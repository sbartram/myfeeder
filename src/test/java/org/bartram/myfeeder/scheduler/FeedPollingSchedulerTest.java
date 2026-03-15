package org.bartram.myfeeder.scheduler;

import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.repository.FeedRepository;
import org.bartram.myfeeder.service.FeedPollingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import static org.mockito.ArgumentMatchers.any;
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
}
