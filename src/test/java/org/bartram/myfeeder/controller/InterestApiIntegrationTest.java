package org.bartram.myfeeder.controller;

import org.bartram.myfeeder.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end: MockMvc → InterestController → InterestService → Testcontainers Postgres. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class InterestApiIntegrationTest {

    @Autowired private WebApplicationContext wac;
    @Autowired private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        jdbcTemplate.update("DELETE FROM interest_topic");
        jdbcTemplate.update("UPDATE interest_profile SET profile_text = '', version = 1 WHERE id = 1");
    }

    @Test
    void profilePutThenGetPersists() throws Exception {
        mockMvc.perform(put("/api/interest/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileText\":\"Rust and Postgres\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileText").value("Rust and Postgres"))
                .andExpect(jsonPath("$.version").value(2));

        mockMvc.perform(get("/api/interest/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.profileText").value("Rust and Postgres"))
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void savingTheSameProfileTextKeepsTheVersion() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(put("/api/interest/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"profileText\":\"Rust and Postgres\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.version").value(2));
        }
    }
}
