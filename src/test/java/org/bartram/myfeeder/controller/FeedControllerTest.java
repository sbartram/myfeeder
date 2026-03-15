package org.bartram.myfeeder.controller;

import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.model.FeedType;
import org.bartram.myfeeder.service.FeedPollingService;
import org.bartram.myfeeder.service.FeedService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FeedController.class)
class FeedControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private FeedService feedService;
    @MockitoBean private FeedPollingService feedPollingService;

    @Test
    void shouldListFeeds() throws Exception {
        var feed = new Feed();
        feed.setId(1L);
        feed.setTitle("Test Feed");
        feed.setUrl("https://example.com/feed.xml");
        feed.setFeedType(FeedType.RSS);
        feed.setCreatedAt(Instant.now());
        when(feedService.findAll()).thenReturn(List.of(feed));

        mockMvc.perform(get("/api/feeds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Test Feed"));
    }

    @Test
    void shouldSubscribeToFeed() throws Exception {
        var feed = new Feed();
        feed.setId(1L);
        feed.setTitle("New Feed");
        feed.setFeedType(FeedType.RSS);
        when(feedService.subscribe("https://example.com/feed.xml")).thenReturn(feed);

        mockMvc.perform(post("/api/feeds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/feed.xml\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("New Feed"));
    }

    @Test
    void shouldReturn404ForUnknownFeed() throws Exception {
        when(feedService.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/feeds/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldDeleteFeed() throws Exception {
        mockMvc.perform(delete("/api/feeds/1"))
                .andExpect(status().isNoContent());
        verify(feedService).delete(1L);
    }

    @Test
    void shouldTriggerManualPoll() throws Exception {
        mockMvc.perform(post("/api/feeds/1/poll"))
                .andExpect(status().isAccepted());
        verify(feedPollingService).pollFeed(1L);
    }
}
