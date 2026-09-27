package org.bartram.myfeeder.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Logs the {@code jev} retry and circuit-breaker events (D-15). These lines are the launch
 * evidence channel for D-05/D-06: the rollout watch (07-07) greps them to prove the breaker stayed
 * CLOSED during the backfill and that 429s were absorbed by the retry. No metric is added.
 *
 * <p>The registries are get-or-create by name, so {@code circuitBreaker("jev")} and
 * {@code retry("jev")} return the same instances the {@code @CircuitBreaker}/{@code @Retry}
 * aspects on {@code JevApiClientImpl.judge} use. The logger only subscribes to their event
 * publishers and never reconfigures them.
 *
 * <p>Fixed text only (Phase 2 D-06): each line carries the exception's simple class name and
 * numbers, never an exception message, a response body or the API key. Transitions are logged
 * with {@code StateTransition.name()} (e.g. {@code CLOSED_TO_OPEN}) because its
 * {@code toString()} is prose ("State transition from CLOSED to OPEN").
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class JevEventLogging {

    public JevEventLogging(CircuitBreakerRegistry circuitBreakerRegistry, RetryRegistry retryRegistry) {
        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("jev");
        Retry retry = retryRegistry.retry("jev");

        retry.getEventPublisher()
                .onRetry(e -> log.info("Jev retry attempt {} after {} (waiting {} ms)",
                        e.getNumberOfRetryAttempts(), simpleName(e.getLastThrowable()),
                        e.getWaitInterval().toMillis()))
                // Fires only when a retryable call has used all its attempts.
                .onError(e -> log.warn("Jev retries exhausted after {} attempts: {}",
                        e.getNumberOfRetryAttempts(), simpleName(e.getLastThrowable())));

        breaker.getEventPublisher()
                .onStateTransition(e -> log.warn("Jev circuit breaker {} (failure rate {}%, slow-call rate {}%)",
                        e.getStateTransition().name(), breaker.getMetrics().getFailureRate(),
                        breaker.getMetrics().getSlowCallRate()));
    }

    private static String simpleName(Throwable t) {
        return t == null ? "none" : t.getClass().getSimpleName();
    }
}
