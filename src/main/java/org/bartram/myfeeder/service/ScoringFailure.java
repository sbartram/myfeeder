package org.bartram.myfeeder.service;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.bartram.myfeeder.integration.JevNotConfiguredException;
import org.springaicommunity.typesafe.exception.TypeSafeApiConnectionException;
import org.springaicommunity.typesafe.exception.TypeSafeApiException;
import org.springaicommunity.typesafe.exception.TypeSafeAuthenticationException;
import org.springaicommunity.typesafe.exception.TypeSafeInternalServerException;
import org.springaicommunity.typesafe.exception.TypeSafePermissionDeniedException;
import org.springaicommunity.typesafe.exception.TypeSafeRateLimitException;

/**
 * Classifies a scoring failure (SCOR-07, D-19) and describes it in fixed text.
 *
 * <p>Transient failures write nothing and use no attempt: the outage, rate limit or missing key is
 * not the article's fault, and the circuit breaker pauses the sweep. Everything else is permanent,
 * including unknown errors, so a failure nobody anticipated is bounded at three billed attempts
 * instead of re-billing every sweep.
 */
final class ScoringFailure {

    private ScoringFailure() {
    }

    /**
     * True for 429, 5xx (including overloaded), connection failures and timeouts, an open breaker,
     * a missing key, and 401/403 (D-08: the breaker records those and opens, so they get no special
     * surfacing here).
     */
    static boolean isTransient(Throwable e) {
        return e instanceof TypeSafeRateLimitException
                || e instanceof TypeSafeInternalServerException
                || e instanceof TypeSafeApiConnectionException
                || e instanceof CallNotPermittedException
                || e instanceof JevNotConfiguredException
                || e instanceof TypeSafeAuthenticationException
                || e instanceof TypeSafePermissionDeniedException;
    }

    /**
     * Fixed text for {@code last_error} and logs: the simple class name, plus the HTTP status and
     * request id for API errors. Never the exception message or body, which can echo request
     * content (Phase 2 D-06).
     */
    static String describe(Throwable e) {
        String name = e.getClass().getSimpleName();
        if (e instanceof TypeSafeApiException api) {
            return name + " (HTTP " + api.status() + ", requestId " + api.requestId() + ")";
        }
        return name;
    }
}
