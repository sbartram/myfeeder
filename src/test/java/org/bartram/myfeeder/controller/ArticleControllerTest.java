package org.bartram.myfeeder.controller;

import org.bartram.myfeeder.integration.RaindropService;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.model.InterestBreakdown;
import org.bartram.myfeeder.model.InterestBreakdown.NonMatchingTopic;
import org.bartram.myfeeder.model.InterestBreakdown.Row;
import org.bartram.myfeeder.repository.InterestScoreQueries.PriorityRow;
import org.bartram.myfeeder.repository.InterestScoreQueries.SortKey;
import org.bartram.myfeeder.service.ArticleExtractionService;
import org.bartram.myfeeder.service.ArticleService;
import org.bartram.myfeeder.service.ExtractedContent;
import org.bartram.myfeeder.service.FeedFetchException;
import org.bartram.myfeeder.service.NotFoundException;
import org.bartram.myfeeder.service.PriorityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ArticleController.class)
class ArticleControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ArticleService articleService;
    @MockitoBean private RaindropService raindropService;
    @MockitoBean private ArticleExtractionService articleExtractionService;
    @MockitoBean private PriorityService priorityService;

    @Test
    void shouldReturnExtractedContent() throws Exception {
        when(articleExtractionService.extract(5L))
                .thenReturn(new ExtractedContent("Page Title", "<p>extracted</p>"));

        mockMvc.perform(get("/api/articles/5/extracted-content"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Page Title"))
                .andExpect(jsonPath("$.contentHtml").value("<p>extracted</p>"));
    }

    @Test
    void extractedContentReturns404ForMissingArticle() throws Exception {
        when(articleExtractionService.extract(99L))
                .thenThrow(new NotFoundException("Article not found: 99"));

        mockMvc.perform(get("/api/articles/99/extracted-content"))
                .andExpect(status().isNotFound());
    }

    @Test
    void extractedContentReturns422WhenPageFetchFails() throws Exception {
        when(articleExtractionService.extract(5L))
                .thenThrow(new FeedFetchException("HTTP 403 fetching https://example.com/post"));

        mockMvc.perform(get("/api/articles/5/extracted-content"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void shouldListArticles() throws Exception {
        var article = new Article();
        article.setId(1L);
        article.setTitle("Test Article");
        article.setFetchedAt(Instant.now());
        when(articleService.findFiltered(null, null, null, null, 51, false)).thenReturn(List.of(article));

        mockMvc.perform(get("/api/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("Test Article"));
    }

    @Test
    void capsLimitAtServerMaximum() throws Exception {
        when(articleService.findFiltered(any(), any(), any(), any(), anyInt(), anyBoolean()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/articles?limit=1000000"))
                .andExpect(status().isOk());

        // clamped to 100, then +1 for the pagination look-ahead row
        verify(articleService).findFiltered(null, null, null, null, 101, false);
    }

    @Test
    void clampsNonPositiveLimitToOne() throws Exception {
        when(articleService.findFiltered(any(), any(), any(), any(), anyInt(), anyBoolean()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/articles?limit=0"))
                .andExpect(status().isOk());

        verify(articleService).findFiltered(null, null, null, null, 2, false);
    }

    @Test
    void doesNotOverflowAtIntegerMaxLimit() throws Exception {
        when(articleService.findFiltered(any(), any(), any(), any(), anyInt(), anyBoolean()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/articles?limit=2147483647"))
                .andExpect(status().isOk());

        // without the clamp, limit + 1 overflows to Integer.MIN_VALUE
        verify(articleService).findFiltered(null, null, null, null, 101, false);
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
        when(articleService.findByIdWithBreakdown(1L)).thenReturn(Optional.of(article));

        mockMvc.perform(get("/api/articles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("<p>Full content</p>"));
    }

    @Test
    void getArticleSerializesTheBreakdown() throws Exception {
        var article = new Article();
        article.setId(1L);
        article.setTitle("Scored");
        article.setInterestScore(82);
        article.setInterestBreakdown(new InterestBreakdown(new BigDecimal("82.200000"), 82, 82,
                List.of(Row.profile(3, new BigDecimal("64.000000"), 64),
                        Row.topic(10L, "Rust", 0.93, 0.86, 20, new BigDecimal("17.200000"), 17),
                        Row.topic(11L, "WebAssembly", 0.75, 0.5, 14, new BigDecimal("7.000000"), 7),
                        Row.topic(12L, "Politics", 0.6, 0.2, -30, new BigDecimal("-6.000000"), -6)),
                List.of(new NonMatchingTopic(13L, "Gardening", 0.2))));
        when(articleService.findByIdWithBreakdown(1L)).thenReturn(Optional.of(article));

        mockMvc.perform(get("/api/articles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interestScore").value(82))
                .andExpect(jsonPath("$.interestBreakdown.total").value(82))
                .andExpect(jsonPath("$.interestBreakdown.display").value(82))
                .andExpect(jsonPath("$.interestBreakdown.rows[0].kind").value("PROFILE"))
                .andExpect(jsonPath("$.interestBreakdown.rows[0].levelIndex").value(3))
                .andExpect(jsonPath("$.interestBreakdown.rows[0].points").value(64))
                .andExpect(jsonPath("$.interestBreakdown.rows[0].topicId").doesNotExist())
                .andExpect(jsonPath("$.interestBreakdown.rows[1].kind").value("TOPIC"))
                .andExpect(jsonPath("$.interestBreakdown.rows[1].name").value("Rust"))
                .andExpect(jsonPath("$.interestBreakdown.rows[1].levelIndex").doesNotExist())
                .andExpect(jsonPath("$.interestBreakdown.nonMatching[0].name").value("Gardening"));
        verify(articleService, never()).findById(1L);
    }

    @Test
    void listItemsOmitTheBreakdown() throws Exception {
        var article = new Article();
        article.setId(1L);
        article.setTitle("Listed");
        article.setFetchedAt(Instant.now());
        article.setInterestScore(50);
        when(articleService.findFiltered(null, null, null, null, 51, false)).thenReturn(List.of(article));

        mockMvc.perform(get("/api/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].interestScore").value(50))
                .andExpect(jsonPath("$.items[0].interestBreakdown").doesNotExist());
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
    void updateStateReturns404WhenArticleMissing() throws Exception {
        when(articleService.updateState(eq(99L), any(), any()))
                .thenThrow(new NotFoundException("Article not found: 99"));

        mockMvc.perform(patch("/api/articles/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"read\":true}"))
                .andExpect(status().isNotFound());
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

    @Test
    void priorityRouteIsNotTheIdRoute() throws Exception {
        var row = priorityRow(3L);
        row.article().setInterestScore(82);
        when(priorityService.page(null, 51)).thenReturn(List.of(row));

        mockMvc.perform(get("/api/articles/priority"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(3))
                .andExpect(jsonPath("$.items[0].interestScore").value(82))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));

        verify(articleService, never()).findById(any());
    }

    @Test
    void priorityClampsLimit() throws Exception {
        when(priorityService.page(any(), anyInt())).thenReturn(List.of());

        for (String limit : List.of("0", "-5", "1")) {
            mockMvc.perform(get("/api/articles/priority?limit=" + limit)).andExpect(status().isOk());
        }
        // clamped to 1, then +1 for the pagination look-ahead row
        verify(priorityService, times(3)).page(null, 2);

        for (String limit : List.of("100", "101")) {
            mockMvc.perform(get("/api/articles/priority?limit=" + limit)).andExpect(status().isOk());
        }
        verify(priorityService, times(2)).page(null, 101);
    }

    @Test
    void priorityPassesCursor() throws Exception {
        SortKey key = new SortKey(82.2, Instant.parse("2026-09-25T12:34:56.123456Z"), 7L);
        when(priorityService.page(any(), anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/api/articles/priority").param("before", PriorityPage.encodeCursor(key)))
                .andExpect(status().isOk());

        verify(priorityService).page(key, 51);
    }

    @Test
    void priorityMissingCursorIs404() throws Exception {
        SortKey key = new SortKey(50.0, Instant.parse("2026-09-25T12:34:56Z"), 9L);
        when(priorityService.page(eq(key), anyInt()))
                .thenThrow(new NotFoundException("Article not found: 9"));

        mockMvc.perform(get("/api/articles/priority").param("before", PriorityPage.encodeCursor(key)))
                .andExpect(status().isNotFound());
    }

    @Test
    void priorityUnreadableCursorIs404WithoutCallingTheService() throws Exception {
        mockMvc.perform(get("/api/articles/priority?before=12345"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Priority cursor not recognized"));

        verify(priorityService, never()).page(any(), anyInt());
    }

    @Test
    void priorityTrimsLookAheadRowAndSetsNextCursor() throws Exception {
        when(priorityService.page(null, 3))
                .thenReturn(List.of(priorityRow(10L), priorityRow(11L), priorityRow(12L)));

        mockMvc.perform(get("/api/articles/priority?limit=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[1].id").value(11))
                .andExpect(jsonPath("$.nextCursor").value(PriorityPage.encodeCursor(keyFor(11L))));

        when(priorityService.page(null, 3))
                .thenReturn(List.of(priorityRow(10L), priorityRow(11L)));

        mockMvc.perform(get("/api/articles/priority?limit=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    private static PriorityRow priorityRow(long id) {
        return new PriorityRow(articleWithId(id), keyFor(id));
    }

    private static SortKey keyFor(long id) {
        return new SortKey(82.2, Instant.parse("2026-09-25T12:00:00.123456Z"), id);
    }

    private static Article articleWithId(long id) {
        var article = new Article();
        article.setId(id);
        return article;
    }
}
