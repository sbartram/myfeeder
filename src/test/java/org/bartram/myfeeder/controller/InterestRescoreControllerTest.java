package org.bartram.myfeeder.controller;

import org.bartram.myfeeder.service.InterestRescoreService;
import org.bartram.myfeeder.service.RescoreCount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InterestRescoreController.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({GlobalExceptionHandler.class})
class InterestRescoreControllerTest {

    private static final String REFUSAL = "Re-score needs a TypeSafe API key and a profile or at least one topic";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private InterestRescoreService service;

    @Test
    void getReturnsCountAndWindow() throws Exception {
        when(service.count()).thenReturn(new RescoreCount(312, 14));

        mockMvc.perform(get("/api/interest/rescore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(312))
                .andExpect(jsonPath("$.windowDays").value(14));
    }

    @Test
    void postReturnsTheResetCount() throws Exception {
        when(service.rescore()).thenReturn(new RescoreCount(5, 14));

        mockMvc.perform(post("/api/interest/rescore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(5))
                .andExpect(jsonPath("$.windowDays").value(14));
    }

    @Test
    void postWhenItCannotRescoreIs409() throws Exception {
        when(service.rescore()).thenThrow(new IllegalStateException(REFUSAL));

        mockMvc.perform(post("/api/interest/rescore"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Configuration error"))
                .andExpect(jsonPath("$.detail").value(REFUSAL));
    }
}
