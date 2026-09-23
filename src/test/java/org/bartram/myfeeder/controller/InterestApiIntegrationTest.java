package org.bartram.myfeeder.controller;

import com.jayway.jsonpath.JsonPath;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

    @Test
    void topicCrudRoundTripsThroughTheDatabase() throws Exception {
        String created = mockMvc.perform(post("/api/interest/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Rust\",\"description\":\"The Rust programming language\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.weight").value(20))
                .andExpect(jsonPath("$.version").value(1))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(created, "$.id")).longValue();

        mockMvc.perform(get("/api/interest/topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].name").value("Rust"));

        mockMvc.perform(put("/api/interest/topics/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Rust\",\"description\":\"Rust systems programming\",\"weight\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.version").value(2));

        mockMvc.perform(put("/api/interest/topics/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Rust\",\"description\":\"Rust systems programming\",\"weight\":-15}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.weight").value(-15))
                .andExpect(jsonPath("$.version").value(2));

        mockMvc.perform(delete("/api/interest/topics/" + id))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/interest/topics/" + id))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/interest/topics"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void statusReportsKeylessClosedAndColdStart() throws Exception {
        mockMvc.perform(get("/api/interest/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.breakerState").value("CLOSED"))
                .andExpect(jsonPath("$.coldStart").value(true));

        mockMvc.perform(put("/api/interest/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileText\":\"Rust\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/interest/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coldStart").value(false));
    }

    @Test
    void weightOutOfRangeIs400BeforeTheDatabase() throws Exception {
        mockMvc.perform(post("/api/interest/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Rust\",\"description\":\"Rust lang\",\"weight\":51}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Weight must be a whole number from -50 to +50"));

        Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM interest_topic", Integer.class);
        assertThat(rows).isZero();
    }
}
