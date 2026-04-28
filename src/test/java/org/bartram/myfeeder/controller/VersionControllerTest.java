package org.bartram.myfeeder.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Properties;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VersionController.class)
@Import(VersionControllerTest.TestConfig.class)
class VersionControllerTest {
    @Autowired private MockMvc mockMvc;

    static class TestConfig {
        @Bean
        BuildProperties buildProperties() {
            Properties props = new Properties();
            props.setProperty("version", "1.2.3");
            props.setProperty("time", Instant.parse("2026-04-27T19:00:00Z").toString());
            return new BuildProperties(props);
        }
    }

    @Test
    void shouldReturnVersionFromBuildProperties() throws Exception {
        mockMvc.perform(get("/api/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("1.2.3"))
                .andExpect(jsonPath("$.buildTime").value("2026-04-27T19:00:00Z"));
    }
}
