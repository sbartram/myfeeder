package org.bartram.myfeeder.integration;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.spring6.circuitbreaker.configure.CircuitBreakerAspect;
import io.github.resilience4j.spring6.retry.configure.RetryAspect;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import io.netty.handler.timeout.ReadTimeoutException;
import org.bartram.myfeeder.config.JevEventLogging;
import org.bartram.myfeeder.config.RestClientConfig;
import org.bartram.myfeeder.config.TypeSafeConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springaicommunity.typesafe.autoconfigure.TypeSafeAutoConfiguration;
import org.springaicommunity.typesafe.exception.TypeSafeAnswerTypeException;
import org.springaicommunity.typesafe.exception.TypeSafeApiConnectionException;
import org.springaicommunity.typesafe.exception.TypeSafeApiTimeoutException;
import org.springaicommunity.typesafe.exception.TypeSafeAuthenticationException;
import org.springaicommunity.typesafe.exception.TypeSafeBadRequestException;
import org.springaicommunity.typesafe.exception.TypeSafeInternalServerException;
import org.springaicommunity.typesafe.exception.TypeSafeMissingAnswerException;
import org.springaicommunity.typesafe.exception.TypeSafeOverloadedException;
import org.springaicommunity.typesafe.exception.TypeSafePermissionDeniedException;
import org.springaicommunity.typesafe.exception.TypeSafeRateLimitException;
import org.springaicommunity.typesafe.exception.TypeSafeUnprocessableEntityException;
import org.springaicommunity.typesafe.question.Choice;
import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.question.Question;
import org.springaicommunity.typesafe.question.Score;
import org.springaicommunity.typesafe.response.AnswerType;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Proves the Jev resilience wiring through the real AOP proxy and a real socket: the production
 * {@code jev} circuit-breaker and retry instances (main application.yaml, loaded from disk) drive
 * {@link JevApiClientImpl#judge} against a JDK {@link HttpServer} stub. MockRestServiceServer cannot
 * be used here because {@link TypeSafeConfig} replaces the request factory with a Reactor Netty one.
 * Runs without Docker.
 */
@ExtendWith(OutputCaptureExtension.class)
class JevResilienceTest {

    private static final String FAKE_KEY = "sk-test-LEAKCHECK";
    private static final String ENDPOINT = "POST /v1/systemone";

    private static final String OK_BODY = """
            {
              "model": "jev-1.13.0",
              "answers": {
                "profile": {
                  "type": "score",
                  "score": 2.6,
                  "legend": {"0": "a", "1": "b", "2": "c", "3": "d", "4": "e"},
                  "probabilities": {"0": 0.05, "1": 0.1, "2": 0.2, "3": 0.4, "4": 0.25},
                  "confidence": 0.7
                },
                "t1": {"type": "noul", "noul": 0.91}
              },
              "usage": {"input_tokens": 123, "output_tokens": 4}
            }
            """;

    private static final String ERROR_BODY = "{\"error\":{\"type\":\"error\",\"message\":\"stubbed failure\"}}";

    private StubServer stub;
    private ApplicationContextRunner runner;

    @BeforeEach
    void setUp() throws IOException {
        stub = new StubServer();
        PropertySource<?> mainYaml = loadYaml("src/main/resources/application.yaml");
        runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class,
                        CircuitBreakerAutoConfiguration.class, RetryAutoConfiguration.class,
                        TypeSafeAutoConfiguration.class, RestClientAutoConfiguration.class,
                        HttpClientAutoConfiguration.class, ImperativeHttpClientAutoConfiguration.class))
                .withConfiguration(UserConfigurations.of(TypeSafeConfig.class, JevApiClientImpl.class, JevEventLogging.class))
                // addLast: the test properties below must beat the YAML's blank ${MYFEEDER_TYPESAFE_API_KEY:}
                .withInitializer(ctx -> ctx.getEnvironment().getPropertySources().addLast(mainYaml))
                .withPropertyValues(
                        "spring.ai.typesafe.api-key=" + FAKE_KEY,
                        "spring.ai.typesafe.base-url=" + stub.url(),
                        "resilience4j.retry.instances.jev.wait-duration=10ms");
    }

    @AfterEach
    void tearDown() {
        stub.stop();
    }

    @Test
    void jevClientIsAnAopProxy() {
        // Without AspectJ on the classpath the resilience4j annotations would silently do nothing.
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(AopUtils.isAopProxy(ctx.getBean(JevApiClient.class))).isTrue();
        });
    }

    @Test
    void serverErrorIsAttemptedThreeTimesThenPropagatesTyped() {
        stub.enqueue(500);
        stub.enqueue(500);
        stub.enqueue(500);
        runner.run(ctx -> {
            JevApiClient client = ctx.getBean(JevApiClient.class);
            assertThatThrownBy(() -> client.judge(state(), questions()))
                    .isExactlyInstanceOf(TypeSafeInternalServerException.class);
            assertThat(stub.hits()).isEqualTo(3);
        });
    }

    @Test
    void rateLimitWaitsForRetryAfterThenSucceeds() {
        // The test's base wait is 10ms, so only the retry-after-ms hint can explain a ~700ms wait (D-07).
        stub.enqueue(429, Map.of("retry-after-ms", "700"));
        stub.enqueue(200);
        runner.run(ctx -> {
            JevApiClient client = ctx.getBean(JevApiClient.class);
            long start = System.nanoTime();
            JevJudgment judgment = client.judge(state(), questions());
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            assertThat(judgment.model()).isEqualTo("jev-1.13.0");
            assertThat(stub.hits()).isEqualTo(2);
            assertThat(elapsedMs).isGreaterThanOrEqualTo(650L).isLessThan(5_000L);
        });
    }

    @Test
    void rateLimitRetryIsLoggedWithoutLeaking(CapturedOutput output) {
        // D-15: a 429 the retry absorbs is visible in the log as fixed text (class name and numbers only).
        stub.enqueue(429, Map.of("retry-after-ms", "700"));
        stub.enqueue(200);
        runner.run(ctx -> {
            JevJudgment judgment = ctx.getBean(JevApiClient.class).judge(state(), questions());
            assertThat(judgment.model()).isEqualTo("jev-1.13.0");
            assertThat(stub.hits()).isEqualTo(2);
        });
        assertThat(output.toString())
                .contains("Jev retry attempt 1 after TypeSafeRateLimitException (waiting 700 ms)")
                .doesNotContain("LEAKCHECK")
                .doesNotContain("stubbed failure")
                .doesNotContain("Jev retries exhausted");
    }

    @Test
    void exhaustedRetriesAreLogged(CapturedOutput output) {
        // D-15: a call the retry could not absorb leaves a WARN line, so it is visible in the prod logs.
        stub.enqueue(500);
        stub.enqueue(500);
        stub.enqueue(500);
        runner.run(ctx -> {
            assertThatThrownBy(() -> ctx.getBean(JevApiClient.class).judge(state(), questions()))
                    .isExactlyInstanceOf(TypeSafeInternalServerException.class);
            assertThat(stub.hits()).isEqualTo(3);
        });
        assertThat(output.toString())
                .contains("Jev retry attempt 1 after TypeSafeInternalServerException")
                .contains("Jev retry attempt 2 after TypeSafeInternalServerException")
                .contains("Jev retries exhausted after 3 attempts: TypeSafeInternalServerException")
                .doesNotContain("LEAKCHECK")
                .doesNotContain("stubbed failure");
    }

    @Test
    void breakerTransitionsAreLogged(CapturedOutput output) {
        // The enum name, not StateTransition.toString(), which is prose.
        runner.run(ctx -> {
            CircuitBreaker b = jevBreaker(ctx);
            b.transitionToOpenState();
            b.transitionToHalfOpenState();
            b.transitionToClosedState();
        });
        assertThat(output.toString())
                .contains("Jev circuit breaker CLOSED_TO_OPEN")
                .contains("Jev circuit breaker OPEN_TO_HALF_OPEN")
                .contains("Jev circuit breaker HALF_OPEN_TO_CLOSED")
                .doesNotContain("State transition from");
    }

    @Test
    void perArticleErrorsLogNoRetryLine(CapturedOutput output) {
        // A non-retryable error publishes RetryOnIgnoredErrorEvent, which is deliberately not logged.
        stub.enqueue(400);
        runner.run(ctx -> {
            assertThatThrownBy(() -> ctx.getBean(JevApiClient.class).judge(state(), questions()))
                    .isExactlyInstanceOf(TypeSafeBadRequestException.class);
            assertThat(stub.hits()).isEqualTo(1);
        });
        assertThat(output.toString())
                .doesNotContain("Jev retry attempt")
                .doesNotContain("Jev retries exhausted");
    }

    @Test
    void badRequestAndUnprocessableAreNotRetriedOrRecorded() {
        // Per-article errors: attempted once and ignored by the breaker, so they can never open it.
        stub.enqueue(400);
        runner.run(ctx -> {
            assertThatThrownBy(() -> ctx.getBean(JevApiClient.class).judge(state(), questions()))
                    .isExactlyInstanceOf(TypeSafeBadRequestException.class);
            assertThat(stub.hits()).isEqualTo(1);
            assertNotRecorded(ctx);
        });

        stub.enqueue(422);
        runner.run(ctx -> {
            assertThatThrownBy(() -> ctx.getBean(JevApiClient.class).judge(state(), questions()))
                    .isExactlyInstanceOf(TypeSafeUnprocessableEntityException.class);
            assertThat(stub.hits()).isEqualTo(2); // one more hit, in a fresh context
            assertNotRecorded(ctx);
        });
    }

    @Test
    void rejectedKeyIsNotRetriedButRecorded(CapturedOutput output) {
        // D-06: a bad key is not retried, but it is a breaker failure, so on its own it opens the breaker.
        stub.enqueue(401);
        runner.run(ctx -> {
            assertThatThrownBy(() -> ctx.getBean(JevApiClient.class).judge(state(), questions()))
                    .isExactlyInstanceOf(TypeSafeAuthenticationException.class);
            assertThat(stub.hits()).isEqualTo(1);
            assertThat(jevBreaker(ctx).getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
        });
        assertThat(output.toString()).contains("req-1").doesNotContain("LEAKCHECK");

        stub.enqueue(403);
        runner.run(ctx -> {
            assertThatThrownBy(() -> ctx.getBean(JevApiClient.class).judge(state(), questions()))
                    .isExactlyInstanceOf(TypeSafePermissionDeniedException.class);
            assertThat(stub.hits()).isEqualTo(2);
            assertThat(jevBreaker(ctx).getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
        });
        assertThat(output.toString()).contains("req-2").doesNotContain("LEAKCHECK");
    }

    @Test
    void notConfiguredIsNeitherRetriedNorRecorded() {
        runner.withPropertyValues("spring.ai.typesafe.api-key=")
                .run(ctx -> {
                    assertThatThrownBy(() -> ctx.getBean(JevApiClient.class).judge(state(), questions()))
                            .isExactlyInstanceOf(JevNotConfiguredException.class);
                    assertThat(stub.hits()).isZero();
                    assertThat(jevBreaker(ctx).getMetrics().getNumberOfFailedCalls()).isZero();
                });
    }

    @Test
    void callerInputErrorsAreNeitherSentNorRecorded() {
        // D-14/D-15: caller bugs never reach Jev (nothing billed) and never count against the breaker.
        Map<String, Question> choice = Map.of("c1", Choice.builder()
                .instructions("Which language is the article about?")
                .option("Rust")
                .option("Java")
                .build());
        runner.run(ctx -> {
            JevApiClient client = ctx.getBean(JevApiClient.class);
            assertThatThrownBy(() -> client.judge(null, questions()))
                    .isExactlyInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> client.judge(state(), Map.of()))
                    .isExactlyInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> client.judge(state(), choice))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported question type for 'c1'");
            assertThat(stub.hits()).isZero();
            assertNotRecorded(ctx);
        });
    }

    @Test
    void openBreakerMovesToHalfOpenWithoutACall() throws Exception {
        // D-17: a paused sweep never calls judge(), so the breaker must leave OPEN on its own.
        runner.withPropertyValues("resilience4j.circuitbreaker.instances.jev.wait-duration-in-open-state=200ms")
                .run(ctx -> {
                    CircuitBreaker breaker = jevBreaker(ctx);
                    breaker.transitionToOpenState();
                    assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
                    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
                    while (breaker.getState() == CircuitBreaker.State.OPEN && System.nanoTime() < deadline) {
                        Thread.sleep(50);
                    }
                    assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
                    assertThat(stub.hits()).isZero();
                });
    }

    @Test
    void breakerOpensAtMinimumCallsAndShortCircuits() {
        for (int i = 0; i < 30; i++) {
            stub.enqueue(500);
        }
        runner.run(ctx -> {
            JevApiClient client = ctx.getBean(JevApiClient.class);
            CircuitBreaker breaker = jevBreaker(ctx);

            // The breaker is the outer aspect, so it records one failure per logical call after the
            // retry has spent its 3 attempts: 9 calls x 3 attempts, 9 recorded failures.
            for (int call = 1; call <= 9; call++) {
                assertThatThrownBy(() -> client.judge(state(), questions()))
                        .isExactlyInstanceOf(TypeSafeInternalServerException.class);
            }
            assertThat(stub.hits()).isEqualTo(27);
            assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(9);
            assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

            // The 10th failed call (minimum-number-of-calls) opens it.
            assertThatThrownBy(() -> client.judge(state(), questions()))
                    .isExactlyInstanceOf(TypeSafeInternalServerException.class);
            assertThat(stub.hits()).isEqualTo(30);
            assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(10);
            assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            // Open: no HTTP request, and CallNotPermittedException is not retried.
            assertThatThrownBy(() -> client.judge(state(), questions()))
                    .isExactlyInstanceOf(CallNotPermittedException.class);
            assertThat(stub.hits()).isEqualTo(30);
        });
    }

    @Test
    void retriedCallThatSucceedsRecordsOneSuccess() {
        stub.enqueue(500);
        stub.enqueue(500);
        stub.enqueue(200);
        runner.run(ctx -> {
            JevJudgment judgment = ctx.getBean(JevApiClient.class).judge(state(), questions());
            assertThat(judgment.model()).isEqualTo("jev-1.13.0");
            assertThat(stub.hits()).isEqualTo(3);
            CircuitBreaker breaker = jevBreaker(ctx);
            assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();
            assertThat(breaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
        });
    }

    @Test
    void breakerAspectWrapsRetryAspect() {
        // The runner loads the main YAML from disk, so this proves the shipped aspect orders (D-07).
        runner.run(ctx -> {
            assertThat(ctx.getBean(CircuitBreakerAspect.class).getOrder()).isEqualTo(1);
            assertThat(ctx.getBean(RetryAspect.class).getOrder()).isEqualTo(2);
        });
    }

    @Test
    void readTimeoutSurfacesAsConnectionExceptionAndIsRetried() {
        stub.enqueueDelayed(200, 1_500);
        stub.enqueueDelayed(200, 1_500);
        stub.enqueueDelayed(200, 1_500);
        runner.withPropertyValues("spring.ai.typesafe.timeout=300ms")
                .run(ctx -> {
                    Throwable thrown = catchThrowable(() -> ctx.getBean(JevApiClient.class).judge(state(), questions()));
                    // D-05: Reactor Netty timeouts are the base connection type, never the timeout subtype.
                    assertThat(thrown).isExactlyInstanceOf(TypeSafeApiConnectionException.class)
                            .isNotInstanceOf(TypeSafeApiTimeoutException.class);
                    assertThat(causeChain(thrown)).anyMatch(ReadTimeoutException.class::isInstance);
                    assertThat(stub.hits()).isEqualTo(3);
                });
    }

    @Test
    void outboundRequestCarriesUserAgentAndBearer() {
        Properties props = new Properties();
        props.setProperty("version", "9.9.9-test");
        runner.withConfiguration(UserConfigurations.of(RestClientConfig.class))
                .withBean(BuildProperties.class, () -> new BuildProperties(props))
                .run(ctx -> {
                    ctx.getBean(JevApiClient.class).judge(state(), questions());
                    Headers headers = stub.headers().getFirst();
                    assertThat(headers.getFirst(HttpHeaders.USER_AGENT)).startsWith("myfeeder/9.9.9-test");
                    assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + FAKE_KEY);
                });
    }

    @Test
    void retriedAttemptsSendIdenticalBodies() {
        stub.enqueue(500);
        stub.enqueue(500);
        stub.enqueue(200);
        runner.run(ctx -> {
            JevJudgment judgment = ctx.getBean(JevApiClient.class).judge(state(), questions());
            assertThat(judgment.model()).isEqualTo("jev-1.13.0");
            assertThat(stub.bodies()).hasSize(3);
            assertThat(stub.bodies().get(0)).isNotBlank();
            assertThat(stub.bodies()).allMatch(body -> body.equals(stub.bodies().get(0)));
        });
    }

    @Test
    void mainYamlJevInstancesBindAsSpecified() {
        runner.run(ctx -> {
            CircuitBreaker breaker = jevBreaker(ctx);
            CircuitBreakerConfig cb = breaker.getCircuitBreakerConfig();
            assertThat(cb.getSlidingWindowType()).isEqualTo(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED);
            assertThat(cb.getSlidingWindowSize()).isEqualTo(20);
            assertThat(cb.getMinimumNumberOfCalls()).isEqualTo(10);
            assertThat(cb.getFailureRateThreshold()).isEqualTo(50f);
            assertThat(cb.getWaitIntervalFunctionInOpenState().apply(1)).isEqualTo(60_000L);
            assertThat(cb.getSlowCallDurationThreshold()).isEqualTo(Duration.ofSeconds(15));
            assertThat(cb.getSlowCallRateThreshold()).isEqualTo(50f);
            assertThat(cb.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(3);
            assertThat(cb.isAutomaticTransitionFromOpenToHalfOpenEnabled()).isTrue();

            Predicate<Throwable> ignored = cb.getIgnoreExceptionPredicate();
            assertThat(ignored.test(new JevNotConfiguredException())).isTrue();
            assertThat(ignored.test(badRequest())).isTrue();
            assertThat(ignored.test(unprocessable())).isTrue();
            assertThat(ignored.test(new TypeSafeMissingAnswerException("t1", List.of("profile")))).isTrue();
            assertThat(ignored.test(new TypeSafeAnswerTypeException("t1", AnswerType.NOUL, AnswerType.SCORE))).isTrue();
            assertThat(ignored.test(unauthorized())).isFalse();
            assertThat(ignored.test(forbidden())).isFalse();
            assertThat(ignored.test(serverError())).isFalse();
            assertThat(ignored.test(new IllegalArgumentException("x"))).isTrue();

            RetryConfig retry = ctx.getBean(RetryRegistry.class).retry("jev").getRetryConfig();
            assertThat(retry.getMaxAttempts()).isEqualTo(3);
            Predicate<Throwable> retried = retry.getExceptionPredicate();
            assertThat(retried.test(new TypeSafeRateLimitException(
                    "rate limited", 429, "", new HttpHeaders(), ENDPOINT, 700L))).isTrue();
            assertThat(retried.test(serverError())).isTrue();
            assertThat(retried.test(new TypeSafeOverloadedException(
                    "overloaded", 529, "", new HttpHeaders(), ENDPOINT))).isTrue();
            assertThat(retried.test(new TypeSafeApiConnectionException(
                    "connection failed", new ConnectException("refused")))).isTrue();
            assertThat(retried.test(unauthorized())).isFalse();
            assertThat(retried.test(forbidden())).isFalse();
            assertThat(retried.test(badRequest())).isFalse();
            assertThat(retried.test(unprocessable())).isFalse();
            assertThat(retried.test(new JevNotConfiguredException())).isFalse();
            assertThat(retried.test(new IllegalArgumentException("x"))).isFalse();
            assertThat(retried.test(CallNotPermittedException.createCallNotPermittedException(breaker))).isFalse();
        });
    }

    @Test
    void testYamlMirrorsMainJevInstances() throws IOException {
        // The test application.yaml shadows main on the classpath, so its jev blocks must be identical.
        Map<String, String> main = jevProperties(loadYaml("src/main/resources/application.yaml"));
        Map<String, String> test = jevProperties(loadYaml("src/test/resources/application.yaml"));
        assertThat(main).isNotEmpty();
        assertThat(test).isEqualTo(main);
    }

    private static Map<String, Question> questions() {
        Map<String, Question> questions = new LinkedHashMap<>();
        questions.put("profile", Score.builder()
                .instructions("How interested is the reader in this article?")
                .level("not interested")
                .level("slightly interested")
                .level("interested")
                .level("very interested")
                .level("extremely interested")
                .build());
        questions.put("t1", Noul.builder()
                .instructions("Is the article about the Rust programming language?")
                .whenTrue("It is about Rust")
                .whenFalse("It is not about Rust")
                .build());
        return questions;
    }

    private static Map<String, Object> state() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("feed", "F");
        state.put("title", "T");
        return state;
    }

    private static CircuitBreaker jevBreaker(ApplicationContext ctx) {
        return ctx.getBean(CircuitBreakerRegistry.class).circuitBreaker("jev");
    }

    private static void assertNotRecorded(ApplicationContext ctx) {
        CircuitBreaker breaker = jevBreaker(ctx);
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();
        assertThat(breaker.getMetrics().getNumberOfSuccessfulCalls()).isZero();
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    private static List<Throwable> causeChain(Throwable thrown) {
        List<Throwable> chain = new ArrayList<>();
        for (Throwable t = thrown; t != null && !chain.contains(t); t = t.getCause()) {
            chain.add(t);
        }
        return chain;
    }

    private static Map<String, String> jevProperties(PropertySource<?> yaml) {
        Map<String, String> jev = new TreeMap<>();
        for (String name : ((EnumerablePropertySource<?>) yaml).getPropertyNames()) {
            // The aspect orders sit outside instances.jev but decide how the jev aspects nest (D-07).
            if (name.startsWith("resilience4j.circuitbreaker.instances.jev.")
                    || name.startsWith("resilience4j.retry.instances.jev.")
                    || name.equals("resilience4j.circuitbreaker.circuit-breaker-aspect-order")
                    || name.equals("resilience4j.retry.retry-aspect-order")) {
                jev.put(name, String.valueOf(yaml.getProperty(name)));
            }
        }
        return jev;
    }

    private static TypeSafeBadRequestException badRequest() {
        return new TypeSafeBadRequestException("bad request", 400, "", new HttpHeaders(), ENDPOINT);
    }

    private static TypeSafeUnprocessableEntityException unprocessable() {
        return new TypeSafeUnprocessableEntityException("unprocessable", 422, "", new HttpHeaders(), ENDPOINT);
    }

    private static TypeSafeAuthenticationException unauthorized() {
        return new TypeSafeAuthenticationException("unauthorized", 401, "", new HttpHeaders(), ENDPOINT);
    }

    private static TypeSafePermissionDeniedException forbidden() {
        return new TypeSafePermissionDeniedException("forbidden", 403, "", new HttpHeaders(), ENDPOINT);
    }

    private static TypeSafeInternalServerException serverError() {
        return new TypeSafeInternalServerException("server error", 500, "", new HttpHeaders(), ENDPOINT);
    }

    private static PropertySource<?> loadYaml(String path) throws IOException {
        return new YamlPropertySourceLoader().load(path, new FileSystemResource(path)).getFirst();
    }

    /** A canned HTTP response; {@code delayMs} sleeps before answering. */
    private record Resp(int status, Map<String, String> headers, String body, long delayMs) {
    }

    /**
     * Minimal JDK HTTP stub on 127.0.0.1. Serves queued responses in order and answers 200 with
     * {@link #OK_BODY} when the queue is empty. Records the hit count, bodies and headers.
     */
    private static final class StubServer {

        private final HttpServer server;
        private final ExecutorService executor = Executors.newCachedThreadPool();
        private final ConcurrentLinkedQueue<Resp> responses = new ConcurrentLinkedQueue<>();
        private final AtomicInteger hits = new AtomicInteger();
        private final List<String> bodies = new CopyOnWriteArrayList<>();
        private final List<Headers> headers = new CopyOnWriteArrayList<>();

        StubServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/", exchange -> {
                int hit = hits.incrementAndGet();
                bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                headers.add(new Headers(exchange.getRequestHeaders()));
                Resp resp = responses.poll();
                if (resp == null) {
                    resp = new Resp(200, Map.of(), OK_BODY, 0);
                }
                try {
                    if (resp.delayMs() > 0) {
                        Thread.sleep(resp.delayMs());
                    }
                    byte[] body = resp.body().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.getResponseHeaders().add("x-typesafe-request-id", "req-" + hit);
                    resp.headers().forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
                    exchange.sendResponseHeaders(resp.status(), body.length);
                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(body);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (IOException e) {
                    // the client gave up (e.g. read timeout); nothing to answer
                } finally {
                    exchange.close();
                }
            });
            server.start();
        }

        void enqueue(int status) {
            enqueue(status, Map.of());
        }

        void enqueue(int status, Map<String, String> responseHeaders) {
            responses.add(new Resp(status, responseHeaders, status == 200 ? OK_BODY : ERROR_BODY, 0));
        }

        void enqueueDelayed(int status, long delayMs) {
            responses.add(new Resp(status, Map.of(), status == 200 ? OK_BODY : ERROR_BODY, delayMs));
        }

        int hits() {
            return hits.get();
        }

        List<String> bodies() {
            return bodies;
        }

        List<Headers> headers() {
            return headers;
        }

        String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        void stop() {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
