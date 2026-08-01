package org.bartram.myfeeder.service;

import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.parser.FeedParser;
import org.bartram.myfeeder.parser.ParsedArticle;
import org.bartram.myfeeder.parser.ParsedFeed;
import org.bartram.myfeeder.repository.ArticleRepository;
import org.bartram.myfeeder.repository.FeedRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedPollingServiceTest {

    @Mock private FeedRepository feedRepository;
    @Mock private ArticleRepository articleRepository;
    @Mock private FeedParser feedParser;
    @Mock private FeedFetcher feedFetcher;

    @InjectMocks
    private FeedPollingService pollingService;

    @Test
    void shouldIncrementErrorCountOnFailure() {
        var feed = new Feed();
        feed.setId(1L);
        feed.setUrl("https://example.com/feed.xml");
        feed.setErrorCount(0);
        when(feedRepository.findById(1L)).thenReturn(Optional.of(feed));
        when(feedFetcher.fetch(anyString(), any(), any()))
                .thenThrow(new RuntimeException("Connection refused"));

        pollingService.pollFeed(1L);

        var captor = ArgumentCaptor.forClass(Feed.class);
        verify(feedRepository).save(captor.capture());
        assertThat(captor.getValue().getErrorCount()).isEqualTo(1);
        assertThat(captor.getValue().getLastError()).contains("Connection refused");
    }

    @Test
    void shouldSkipParsingWhenNotModified() {
        var feed = new Feed();
        feed.setId(1L);
        feed.setUrl("https://example.com/feed.xml");
        when(feedRepository.findById(1L)).thenReturn(Optional.of(feed));
        when(feedFetcher.fetch(anyString(), any(), any())).thenReturn(FetchResult.notModified304());

        pollingService.pollFeed(1L);

        verify(feedParser, never()).parse(any(), any());
        verify(articleRepository, never()).save(any());
        var captor = ArgumentCaptor.forClass(Feed.class);
        verify(feedRepository).save(captor.capture());
        assertThat(captor.getValue().getLastPolledAt()).isNotNull();
    }

    @Test
    void shouldSaveOnlyNewArticles() {
        var feed = new Feed();
        feed.setId(1L);
        feed.setUrl("https://example.com/feed.xml");
        feed.setErrorCount(3);
        when(feedRepository.findById(1L)).thenReturn(Optional.of(feed));
        when(feedFetcher.fetch(anyString(), any(), any()))
                .thenReturn(new FetchResult("<rss/>".getBytes(StandardCharsets.UTF_8),
                        "application/rss+xml", "\"tag\"", null, false));

        var existing = ParsedArticle.builder().guid("g-existing").title("Old").build();
        var fresh = ParsedArticle.builder().guid("g-fresh").title("New").build();
        var parsed = ParsedFeed.builder().title("Feed").articles(List.of(existing, fresh)).build();
        when(feedParser.parse("<rss/>".getBytes(StandardCharsets.UTF_8), "application/rss+xml"))
                .thenReturn(parsed);
        when(articleRepository.existsByFeedIdAndGuid(1L, "g-existing")).thenReturn(true);
        when(articleRepository.existsByFeedIdAndGuid(1L, "g-fresh")).thenReturn(false);

        pollingService.pollFeed(1L);

        verify(articleRepository).save(any());
        var captor = ArgumentCaptor.forClass(Feed.class);
        verify(feedRepository).save(captor.capture());
        Feed saved = captor.getValue();
        assertThat(saved.getErrorCount()).isZero();
        assertThat(saved.getLastError()).isNull();
        assertThat(saved.getEtag()).isEqualTo("\"tag\"");
        assertThat(saved.getLastSuccessfulPollAt()).isNotNull();
    }
}
