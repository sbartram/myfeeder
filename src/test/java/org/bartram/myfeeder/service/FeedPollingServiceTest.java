package org.bartram.myfeeder.service;

import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.parser.FeedParser;
import org.bartram.myfeeder.repository.ArticleRepository;
import org.bartram.myfeeder.repository.FeedRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedPollingServiceTest {

    @Mock private FeedRepository feedRepository;
    @Mock private ArticleRepository articleRepository;
    @Mock private FeedParser feedParser;
    @Mock private RestClient.Builder restClientBuilder;

    @InjectMocks
    private FeedPollingService pollingService;

    @Test
    void shouldIncrementErrorCountOnFailure() {
        var feed = new Feed();
        feed.setId(1L);
        feed.setUrl("https://example.com/feed.xml");
        feed.setErrorCount(0);
        when(feedRepository.findById(1L)).thenReturn(Optional.of(feed));

        // RestClient.Builder.build() throws to simulate connection failure
        when(restClientBuilder.build()).thenThrow(new RuntimeException("Connection refused"));

        pollingService.pollFeed(1L);

        var captor = ArgumentCaptor.forClass(Feed.class);
        verify(feedRepository).save(captor.capture());
        assertThat(captor.getValue().getErrorCount()).isEqualTo(1);
        assertThat(captor.getValue().getLastError()).contains("Connection refused");
    }
}
