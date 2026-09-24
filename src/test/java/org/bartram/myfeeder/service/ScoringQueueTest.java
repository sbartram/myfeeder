package org.bartram.myfeeder.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.repository.ArticleScoreStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Pins ScoringQueue's overflow, dedup and failure containment against a real 1-thread executor. */
@ExtendWith(MockitoExtension.class)
class ScoringQueueTest {

    @Mock private ArticleScoringService scorer;
    @Mock private ArticleScoreStore store;

    private final List<ThreadPoolTaskExecutor> executors = new ArrayList<>();
    private final CountDownLatch release = new CountDownLatch(1);
    private final Logger queueLogger = (Logger) LoggerFactory.getLogger(ScoringQueue.class);
    private ListAppender<ILoggingEvent> logs;

    @AfterEach
    void tearDown() {
        release.countDown();
        executors.forEach(ThreadPoolTaskExecutor::shutdown);
        if (logs != null) {
            queueLogger.detachAppender(logs);
        }
    }

    @Test
    void rejectedArticleIsReleasedForTheSweep() {
        captureLogs();
        blockWorkerOn(1L);
        ScoringQueue queue = queue(pool(1));

        // 1 runs (blocked), 2 fills the one queue slot, 3 is rejected
        assertThat(queue.submit(List.of(1L, 2L, 3L))).isEqualTo(2);

        assertThat(queue.isInFlight(1L)).isTrue();
        assertThat(queue.isInFlight(2L)).isTrue();
        assertThat(queue.isInFlight(3L)).isFalse();
        assertThat(logs.list)
                .filteredOn(e -> e.getLevel() == Level.WARN)
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("Scoring queue full; dropped 1 articles (the sweep will retry them)");

        release.countDown();
        awaitTrue(() -> !queue.isInFlight(1L) && !queue.isInFlight(2L));
        assertThat(queue.submit(List.of(3L))).isEqualTo(1);
        verify(scorer, timeout(5000)).score(3L);
    }

    @Test
    void duplicateSubmitWhileQueuedIsIgnored() {
        blockWorkerOn(4L);
        ScoringQueue queue = queue(pool(10));
        queue.submit(List.of(4L));

        assertThat(queue.submit(List.of(5L))).isEqualTo(1);
        assertThat(queue.submit(List.of(5L))).isZero();

        release.countDown();
        awaitTrue(() -> !queue.isInFlight(5L));
        verify(scorer, times(1)).score(5L);
    }

    @Test
    void remainingCapacityTracksTheQueue() {
        blockWorkerOn(1L);
        ScoringQueue queue = queue(pool(3));

        queue.submit(List.of(1L, 2L, 3L)); // 1 runs, 2 and 3 wait

        assertThat(queue.remainingCapacity()).isEqualTo(1);
    }

    @Test
    void scorerExceptionIsContainedAndReleasesTheId() {
        doThrow(new IllegalStateException("scorer bug")).when(scorer).score(7L);
        ScoringQueue queue = queue(pool(10));

        assertThat(queue.submit(List.of(7L))).isEqualTo(1);
        verify(scorer, timeout(5000)).score(7L);
        awaitTrue(() -> !queue.isInFlight(7L));

        assertThat(queue.submit(List.of(7L))).isEqualTo(1);
        verify(scorer, timeout(5000).times(2)).score(7L);
    }

    @Test
    void inlineExecutorRunsImmediatelyAndReportsUnboundedRoom() {
        ScoringQueue queue = queue(new SyncTaskExecutor());

        assertThat(queue.submit(List.of(9L))).isEqualTo(1);

        verify(scorer).score(9L);
        assertThat(queue.isInFlight(9L)).isFalse();
        assertThat(queue.remainingCapacity()).isEqualTo(Integer.MAX_VALUE);
    }

    private ScoringQueue queue(TaskExecutor executor) {
        return new ScoringQueue(executor, scorer, store, new MyfeederProperties());
    }

    private ThreadPoolTaskExecutor pool(int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("jev-score-test-");
        executor.initialize();
        executors.add(executor);
        return executor;
    }

    /** The scorer holds the single worker while scoring {@code blockingId}; other ids return at once. */
    private void blockWorkerOn(long blockingId) {
        doAnswer(inv -> {
            if ((long) inv.getArgument(0) == blockingId) {
                release.await(10, TimeUnit.SECONDS);
            }
            return null;
        }).when(scorer).score(anyLong());
    }

    private void captureLogs() {
        logs = new ListAppender<>();
        logs.start();
        queueLogger.addAppender(logs);
    }

    private static void awaitTrue(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            assertThat(System.nanoTime()).as("condition not met within 5 s").isLessThan(deadline);
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
    }
}
