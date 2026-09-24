package org.bartram.myfeeder.config;

import io.github.resilience4j.common.retry.configuration.RetryConfigCustomizer;
import io.github.resilience4j.core.functions.Either;
import io.github.resilience4j.retry.RetryConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.autoconfigure.TypeSafeAutoConfiguration;
import org.springaicommunity.typesafe.autoconfigure.TypeSafeProperties;
import org.springaicommunity.typesafe.exception.TypeSafeApiConnectionException;
import org.springaicommunity.typesafe.exception.TypeSafeInternalServerException;
import org.springaicommunity.typesafe.exception.TypeSafeRateLimitException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.ConnectException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the app-owned {@link TypeSafeClient} bean: the Spring context must start whether the
 * TypeSafe key is absent, blank, whitespace or set, with the starter's auto-configuration loaded
 * (that is what would crash on a blank key if the app-owned bean were removed). Also checks the
 * keyless INFO line, that the key never reaches the output, and the {@code spring.ai.typesafe}
 * pins in both application.yaml files. Runs without Docker: only the TypeSafe, RestClient and
 * HTTP-client auto-configurations are loaded.
 */
@ExtendWith(OutputCaptureExtension.class)
class TypeSafeConfigTest {

    private static final String KEYLESS_LINE = "TypeSafe Jev not configured; interest scoring disabled";
    private static final String FAKE_KEY = "sk-test-LEAKCHECK";

