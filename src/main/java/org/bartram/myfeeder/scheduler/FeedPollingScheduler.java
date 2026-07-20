package org.bartram.myfeeder.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.event.FeedDeletedEvent;
import org.bartram.myfeeder.event.FeedSavedEvent;
import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.repository.FeedRepository;
import org.bartram.myfeeder.service.FeedPollingService;
import org.bartram.myfeeder.service.NotFoundException;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeedPollingScheduler {

    private final FeedRepository feedRepository;
    private final FeedPollingService feedPollingService;
    private final TaskScheduler taskScheduler;
    private final MyfeederProperties properties;

    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final Map<Long, Duration> currentIntervals = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        feedRepository.findAll().forEach(this::registerFeed);
        log.info("Registered polling tasks for {} feeds", scheduledTasks.size());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onFeedSaved(FeedSavedEvent event) {
        registerFeed(event.feed());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onFeedDeleted(FeedDeletedEvent event) {
        cancelFeed(event.feedId());
    }

    public void registerFeed(Feed feed) {
        cancelFeed(feed.getId());

        Duration interval = computeEffectiveInterval(feed);
        currentIntervals.put(feed.getId(), interval);
        scheduledTasks.put(feed.getId(),
                taskScheduler.scheduleAtFixedRate(() -> pollAndAdjust(feed.getId()), interval));
        log.info("Scheduled polling for feed '{}' every {} minutes", feed.getTitle(), interval.toMinutes());
    }

    public void cancelFeed(Long feedId) {
        currentIntervals.remove(feedId);
        ScheduledFuture<?> existing = scheduledTasks.remove(feedId);
        if (existing != null) {
            existing.cancel(false);
        }
    }

    /**
     * Polls, then re-reads the feed's error state; when the effective interval changed
     * (backoff engaged or cleared) the fixed-rate task replaces itself with one at the new
     * interval whose first run is one full interval away. A concurrent external
     * registerFeed/cancelFeed can in theory race this replacement; worst case is
     * redundant polling until the feed is next edited or deleted; acceptable for a
     * single-user deployment.
     */
    private void pollAndAdjust(Long feedId) {
        try {
            feedPollingService.pollFeed(feedId);
        } catch (NotFoundException e) {
            cancelFeed(feedId);
            return;
        }
        try {
            feedRepository.findById(feedId).ifPresent(feed -> {
                Duration desired = computeEffectiveInterval(feed);
                if (!desired.equals(currentIntervals.get(feedId))) {
                    cancelFeed(feedId);
                    currentIntervals.put(feedId, desired);
                    scheduledTasks.put(feedId, taskScheduler.scheduleAtFixedRate(
                            () -> pollAndAdjust(feedId),
                            taskScheduler.getClock().instant().plus(desired),
                            desired));
                    log.info("Adjusted polling interval for feed '{}' to {} minutes",
                            feed.getTitle(), desired.toMinutes());
                }
            });
        } catch (Exception e) {
            log.warn("Interval re-evaluation failed for feed {}; keeping current schedule", feedId, e);
        }
    }

    private Duration computeEffectiveInterval(Feed feed) {
        int threshold = properties.getPolling().getBackoffThreshold();
        int maxMinutes = properties.getPolling().getMaxIntervalMinutes();

        if (feed.getErrorCount() >= threshold) {
            // Cap the exponent (result is clamped to maxMinutes anyway) and use long arithmetic so
            // the doubling never overflows to a negative interval — a negative Duration makes
            // scheduleAtFixedRate throw and crashes startup for feeds with many errors.
            int exponent = Math.min(feed.getErrorCount() / threshold, 30);
            long multiplier = 1L << exponent;
            long backoffMinutes = Math.min((long) feed.getPollIntervalMinutes() * multiplier, maxMinutes);
            return Duration.ofMinutes(backoffMinutes);
        }

        return Duration.ofMinutes(feed.getPollIntervalMinutes());
    }
}
