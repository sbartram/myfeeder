package org.bartram.myfeeder.integration;

import org.bartram.myfeeder.integration.JevJudgment.JevScore;
import org.junit.jupiter.api.Test;
import org.springaicommunity.typesafe.RetryPolicy;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.autoconfigure.TypeSafeProperties;
import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.question.Question;
import org.springaicommunity.typesafe.question.Score;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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
}
