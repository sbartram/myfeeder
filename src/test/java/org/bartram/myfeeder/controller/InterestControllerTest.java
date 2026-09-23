package org.bartram.myfeeder.controller;

import org.bartram.myfeeder.model.InterestProfile;
import org.bartram.myfeeder.model.InterestTopic;
import org.bartram.myfeeder.service.InterestService;
import org.bartram.myfeeder.service.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InterestController.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({GlobalExceptionHandler.class})
class InterestControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private InterestService interestService;

    private static InterestTopic topic(Long id, String name, String description, int weight, int version) {
        InterestTopic t = new InterestTopic();
        t.setId(id);
        t.setName(name);
        t.setDescription(description);
        t.setWeight(weight);
        t.setVersion(version);
        t.setCreatedAt(Instant.now());
        t.setUpdatedAt(Instant.now());
        return t;
    }

    @Test
    void getProfileReturnsJson() throws Exception {
        InterestProfile p = new InterestProfile();
        p.setId(1);
        p.setProfileText("Rust and Postgres");
        p.setVersion(3);
        p.setUpdatedAt(Instant.now());
        when(interestService.getProfile()).thenReturn(p);

        mockMvc.perform(get("/api/interest/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileText").value("Rust and Postgres"))
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void putProfileTooLongIs400WithDetail() throws Exception {
        when(interestService.updateProfile("too long"))
                .thenThrow(new IllegalArgumentException("The profile can be at most 2,000 characters"));

        mockMvc.perform(put("/api/interest/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileText\":\"too long\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The profile can be at most 2,000 characters"));
    }

    @Test
    void listTopicsReturnsArray() throws Exception {
        when(interestService.listTopics()).thenReturn(List.of(topic(1L, "Rust", "Rust lang", -15, 1)));

        mockMvc.perform(get("/api/interest/topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Rust"))
                .andExpect(jsonPath("$[0].weight").value(-15));
    }

    @Test
    void createTopicReturns201() throws Exception {
        when(interestService.createTopic("Rust", "Rust lang", null)).thenReturn(topic(5L, "Rust", "Rust lang", 20, 1));

        mockMvc.perform(post("/api/interest/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Rust\",\"description\":\"Rust lang\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.weight").value(20))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void createTwentySixthTopicIs400() throws Exception {
        when(interestService.createTopic("Rust", "Rust lang", 10))
                .thenThrow(new IllegalArgumentException("A maximum of 25 topics is allowed"));

        mockMvc.perform(post("/api/interest/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Rust\",\"description\":\"Rust lang\",\"weight\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("A maximum of 25 topics is allowed"));
    }

    @Test
    void updateTopicReturns200() throws Exception {
        when(interestService.updateTopic(5L, "Rust", "The Rust language", 30))
                .thenReturn(topic(5L, "Rust", "The Rust language", 30, 2));

        mockMvc.perform(put("/api/interest/topics/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Rust\",\"description\":\"The Rust language\",\"weight\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void updateMissingTopicIs404() throws Exception {
        when(interestService.updateTopic(99L, "Rust", "Rust lang", 10))
                .thenThrow(new NotFoundException("Topic not found: 99"));

        mockMvc.perform(put("/api/interest/topics/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Rust\",\"description\":\"Rust lang\",\"weight\":10}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteMissingTopicIs404() throws Exception {
        doThrow(new NotFoundException("Topic not found: 99")).when(interestService).deleteTopic(99L);

        mockMvc.perform(delete("/api/interest/topics/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteTopicReturns204() throws Exception {
        mockMvc.perform(delete("/api/interest/topics/5"))
                .andExpect(status().isNoContent());
        verify(interestService).deleteTopic(5L);
    }
}
