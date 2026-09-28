package org.bartram.myfeeder.controller;

import org.bartram.myfeeder.service.InterestRescoreService;
import org.bartram.myfeeder.service.RescoreCount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InterestRescoreController.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({GlobalExceptionHandler.class})
class InterestRescoreControllerTest {

    private static final String CONFIRM = "{\"confirm\":true}";
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

        mockMvc.perform(post("/api/interest/rescore").contentType(MediaType.APPLICATION_JSON).content(CONFIRM))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(5))
                .andExpect(jsonPath("$.windowDays").value(14));
    }

    @Test
    void postWhenItCannotRescoreIs409() throws Exception {
        when(service.rescore()).thenThrow(new IllegalStateException(REFUSAL));

        mockMvc.perform(post("/api/interest/rescore").contentType(MediaType.APPLICATION_JSON).content(CONFIRM))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Configuration error"))
                .andExpect(jsonPath("$.detail").value(REFUSAL));
    }

    @Test
    void crossSiteSimplePostsAreRejectedWithoutAReset() throws Exception {
        // A body-less POST or a form/text POST is a CORS "simple request" a foreign page can send
        mockMvc.perform(post("/api/interest/rescore"))
                .andExpect(status().isUnsupportedMediaType());
        mockMvc.perform(post("/api/interest/rescore")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).content("confirm=true"))
                .andExpect(status().isUnsupportedMediaType());
        mockMvc.perform(post("/api/interest/rescore").contentType(MediaType.TEXT_PLAIN).content(CONFIRM))
                .andExpect(status().isUnsupportedMediaType());

        verify(service, never()).rescore();
    }

    @Test
    void postWithoutConfirmIs400WithoutAReset() throws Exception {
        mockMvc.perform(post("/api/interest/rescore").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/interest/rescore")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":false}"))
                .andExpect(status().isBadRequest());

        verify(service, never()).rescore();
    }
}
