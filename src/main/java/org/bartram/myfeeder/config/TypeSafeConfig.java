package org.bartram.myfeeder.config;

import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.autoconfigure.TypeSafeProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
}
