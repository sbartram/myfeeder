package org.bartram.myfeeder.controller;

import org.bartram.myfeeder.integration.RaindropService;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.service.ArticleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ArticleController.class)
class ArticleControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ArticleService articleService;
    @MockitoBean private RaindropService raindropService;

    @Test
    void shouldListArticles() throws Exception {
        var article = new Article();
        article.setId(1L);
        article.setTitle("Test Article");
        article.setFetchedAt(Instant.now());
        when(articleService.findFiltered(null, null, null, null, 51)).thenReturn(List.of(article));

        mockMvc.perform(get("/api/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.articles[0].title").value("Test Article"));
    }

    @Test
    void shouldReturnUnreadCounts() throws Exception {
        when(articleService.countUnreadByFeed()).thenReturn(Map.of(1L, 5L, 2L, 3L));

        mockMvc.perform(get("/api/articles/counts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.1").value(5))
                .andExpect(jsonPath("$.2").value(3));
    }

    @Test
    void shouldGetArticleById() throws Exception {
        var article = new Article();
        article.setId(1L);
        article.setTitle("Test");
        article.setContent("<p>Full content</p>");
        when(articleService.findById(1L)).thenReturn(Optional.of(article));

        mockMvc.perform(get("/api/articles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("<p>Full content</p>"));
    }

    @Test
    void shouldPatchArticleState() throws Exception {
        var article = new Article();
        article.setId(1L);
        article.setRead(true);
        when(articleService.updateState(1L, true, null)).thenReturn(article);

        mockMvc.perform(patch("/api/articles/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"read\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
    }

    @Test
    void shouldBulkMarkRead() throws Exception {
        mockMvc.perform(post("/api/articles/mark-read")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"articleIds\":[1,2,3]}"))
                .andExpect(status().isNoContent());

        verify(articleService).markRead(List.of(1L, 2L, 3L), null, null);
    }

    @Test
    void shouldMarkReadOlderThanDays() throws Exception {
        mockMvc.perform(post("/api/articles/mark-read")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feedId\":5,\"olderThanDays\":7}"))
                .andExpect(status().isNoContent());

        verify(articleService).markRead(null, 5L, 7);
    }

    @Test
    void shouldReturn400ForInvalidOlderThanDays() throws Exception {
        doThrow(new IllegalArgumentException("olderThanDays must be >= 1"))
                .when(articleService).markRead(null, 5L, 0);

        mockMvc.perform(post("/api/articles/mark-read")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feedId\":5,\"olderThanDays\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldSaveToRaindrop() throws Exception {
        var article = new Article();
        article.setId(1L);
        article.setTitle("Test");
        article.setUrl("https://example.com");
        when(articleService.findById(1L)).thenReturn(Optional.of(article));

        mockMvc.perform(post("/api/articles/1/raindrop"))
                .andExpect(status().isOk());

        verify(raindropService).saveToRaindrop(article);
    }
}
