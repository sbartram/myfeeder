package org.bartram.myfeeder.integration;

import org.springaicommunity.typesafe.question.Question;

import java.util.Map;

public interface JevApiClient {

    /**
     * True when the TypeSafe key has text. Never throws, never calls Jev, and is not wrapped by
     * the circuit breaker or retry. The interest status endpoint (D-04) and the Phase 4 scorer
     * gate both read it, so it is the single "configured" rule.
     */
    boolean isConfigured();

    /**
     * Sends one TypeSafe Jev call. The client is a generic pass-through: it knows nothing about
     * articles or topics, and callers build the SDK {@code Noul}/{@code Score} questions themselves.
     *
     * <p>The state is sent as a JSON object in the caller's iteration order, with null values
     * dropped. An empty state is sent as {@code {}}. The caller's map is never modified.
     *
     * <p>Typed SDK exceptions ({@code org.springaicommunity.typesafe.exception.*}) propagate
     * unchanged. A timeout arrives as the base {@code TypeSafeApiConnectionException}, not as
     * {@code TypeSafeApiTimeoutException}. A requested question with no answer throws
     * {@code TypeSafeMissingAnswerException}; an answer of the wrong kind throws
     * {@code TypeSafeAnswerTypeException}. Once the circuit breaker is in place,
     * {@code CallNotPermittedException} means the breaker is open.
     *
     * @throws JevNotConfiguredException when the TypeSafe key is not set
     */
    JevJudgment judge(Map<String, ?> state, Map<String, ? extends Question> questions);
}
