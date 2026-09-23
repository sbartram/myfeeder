package org.bartram.myfeeder.integration;

import org.bartram.myfeeder.integration.JevJudgment.JevScore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springaicommunity.typesafe.RetryPolicy;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.autoconfigure.TypeSafeProperties;
import org.springaicommunity.typesafe.exception.TypeSafeAnswerTypeException;
import org.springaicommunity.typesafe.exception.TypeSafeApiException;
import org.springaicommunity.typesafe.exception.TypeSafeAuthenticationException;
import org.springaicommunity.typesafe.exception.TypeSafeBadRequestException;
import org.springaicommunity.typesafe.exception.TypeSafeInternalServerException;
import org.springaicommunity.typesafe.exception.TypeSafeMissingAnswerException;
import org.springaicommunity.typesafe.exception.TypeSafeOverloadedException;
import org.springaicommunity.typesafe.exception.TypeSafePermissionDeniedException;
import org.springaicommunity.typesafe.exception.TypeSafeRateLimitException;
import org.springaicommunity.typesafe.exception.TypeSafeUnprocessableEntityException;
import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.question.Question;
import org.springaicommunity.typesafe.question.Score;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.function.Consumer;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(OutputCaptureExtension.class)
class JevApiClientImplTest {

    private static final String URL = "http://jev.test/v1/systemone";

