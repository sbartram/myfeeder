package org.bartram.myfeeder.service;

import org.bartram.myfeeder.integration.JevApiClientImpl;
import org.bartram.myfeeder.integration.JevJudgment;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.model.InterestTopic;
import org.junit.jupiter.api.Test;
import org.springaicommunity.typesafe.RetryPolicy;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.autoconfigure.TypeSafeProperties;
import org.springaicommunity.typesafe.question.Question;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Proves the builders' output goes through judge() onto the wire as the request Phase 4 will send. */
class InterestJudgeRequestTest {

    private static final String URL = "http://jev.test/v1/systemone";

    private static final String SUCCESS_BODY = """
            {
              "model": "jev-1.13.0",
              "answers": {
                "profile": {
                  "type": "score",
                  "score": 3.0,
                  "legend": {"0": "a", "1": "b", "2": "c", "3": "d", "4": "e"},
                  "probabilities": {"0": 0.0, "1": 0.05, "2": 0.15, "3": 0.5, "4": 0.3},
                  "confidence": 0.8
                },
                "topic_7": {"type": "noul", "noul": 0.82}
              },
              "usage": {"input_tokens": 200, "output_tokens": 5}
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

    @Test
    void builtRequestReachesTheWireInScorerOrder() {
        JevApiClientImpl client = client("sk-test");

        Article article = new Article();
        article.setTitle("Rust &amp; Postgres");
        article.setSummary("<p>Rust 1.90 is out</p>");
        article.setContent(null);

        InterestTopic topic = new InterestTopic();
        topic.setId(7L);
        topic.setName("Rust");
        topic.setDescription("The Rust programming language");

        Map<String, Object> state = ArticleStateBuilder.build("Hacker News", article);
        Map<String, Question> questions =
                InterestQuestions.forRubric("I read about Rust and databases", List.of(topic));

        server.expect(requestTo(URL))
                .andExpect(request -> {
                    String body = ((MockClientHttpRequest) request).getBodyAsString();
                    assertThat(body).contains(
                            "\"state\":{\"feed\":\"Hacker News\",\"title\":\"Rust & Postgres\",\"summary\":\"Rust 1.90 is out\"}");
                    assertThat(body.indexOf("\"profile\"")).isNotNegative()
                            .isLessThan(body.indexOf("\"topic_7\""));
                    assertThat(body).contains("The Rust programming language");
                })
                .andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

        JevJudgment judgment = client.judge(state, questions);

        assertThat(judgment.nouls().get("topic_7")).isEqualTo(0.82);
        assertThat(judgment.scores().get("profile").maxLevel()).isEqualTo(4);
        server.verify();
    }

    @Test
    void isConfiguredReflectsKey() {
        assertThat(client("").isConfigured()).isFalse();
        assertThat(client("   ").isConfigured()).isFalse();
        assertThat(client("sk-test").isConfigured()).isTrue();
        server.verify();
    }
}
