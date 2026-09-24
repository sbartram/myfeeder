package org.bartram.myfeeder;

import org.junit.jupiter.api.Test;
import org.springaicommunity.typesafe.autoconfigure.TypeSafeProperties;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.StringUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves what {@code ./gradlew bootTestRun} sees. Runs Spring Boot's real ConfigData pipeline on
 * the test classpath, which is the same classpath order bootTestRun uses (the test
 * application.yaml shadows main's). With the {@code dev} profile that {@link TestMyfeederApplication}
 * activates, the overlay restores main's live settings; without it (what every
 * {@code @SpringBootTest} context sees) the suite config stays offline. Runs without Docker.
 */
class DevProfileConfigTest {

    private static final String PROBE_KEY = "probe-not-a-real-key";

    @Configuration(proxyBeanMethods = false)
    static class ProbeConfig {
    }

    @Test
    void devProfileRestoresLiveMainSettings() {
        ConfigurableEnvironment env = resolve(TestMyfeederApplication.DEV_PROFILE);

        assertThat(env.getActiveProfiles()).contains(TestMyfeederApplication.DEV_PROFILE);
        assertThat(env.getPropertySources().stream().map(PropertySource::getName))
                .anyMatch(name -> name.contains("application-dev.yaml"));
        // Boolean form: a failure never prints a resolved key.
        assertThat(PROBE_KEY.equals(env.getProperty("spring.ai.typesafe.api-key"))).isTrue();
        assertThat(env.getProperty("spring.ai.typesafe.base-url")).isEqualTo(new TypeSafeProperties().getBaseUrl());
        assertThat(env.getProperty("myfeeder.interest.sweep-initial-delay")).isEqualTo("PT1M");
        assertThat(env.getProperty("myfeeder.interest.sweep-delay")).isEqualTo("PT2M");
        assertThat(env.getProperty("spring.http.clients.connect-timeout")).isEqualTo("5s");
        assertThat(env.getProperty("spring.http.clients.read-timeout")).isEqualTo("30s");
        assertThat(env.getProperty("spring.application.name")).isEqualTo("myfeeder");
    }

    @Test
    void withoutTheProfileTheSuiteConfigStaysOffline() {
        ConfigurableEnvironment env = resolve();

        assertThat(env.getActiveProfiles()).isEmpty();
        assertThat(env.getPropertySources().stream().map(PropertySource::getName))
                .noneMatch(name -> name.contains("application-dev.yaml"));
        assertThat(StringUtils.hasText(env.getProperty("spring.ai.typesafe.api-key"))).isFalse();
        assertThat(env.getProperty("spring.ai.typesafe.base-url")).startsWith("http://127.0.0.1");
        assertThat(env.getProperty("myfeeder.interest.sweep-initial-delay")).isEqualTo("PT1H");
    }

    private static ConfigurableEnvironment resolve(String... profiles) {
        StandardEnvironment env = new StandardEnvironment();
        // Keep a developer's exported key, SPRING_AI_TYPESAFE_* and profile variables out of the probe.
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        env.getPropertySources().addFirst(new MapPropertySource("probe",
                Map.of("MYFEEDER_TYPESAFE_API_KEY", PROBE_KEY)));

        SpringApplication app = new SpringApplication(ProbeConfig.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        app.setRegisterShutdownHook(false);
        app.setEnvironment(env);
        app.setAdditionalProfiles(profiles);
        try (ConfigurableApplicationContext ctx = app.run()) {
            return ctx.getEnvironment();
        }
    }
}
