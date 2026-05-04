package org.bartram.myfeeder.config;

import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClientCustomizer userAgentCustomizer(BuildProperties buildProperties) {
        String version = buildProperties != null ? buildProperties.getVersion() : "dev";
        String userAgent = "myfeeder/" + version + " (+https://github.com/bartram/myfeeder)";
        return builder -> builder.defaultHeader(HttpHeaders.USER_AGENT, userAgent);
    }
}
