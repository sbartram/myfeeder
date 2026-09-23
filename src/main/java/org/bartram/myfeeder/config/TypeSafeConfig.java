package org.bartram.myfeeder.config;

import io.github.resilience4j.common.retry.configuration.RetryConfigCustomizer;
import io.github.resilience4j.core.IntervalBiFunction;
import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.autoconfigure.TypeSafeProperties;
import org.springaicommunity.typesafe.exception.TypeSafeRateLimitException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.ReactorClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Objects;

/**
 * Owns the {@link TypeSafeClient} bean instead of letting the TypeSafe starter create it.
 * The starter's own bean asserts that {@code spring.ai.typesafe.api-key} has text, so a blank
 * {@code MYFEEDER_TYPESAFE_API_KEY} would crash the pod on startup. Defining the bean here makes
 * the starter's {@code @ConditionalOnMissingBean} back off: a missing or blank key only disables
 * Jev calls, never the app.
 *
 * <p>The client's transport is a Jev-only Reactor Netty request factory with its own timeouts.
 * It is set on a {@code clone()} of the auto-configured {@link RestClient.Builder}, which keeps
 * the User-Agent customizer and observations without changing the transport of any other bean
 * that shares the builder.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TypeSafeProperties.class) // the starter's auto-config is skipped when api-key is absent
public class TypeSafeConfig {

    static final Duration JEV_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final long MAX_RETRY_AFTER_MS = 10_000L; // D-07 cap on a server-supplied Retry-After

    @Bean
    TypeSafeClient typeSafeClient(TypeSafeProperties properties, RestClient.Builder restClientBuilder) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            // Fixed text only: never the key, its length or its prefix.
            log.info("TypeSafe Jev not configured; interest scoring disabled");
        }
        ReactorClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.reactor()
                .build(HttpClientSettings.defaults().withTimeouts(JEV_CONNECT_TIMEOUT, properties.getTimeout()));
        return TypeSafeClient.builder()
                // Supplier overload: a blank key is legal at build time (the String overload asserts text)
                .apiKey(() -> Objects.requireNonNullElse(properties.getApiKey(), ""))
                // Set explicitly so the SDK never falls back to its TYPESAFE_* environment variables
                .baseUrl(properties.getBaseUrl())
                .defaultModel(properties.getModel())
                .timeout(properties.getTimeout())
                .retryPolicy(properties.toRetryPolicy())
                .restClientBuilder(restClientBuilder.clone().requestFactory(requestFactory))
                .build();
    }

    /**
     * Wait between "jev" retry attempts. Keeps Resilience4j as the one retry layer without losing the
     * server's Retry-After hint (D-07): a 429 carrying {@code retryAfterMs} waits that long, clamped to
     * [0, {@link #MAX_RETRY_AFTER_MS}]; anything else backs off exponentially from
     * {@code resilience4j.retry.instances.jev.wait-duration} (1s, then 2s). Runs after YAML binding and
     * replaces the wait-duration interval function.
     */
    @Bean
    RetryConfigCustomizer jevRetryInterval(Environment environment) {
        // Binder, not @Value Duration: ApplicationContextRunner has no conversion service for Duration
        Duration base = Binder.get(environment)
                .bind("resilience4j.retry.instances.jev.wait-duration", Duration.class)
                .orElse(Duration.ofSeconds(1));
        IntervalBiFunction<Object> interval = (attempt, either) -> {
            if (either.isLeft() && either.getLeft() instanceof TypeSafeRateLimitException rateLimit
                    && rateLimit.retryAfterMs() != null) {
                return Math.max(0L, Math.min(rateLimit.retryAfterMs(), MAX_RETRY_AFTER_MS));
            }
            return base.toMillis() * (1L << (attempt - 1)); // attempt 1 waits the base, attempt 2 twice it
        };
        return RetryConfigCustomizer.of("jev", builder -> builder.intervalBiFunction(interval));
    }
}
