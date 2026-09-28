package org.bartram.myfeeder.service;

import org.bartram.myfeeder.event.ArticlesIngestedEvent;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.parser.FeedParser;
import org.bartram.myfeeder.parser.ParsedArticle;
import org.bartram.myfeeder.parser.ParsedFeed;
import org.bartram.myfeeder.repository.ArticleRepository;
import org.bartram.myfeeder.repository.FeedRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedPollingServiceTest {

    @Mock private FeedRepository feedRepository;
    @Mock private ArticleRepository articleRepository;
    @Mock private FeedParser feedParser;
    @Mock private FeedFetcher feedFetcher;
    @Mock private ApplicationEventPublisher eventPublisher;

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
        stubSaveAssigningIds(1L);

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

    @Test
    void publishesOneEventWithOnlyTheNewIds() {
        var feed = feed();
        stubFetchOk(feed);
        var existing = ParsedArticle.builder().guid("g-existing").title("Old").build();
        var fresh1 = ParsedArticle.builder().guid("g-1").title("New 1").build();
        var fresh2 = ParsedArticle.builder().guid("g-2").title("New 2").build();
        stubParse(existing, fresh1, fresh2);
        when(articleRepository.existsByFeedIdAndGuid(1L, "g-existing")).thenReturn(true);
        when(articleRepository.existsByFeedIdAndGuid(1L, "g-1")).thenReturn(false);
        when(articleRepository.existsByFeedIdAndGuid(1L, "g-2")).thenReturn(false);
        stubSaveAssigningIds(41L);

        pollingService.pollFeed(1L);

        InOrder order = inOrder(feedRepository, eventPublisher);
        order.verify(feedRepository).save(feed);
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        order.verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getAllValues()).containsExactly(new ArticlesIngestedEvent(1L, List.of(41L, 42L)));
    }

    @Test
    void publishesNothingWhenNothingIsNew() {
        var feed = feed();
        when(feedRepository.findById(1L)).thenReturn(Optional.of(feed));
        when(feedFetcher.fetch(anyString(), any(), any()))
                .thenReturn(new FetchResult(BODY, TYPE, null, null, false))
                .thenReturn(FetchResult.notModified304());
        stubParse(ParsedArticle.builder().guid("g-existing").title("Old").build());
        when(articleRepository.existsByFeedIdAndGuid(1L, "g-existing")).thenReturn(true);

        pollingService.pollFeed(1L); // nothing new
        pollingService.pollFeed(1L); // 304

        verify(articleRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void publisherFailureIsNotAPollError() {
        var feed = feed();
        stubFetchOk(feed);
        stubParse(ParsedArticle.builder().guid("g-1").title("New").build());
        when(articleRepository.existsByFeedIdAndGuid(1L, "g-1")).thenReturn(false);
        stubSaveAssigningIds(7L);
        doThrow(new RuntimeException("listener exploded")).when(eventPublisher).publishEvent(any(Object.class));

        pollingService.pollFeed(1L);

        verify(eventPublisher).publishEvent(any(Object.class));
        var captor = ArgumentCaptor.forClass(Feed.class);
        verify(feedRepository).save(captor.capture());
        assertThat(captor.getValue().getErrorCount()).isZero();
        assertThat(captor.getValue().getLastError()).isNull();
    }

    private static final byte[] BODY = "<rss/>".getBytes(StandardCharsets.UTF_8);
    private static final String TYPE = "application/rss+xml";

    private static Feed feed() {
        var feed = new Feed();
        feed.setId(1L);
        feed.setUrl("https://example.com/feed.xml");
        feed.setErrorCount(0);
        return feed;
    }

    private void stubFetchOk(Feed feed) {
        when(feedRepository.findById(1L)).thenReturn(Optional.of(feed));
        when(feedFetcher.fetch(anyString(), any(), any()))
                .thenReturn(new FetchResult(BODY, TYPE, null, null, false));
    }

    private void stubParse(ParsedArticle... articles) {
        when(feedParser.parse(BODY, TYPE))
                .thenReturn(ParsedFeed.builder().title("Feed").articles(List.of(articles)).build());
    }

    /** save() returns its argument with the next sequential id, as Spring Data JDBC does. */
    private void stubSaveAssigningIds(long firstId) {
        AtomicLong next = new AtomicLong(firstId);
        when(articleRepository.save(any())).thenAnswer(inv -> {
            Article article = inv.getArgument(0);
            article.setId(next.getAndIncrement());
            return article;
        });
    }
}