    private static final AutoConfigurations AUTO_CONFIGURATIONS = AutoConfigurations.of(
            TypeSafeAutoConfiguration.class, RestClientAutoConfiguration.class,
            HttpClientAutoConfiguration.class, ImperativeHttpClientAutoConfiguration.class);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AUTO_CONFIGURATIONS)
            .withConfiguration(UserConfigurations.of(TypeSafeConfig.class));

    @Test
    void absentKeyStartsWithAppOwnedClient() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBeansOfType(TypeSafeClient.class)).hasSize(1);
        });
    }

    @Test
    void blankKeyStartsDespiteStarterAutoConfiguration() {
        runner.withPropertyValues("spring.ai.typesafe.api-key=")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.getBeansOfType(TypeSafeClient.class)).hasSize(1);
                });
    }

    @Test
    void whitespaceKeyIsTreatedAsNotConfigured(CapturedOutput output) {
        // withPropertyValues trims values, so the three-space key goes in through its own property source.
        runner.withInitializer(ctx -> ctx.getEnvironment().getPropertySources().addFirst(
                        new MapPropertySource("whitespace-key", Map.of("spring.ai.typesafe.api-key", "   "))))
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.getBeansOfType(TypeSafeClient.class)).hasSize(1);
                });
        assertThat(output.toString()).contains(KEYLESS_LINE);
    }

    @Test
    void setKeyStartsWithPinnedModel() {
        runner.withPropertyValues("spring.ai.typesafe.api-key=" + FAKE_KEY, "spring.ai.typesafe.model=jev-1.13.0")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.getBeansOfType(TypeSafeClient.class)).hasSize(1);
                    assertThat(ctx.getBean(TypeSafeClient.class).defaultModel()).isEqualTo("jev-1.13.0");
                });
    }

    @Test
    void starterAloneFailsOnBlankKey() {
        // Negative control: without the app-owned bean the starter's own client asserts the key has text.
        new ApplicationContextRunner()
                .withConfiguration(AUTO_CONFIGURATIONS)
                .withPropertyValues("spring.ai.typesafe.api-key=")
                .run(ctx -> {
                    assertThat(ctx.getStartupFailure()).isNotNull();
                    Throwable root = ctx.getStartupFailure();
                    while (root.getCause() != null) {
                        root = root.getCause();
                    }
                    assertThat(root.getMessage()).contains("No API key configured");
                });
    }

    @Test
    void keylessStartupLogsOneInfoLine(CapturedOutput output) {
        runner.withPropertyValues("spring.ai.typesafe.api-key=")
                .run(ctx -> assertThat(ctx).hasNotFailed());
        assertThat(occurrences(output.toString(), KEYLESS_LINE)).isEqualTo(1);

        runner.withPropertyValues("spring.ai.typesafe.api-key=" + FAKE_KEY)
                .run(ctx -> assertThat(ctx).hasNotFailed());
        assertThat(occurrences(output.toString(), KEYLESS_LINE)).isEqualTo(1);
    }

    @Test
    void configuredKeyNeverAppearsInOutput(CapturedOutput output) {
        runner.withPropertyValues("spring.ai.typesafe.api-key=" + FAKE_KEY)
                .run(ctx -> assertThat(ctx).hasNotFailed());
        assertThat(output.toString()).doesNotContain("LEAKCHECK").doesNotContain("sk-test");
    }

    @Test
    void mainYamlPinsModelTimeoutAndDisablesSdkRetries() throws IOException {
        // Loaded from disk: src/test/resources/application.yaml shadows the main one on the classpath.
        // addLast so the blank MYFEEDER_TYPESAFE_API_KEY below wins even if a developer exports a real key.
        PropertySource<?> mainYaml = loadYaml("src/main/resources/application.yaml");

        runner.withInitializer(ctx -> ctx.getEnvironment().getPropertySources().addLast(mainYaml))
                .withPropertyValues("MYFEEDER_TYPESAFE_API_KEY=")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    TypeSafeProperties properties = ctx.getBean(TypeSafeProperties.class);
                    assertThat(properties.getModel()).isEqualTo("jev-1.13.0");
                    assertThat(properties.getTimeout()).isEqualTo(Duration.ofSeconds(30));
                    assertThat(properties.getRetry().getMaxRetries()).isZero();
                    assertThat(properties.toRetryPolicy().maxRetries()).isZero();
                    assertThat(properties.getBaseUrl()).isEqualTo("https://api.typesafe.ai");
                    assertThat(StringUtils.hasText(properties.getApiKey())).isFalse();
                    assertThat(ctx.getBean(TypeSafeClient.class).defaultModel()).isEqualTo("jev-1.13.0");
                });
    }

    @Test
    void testYamlMirrorsMainTypeSafePinsWithoutKey() throws IOException {
        PropertySource<?> mainYaml = loadYaml("src/main/resources/application.yaml");
        PropertySource<?> testYaml = loadYaml("src/test/resources/application.yaml");

        for (String key : List.of("spring.ai.typesafe.model", "spring.ai.typesafe.timeout",
                "spring.ai.typesafe.retry.max-retries")) {
            assertThat(mainYaml.getProperty(key)).as(key).isNotNull();
            assertThat(String.valueOf(testYaml.getProperty(key))).as(key)
                    .isEqualTo(String.valueOf(mainYaml.getProperty(key)));
        }
        assertThat(testYaml.containsProperty("spring.ai.typesafe.api-key")).isFalse();
        assertThat(String.valueOf(testYaml.getProperty("spring.ai.typesafe.base-url"))).startsWith("http://127.0.0.1");
        for (String name : ((EnumerablePropertySource<?>) testYaml).getPropertyNames()) {
            assertThat(String.valueOf(testYaml.getProperty(name))).as(name).doesNotContain("MYFEEDER_TYPESAFE_API_KEY");
        }
    }

    @Test
    void retryIntervalHonorsRetryAfterUpToCap() {
        // D-07: the server hint wins, clamped to [0, MAX_RETRY_AFTER_MS]
        assertThat(interval(rateLimited(700L), 1)).isEqualTo(700L);
        assertThat(interval(rateLimited(9_999L), 1)).isEqualTo(9_999L);
        assertThat(interval(rateLimited(10_000L), 1)).isEqualTo(10_000L);
        assertThat(interval(rateLimited(10_001L), 1)).isEqualTo(10_000L);
        assertThat(interval(rateLimited(60_000L), 1)).isEqualTo(10_000L);
        assertThat(interval(rateLimited(0L), 1)).isEqualTo(0L);
        assertThat(interval(rateLimited(-5L), 1)).isEqualTo(0L);
    }

    @Test
    void retryIntervalFallsBackToExponentialBackoff() {
        // No usable hint: base (wait-duration) * 2^(attempt - 1)
        assertThat(interval(rateLimited(null), 1)).isEqualTo(1_000L);
        assertThat(interval(rateLimited(null), 2)).isEqualTo(2_000L);
        TypeSafeInternalServerException serverError = new TypeSafeInternalServerException(
                "server error", 500, "", new HttpHeaders(), "POST /v1/systemone");
        assertThat(interval(serverError, 1)).isEqualTo(1_000L);
        assertThat(interval(serverError, 2)).isEqualTo(2_000L);
        TypeSafeApiConnectionException connectionError =
                new TypeSafeApiConnectionException("connection failed", new ConnectException("refused"));
        assertThat(interval(connectionError, 1)).isEqualTo(1_000L);
    }

    private static TypeSafeRateLimitException rateLimited(Long retryAfterMs) {
        return new TypeSafeRateLimitException("rate limited", 429, "", new HttpHeaders(), "POST /v1/systemone",
                retryAfterMs);
    }

    private static long interval(Throwable failure, int attempt) {
        RetryConfigCustomizer customizer = new TypeSafeConfig().jevRetryInterval(
                new MockEnvironment().withProperty("resilience4j.retry.instances.jev.wait-duration", "1s"));
        RetryConfig.Builder<Object> builder = RetryConfig.custom();
        customizer.customize(builder);
        return builder.build().getIntervalBiFunction().apply(attempt, Either.left(failure));
    }

    private static PropertySource<?> loadYaml(String path) throws IOException {
        return new YamlPropertySourceLoader().load(path, new FileSystemResource(path)).getFirst();
    }

    private static int occurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }
}
