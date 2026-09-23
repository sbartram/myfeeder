package org.bartram.myfeeder.integration;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.autoconfigure.TypeSafeProperties;
import org.springaicommunity.typesafe.exception.TypeSafeAuthenticationException;
import org.springaicommunity.typesafe.exception.TypeSafePermissionDeniedException;
import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.question.Question;
import org.springaicommunity.typesafe.question.Score;
import org.springaicommunity.typesafe.response.SystemOneResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
public class JevApiClientImpl implements JevApiClient {

    private final TypeSafeClient client;
    private final TypeSafeProperties properties;

    public JevApiClientImpl(TypeSafeClient client, TypeSafeProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    // No resilience annotations: a configuration check must never touch the breaker (D-04)
    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(properties.getApiKey());
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new JevNotConfiguredException();
        }
    }

    // No fallbackMethod: typed SDK exceptions and CallNotPermittedException propagate unchanged (D-08)
    @CircuitBreaker(name = "jev")
    @Retry(name = "jev")
    @Override
    public JevJudgment judge(Map<String, ?> state, Map<String, ? extends Question> questions) {
        requireConfigured();
        Assert.notNull(state, "state must not be null");
        Assert.notEmpty(questions, "questions must not be empty");

        // D-04: keep the caller's order, drop nulls, never mutate the caller's map; always a JSON object
        Map<String, Object> cleaned = new LinkedHashMap<>(state);
        cleaned.values().removeIf(Objects::isNull);

        SystemOneResponse response;
        try {
            response = client.systemOne(cleaned, questions);
        } catch (TypeSafeAuthenticationException | TypeSafePermissionDeniedException e) {
            // D-06: status and requestId only. The exception message and body can echo request details.
            log.warn("TypeSafe rejected the API key (status {}, requestId {})", e.status(), e.requestId());
            throw e;
        }

        // Presence and kind check for every requested question; the SDK accessors throw
        // TypeSafeMissingAnswerException / TypeSafeAnswerTypeException. Unrequested answers are ignored.
        questions.forEach((name, question) -> {
            if (question instanceof Noul) {
                response.noul(name);
            } else if (question instanceof Score) {
                response.score(name);
            } else {
                response.answer(name);
            }
        });

        return JevJudgment.from(response);
    }
}
