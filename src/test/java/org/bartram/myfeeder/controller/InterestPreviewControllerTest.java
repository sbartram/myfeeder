package org.bartram.myfeeder.controller;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.bartram.myfeeder.integration.JevNotConfiguredException;
import org.bartram.myfeeder.service.InterestPreviewService;
import org.bartram.myfeeder.service.NotFoundException;
import org.bartram.myfeeder.service.TopicPreviewResponse;
import org.junit.jupiter.api.Test;
import org.springaicommunity.typesafe.exception.TypeSafeApiConnectionException;
import org.springaicommunity.typesafe.exception.TypeSafeBadRequestException;
import org.springaicommunity.typesafe.exception.TypeSafeInternalServerException;
import org.springaicommunity.typesafe.exception.TypeSafeUnprocessableEntityException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InterestPreviewController.class)
@ImportAutoConfiguration(JacksonAutoConfiguration.class)
@Import({GlobalExceptionHandler.class})
class InterestPreviewControllerTest {

    private static final String BODY = "{\"articleId\":1,\"description\":\"Rust\",\"topicId\":null}";
    private static final String ENDPOINT = "/v1/jev";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private InterestPreviewService previewService;

    @Test
    void previewReturnsNoulAndModel() throws Exception {
        when(previewService.preview(1L, "Rust", null)).thenReturn(new TopicPreviewResponse(0.82, "jev-1.13.0"));

        postPreview()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noul").value(0.82))
                .andExpect(jsonPath("$.model").value("jev-1.13.0"));
    }

    @Test
    void notConfiguredIs503WithTitle() throws Exception {
        when(previewService.preview(any(), any(), any())).thenThrow(new JevNotConfiguredException());

        postPreview()
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Jev not configured"));
    }

    @Test
    void breakerOpenIs503JevUnavailable() throws Exception {
        when(previewService.preview(any(), any(), any())).thenThrow(
                CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("jev")));

        postPreview()
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Jev unavailable"))
                .andExpect(jsonPath("$.detail").value("Jev is temporarily unavailable"));
    }

    @Test
    void jevBadRequestIs422() throws Exception {
        when(previewService.preview(any(), any(), any())).thenThrow(
                new TypeSafeBadRequestException("bad", 400, "{}", new HttpHeaders(), ENDPOINT));

        postPreview()
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.title").value("Jev rejected the request"))
                .andExpect(jsonPath("$.detail").value("Jev rejected the request (HTTP 400)"));
    }

    @Test
    void jevUnprocessableIs422() throws Exception {
        when(previewService.preview(any(), any(), any())).thenThrow(
                new TypeSafeUnprocessableEntityException("bad", 422, "{}", new HttpHeaders(), ENDPOINT));

        postPreview()
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.title").value("Jev rejected the request"))
                .andExpect(jsonPath("$.detail").value("Jev rejected the request (HTTP 422)"));
    }

    @Test
    void otherTypeSafeFailuresAre503() throws Exception {
        when(previewService.preview(any(), any(), any())).thenThrow(
                new TypeSafeInternalServerException("boom", 500, "{}", new HttpHeaders(), ENDPOINT));
        postPreview()
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Jev request failed"));

        doThrow(new TypeSafeApiConnectionException("timed out", new RuntimeException("io")))
                .when(previewService).preview(any(), any(), any());
        postPreview()
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Jev request failed"));
    }

    @Test
    void jevErrorDetailsNeverEchoExceptionText() throws Exception {
        String leak = "LEAKCHECK";
        RuntimeException[] failures = {
                new TypeSafeBadRequestException(leak, 400, leak, new HttpHeaders(), ENDPOINT),
                new TypeSafeUnprocessableEntityException(leak, 422, leak, new HttpHeaders(), ENDPOINT),
                new TypeSafeInternalServerException(leak, 500, leak, new HttpHeaders(), ENDPOINT),
                new TypeSafeApiConnectionException(leak, new RuntimeException(leak)),
        };
        for (RuntimeException failure : failures) {
            doThrow(failure).when(previewService).preview(any(), any(), any());
            String body = postPreview().andReturn().getResponse().getContentAsString();
            assertThat(body).isNotBlank().doesNotContain(leak);
        }
    }

    @Test
    void blankDescriptionIs400() throws Exception {
        when(previewService.preview(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Write a description first"));

        postPreview()
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Write a description first"));
    }

    @Test
    void missingArticleIs404() throws Exception {
        when(previewService.preview(any(), any(), any())).thenThrow(new NotFoundException("Article not found: 1"));

        postPreview()
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Article not found: 1"));
    }

    private ResultActions postPreview() throws Exception {
        return mockMvc.perform(post("/api/interest/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY));
    }
}
