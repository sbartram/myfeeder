package org.bartram.myfeeder.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ReactorClientHttpRequestFactory;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the auto-configured outbound HTTP client used by every RestClient (feed fetches,
 * Raindrop, reader-view extraction). Runs without Docker: only Boot's HTTP-client
 * auto-configuration is loaded. MockRestServiceServer-based tests cannot catch a transport
 * change because they replace the request factory.
 */
class HttpClientConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    HttpClientAutoConfiguration.class, ImperativeHttpClientAutoConfiguration.class));

    @Test
    void outboundTransportIsReactorNetty() {
        runner.run(ctx -> assertThat(ctx.getBean(ClientHttpRequestFactory.class))
                .isInstanceOf(ReactorClientHttpRequestFactory.class));
    }

    @Test
    void outboundTimeoutsFromMainApplicationYamlBind() throws Exception {
        // Loaded from disk: src/test/resources/application.yaml shadows the main one on the classpath.
        PropertySource<?> mainYaml = new YamlPropertySourceLoader()
                .load("main-application-yaml", new FileSystemResource("src/main/resources/application.yaml"))
                .getFirst();

        runner.withInitializer(ctx -> ctx.getEnvironment().getPropertySources().addFirst(mainYaml))
                .run(ctx -> {
                    HttpClientSettings settings = ctx.getBean(HttpClientSettings.class);
                    assertThat(settings.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
                    assertThat(settings.readTimeout()).isEqualTo(Duration.ofSeconds(30));
                });
    }
}