    private static final String CANARY_BODY = """
            {
              "model": "jev-1.13.0-canary",
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

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private JevApiClientImpl client(String apiKey) {
        TypeSafeClient sdk = TypeSafeClient.builder()
                .apiKey(() -> apiKey)
                .baseUrl("http://jev.test")
                .defaultModel("jev-1.13.0")
                .retryPolicy(RetryPolicy.noRetry())
                .restClientBuilder(builder)
                .build();
        TypeSafeProperties properties = new TypeSafeProperties();
        properties.setApiKey(apiKey);
        return new JevApiClientImpl(sdk, properties);
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

    /** Raw request body matcher: JSON-equality matchers ignore key order, so they cannot prove D-04 ordering. */
    private static RequestMatcher rawBody(Consumer<String> assertion) {
        return request -> assertion.accept(((MockClientHttpRequest) request).getBodyAsString());
    }

    @Test
    void judgeReturnsResponseModelNotConfiguredDefault() {
        JevApiClientImpl client = client("test-key");
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("jev-1.13.0"))
                .andRespond(withSuccess(CANARY_BODY, MediaType.APPLICATION_JSON)
                        .header("x-typesafe-request-id", "r-1"));

        JevJudgment judgment = client.judge(state(), questions());

        assertThat(judgment.model()).isEqualTo("jev-1.13.0-canary");
        assertThat(judgment.requestId()).isEqualTo("r-1");
        assertThat(judgment.scores().get("profile")).isEqualTo(new JevScore(2.6, 4, 0.7));
        assertThat(judgment.nouls().get("t1")).isEqualTo(0.91);
        assertThat(judgment.inputTokens()).isEqualTo(123);
        assertThat(judgment.outputTokens()).isEqualTo(4);
        server.verify();
    }

    @Test
    void judgeThrowsNotConfiguredWithoutHttpCall() {
        JevApiClientImpl client = client("");

        assertThatThrownBy(() -> client.judge(state(), questions()))
                .isInstanceOf(JevNotConfiguredException.class);
        server.verify();
    }

    @Test
    void judgeSendsOrderedObjectStateWithoutNulls() {
        JevApiClientImpl client = client("test-key");
        server.expect(requestTo(URL))
                .andExpect(rawBody(body -> assertThat(body).contains("\"state\":{\"feed\":\"F\",\"title\":\"T\"}")))
                .andRespond(withSuccess(CANARY_BODY, MediaType.APPLICATION_JSON));
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("feed", "F");
        state.put("summary", null);
        state.put("title", "T");

        client.judge(state, questions());

        server.verify();
    }

    @Test
    void judgeDoesNotMutateCallerState() {
        JevApiClientImpl client = client("test-key");
        server.expect(requestTo(URL)).andRespond(withSuccess(CANARY_BODY, MediaType.APPLICATION_JSON));
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("feed", "F");
        state.put("summary", null);
        state.put("title", "T");

        client.judge(state, questions());

        assertThat(state).hasSize(3).containsEntry("summary", null);
        server.verify();
    }

    @Test
    void judgeSendsEmptyStateAsObject() {
        JevApiClientImpl client = client("test-key");
        server.expect(requestTo(URL))
                .andExpect(rawBody(body -> assertThat(body).contains("\"state\":{}")))
                .andRespond(withSuccess(CANARY_BODY, MediaType.APPLICATION_JSON));

        client.judge(new LinkedHashMap<String, Object>(), questions());

        server.verify();
    }

    @Test
    void judgeRejectsNullStateAndEmptyQuestionsBeforeHttp() {
        JevApiClientImpl client = client("test-key");

        assertThatThrownBy(() -> client.judge(null, questions()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.judge(state(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    @Test
    void judgeKeepsAnswerOrderAndExactValues() {
        JevApiClientImpl client = client("test-key");
        server.expect(requestTo(URL)).andRespond(withSuccess("""
                {
                  "model": "jev-1.13.0",
                  "answers": {
                    "t2": {"type": "noul", "noul": 0.123456789},
                    "profile": {
                      "type": "score",
                      "score": 3.141592653589793,
                      "legend": {"0": "a", "1": "b", "2": "c", "3": "d", "4": "e"},
                      "probabilities": {"0": 0.0, "1": 0.0, "2": 0.1, "3": 0.6, "4": 0.3},
                      "confidence": 0.8
                    },
                    "t1": {"type": "noul", "noul": 0.5}
                  },
                  "usage": {"input_tokens": 10, "output_tokens": 3}
                }
                """, MediaType.APPLICATION_JSON));
        Map<String, Question> questions = new LinkedHashMap<>();
        questions.put("t2", Noul.builder().instructions("Is it about Go?").whenTrue("yes").whenFalse("no").build());
        questions.putAll(questions());

        JevJudgment judgment = client.judge(state(), questions);

        assertThat(judgment.nouls().keySet()).containsExactly("t2", "t1");
        assertThat(judgment.nouls().get("t2")).isEqualTo(0.123456789);
        assertThat(judgment.scores().get("profile").value()).isEqualTo(3.141592653589793);
        assertThatThrownBy(() -> judgment.nouls().put("x", 1.0))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> judgment.scores().put("x", new JevScore(1, 4, 1)))
                .isInstanceOf(UnsupportedOperationException.class);
        server.verify();
    }

    @Test
    void judgeHandlesMissingUsageRequestIdAndLegend() {
        JevApiClientImpl client = client("test-key");
        server.expect(requestTo(URL)).andRespond(withSuccess("""
                {
                  "model": "jev-1.13.0",
                  "answers": {
                    "profile": {"type": "score", "score": 1.5, "confidence": 0.4},
                    "t1": {"type": "noul", "noul": 0.2}
                  }
                }
                """, MediaType.APPLICATION_JSON));

        JevJudgment judgment = client.judge(state(), questions());

        assertThat(judgment.inputTokens()).isNull();
        assertThat(judgment.outputTokens()).isNull();
        assertThat(judgment.requestId()).isNull();
        assertThat(judgment.scores().get("profile").maxLevel()).isEqualTo(-1);
        server.verify();
    }

    @Test
    void judgeThrowsMissingAnswerWhenQuestionUnanswered() {
        JevApiClientImpl client = client("test-key");
        server.expect(requestTo(URL)).andRespond(withSuccess("""
                {
                  "model": "jev-1.13.0",
                  "answers": {
                    "profile": {"type": "score", "score": 2.0, "legend": {"0": "a", "1": "b", "2": "c", "3": "d", "4": "e"},
                                "probabilities": {"0": 0.2, "1": 0.2, "2": 0.2, "3": 0.2, "4": 0.2}, "confidence": 0.5}
                  }
                }
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.judge(state(), questions()))
                .isInstanceOf(TypeSafeMissingAnswerException.class);
        server.verify();
    }

    @Test
    void judgeThrowsAnswerTypeWhenKindMismatches() {
        JevApiClientImpl client = client("test-key");
        server.expect(requestTo(URL)).andRespond(withSuccess("""
                {
                  "model": "jev-1.13.0",
                  "answers": {
                    "profile": {"type": "noul", "noul": 0.9},
                    "t1": {"type": "noul", "noul": 0.1}
                  }
                }
                """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.judge(state(), questions()))
                .isInstanceOf(TypeSafeAnswerTypeException.class);
        server.verify();
    }

    static Stream<Arguments> httpErrors() {
        return Stream.of(
                Arguments.of(400, TypeSafeBadRequestException.class),
                Arguments.of(401, TypeSafeAuthenticationException.class),
                Arguments.of(403, TypeSafePermissionDeniedException.class),
                Arguments.of(422, TypeSafeUnprocessableEntityException.class),
                Arguments.of(429, TypeSafeRateLimitException.class),
                Arguments.of(500, TypeSafeInternalServerException.class),
                Arguments.of(529, TypeSafeOverloadedException.class));
    }

    @ParameterizedTest
    @MethodSource("httpErrors")
    void httpErrorsSurfaceAsTypedExceptions(int status, Class<? extends TypeSafeApiException> expected) {
        // JUnit creates a fresh test instance per invocation, so each case has its own builder and server
        JevApiClientImpl client = client("test-key");
        var response = withStatus(HttpStatusCode.valueOf(status))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"type\":\"error\",\"message\":\"status " + status + "\"}}");
        if (status == 429) {
            response = response.header("retry-after-ms", "700");
        }
        server.expect(requestTo(URL)).andRespond(response);

        assertThatThrownBy(() -> client.judge(state(), questions()))
                .isExactlyInstanceOf(expected)
                .satisfies(e -> assertThat(((TypeSafeApiException) e).status()).isEqualTo(status))
                .satisfies(e -> {
                    if (e instanceof TypeSafeRateLimitException rateLimit) {
                        assertThat(rateLimit.retryAfterMs()).isEqualTo(700L);
                    }
                });
        server.verify();
    }

    @Test
    void rejectedKeyLogsWarnWithRequestIdOnly(CapturedOutput output) {
        JevApiClientImpl client = client("sk-test-LEAKCHECK");
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatusCode.valueOf(401))
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-typesafe-request-id", "req-401")
                .body("{\"error\":{\"type\":\"authentication_error\",\"message\":\"Invalid API key sk-test-LEAKCHECK\"}}"));

        assertThatThrownBy(() -> client.judge(state(), questions()))
                .isInstanceOf(TypeSafeAuthenticationException.class);

        assertThat(output.getAll()).contains("req-401").contains("401").doesNotContain("LEAKCHECK");
        server.verify();
    }
}
