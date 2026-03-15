package org.bartram.myfeeder.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.repository.FeedRepository;
import org.bartram.myfeeder.service.FeedPollingService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

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

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        feedRepository.findAll().forEach(this::registerFeed);
        log.info("Registered polling tasks for {} feeds", scheduledTasks.size());
    }

    public void registerFeed(Feed feed) {
        cancelFeed(feed.getId());

        Duration interval = computeEffectiveInterval(feed);
        ScheduledFuture<?> future = taskScheduler.scheduleAtFixedRate(
                () -> feedPollingService.pollFeed(feed.getId()),
                interval
        );

        scheduledTasks.put(feed.getId(), future);
        log.info("Scheduled polling for feed '{}' every {} minutes", feed.getTitle(), interval.toMinutes());
    }

    public void cancelFeed(Long feedId) {
        ScheduledFuture<?> existing = scheduledTasks.remove(feedId);
        if (existing != null) {
            existing.cancel(false);
        }
    }

    private Duration computeEffectiveInterval(Feed feed) {
        int threshold = properties.getPolling().getBackoffThreshold();
        int maxMinutes = properties.getPolling().getMaxIntervalMinutes();

        if (feed.getErrorCount() >= threshold) {
            int multiplier = (int) Math.pow(2, feed.getErrorCount() / threshold);
            int backoffMinutes = Math.min(feed.getPollIntervalMinutes() * multiplier, maxMinutes);
            return Duration.ofMinutes(backoffMinutes);
        }

        return Duration.ofMinutes(feed.getPollIntervalMinutes());
    }
}
