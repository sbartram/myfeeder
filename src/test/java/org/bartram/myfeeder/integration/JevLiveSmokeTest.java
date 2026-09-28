package org.bartram.myfeeder.integration;

import org.bartram.myfeeder.config.TypeSafeConfig;
import org.bartram.myfeeder.integration.JevJudgment.JevScore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springaicommunity.typesafe.autoconfigure.TypeSafeAutoConfiguration;
import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.question.Question;
import org.springaicommunity.typesafe.question.Score;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in live smoke test (SC2): one real Jev call through the production wiring
 * (TypeSafeConfig + JevApiClientImpl + the main application.yaml) returns the pinned model.
 *
 * <p>Runs only with {@code JEV_LIVE_SMOKE=true}, never on the key alone, so a developer who has
 * {@code MYFEEDER_TYPESAFE_API_KEY} exported still gets an offline default run. The call is billed
 * to the key's account. Run it with {@code cleanTest}, because Gradle does not treat environment
 * variables as test inputs:
 * <pre>
 * JEV_LIVE_SMOKE=true MYFEEDER_TYPESAFE_API_KEY=... ./gradlew cleanTest test -x npmBuild -x npmInstall \
 *     --tests 'org.bartram.myfeeder.integration.JevLiveSmokeTest' --info
 * </pre>
 */
class JevLiveSmokeTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "JEV_LIVE_SMOKE", matches = "true")
    void liveJudgeReturnsPinnedModel() throws IOException {
        assertThat(StringUtils.hasText(System.getenv("MYFEEDER_TYPESAFE_API_KEY")))
                .as("JEV_LIVE_SMOKE=true needs MYFEEDER_TYPESAFE_API_KEY exported")
                .isTrue();

        // Loaded from disk: src/test/resources/application.yaml shadows the main one on the classpath.
        // addLast so ${MYFEEDER_TYPESAFE_API_KEY:} resolves from the real environment.
        String path = "src/main/resources/application.yaml";
        PropertySource<?> mainYaml = new YamlPropertySourceLoader().load(path, new FileSystemResource(path)).getFirst();

        new ApplicationContextRunner()
                .withInitializer(ctx -> ctx.getEnvironment().getPropertySources().addLast(mainYaml))
                .withConfiguration(AutoConfigurations.of(TypeSafeAutoConfiguration.class,
                        RestClientAutoConfiguration.class, HttpClientAutoConfiguration.class,
                        ImperativeHttpClientAutoConfiguration.class))
                .withConfiguration(UserConfigurations.of(TypeSafeConfig.class, JevApiClientImpl.class))
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();

                    Map<String, Object> state = new LinkedHashMap<>();
                    state.put("feed", "Hacker News");
                    state.put("title", "Rust 1.90 released with faster compile times");
                    state.put("summary", "The Rust team shipped version 1.90 of the language, "
                            + "cutting compile times for large workspaces.");

                    Map<String, Question> questions = new LinkedHashMap<>();
                    questions.put("profile", Score.builder()
                            .instructions("How interested is a reader who follows systems programming "
                                    + "and programming languages in this article?")
                            .level("not interested")
                            .level("slightly interested")
                            .level("moderately interested")
                            .level("very interested")
                            .level("extremely interested")
                            .build());
                    questions.put("t1", Noul.builder()
                            .instructions("Is this article about the Rust programming language?")
                            .whenTrue("The article is about the Rust programming language")
                            .whenFalse("The article is not about the Rust programming language")
                            .build());

                    JevJudgment judgment = ctx.getBean(JevApiClient.class).judge(state, questions);

                    System.out.printf("Jev live smoke: model=%s requestId=%s inputTokens=%s outputTokens=%s%n",
                            judgment.model(), judgment.requestId(), judgment.inputTokens(), judgment.outputTokens());

                    assertThat(judgment.model()).isEqualTo("jev-1.13.0");
                    assertThat(StringUtils.hasText(judgment.requestId())).isTrue();
                    assertThat(judgment.scores()).containsKey("profile");
                    JevScore profile = judgment.scores().get("profile");
                    assertThat(profile.maxLevel()).isEqualTo(4);
                    assertThat(profile.value()).isBetween(0.0, 4.0);
                    assertThat(judgment.nouls()).containsKey("t1");
                    assertThat(judgment.nouls().get("t1")).isBetween(0.0, 1.0);
                });
    }
}
