package org.bartram.myfeeder.integration;

import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration;
import org.bartram.myfeeder.config.TypeSafeConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springaicommunity.typesafe.autoconfigure.TypeSafeAutoConfiguration;
import org.springaicommunity.typesafe.exception.TypeSafeInternalServerException;
import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.question.Question;
import org.springaicommunity.typesafe.question.Score;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                .withConfiguration(UserConfigurations.of(TypeSafeConfig.class, JevApiClientImpl.class))
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
        private final List<Map<String, List<String>>> headers = new CopyOnWriteArrayList<>();

        StubServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(executor);
            server.createContext("/", exchange -> {
                int hit = hits.incrementAndGet();
                bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                headers.add(Map.copyOf(exchange.getRequestHeaders()));
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

        List<Map<String, List<String>>> headers() {
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
