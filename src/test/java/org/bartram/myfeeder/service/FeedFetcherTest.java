package org.bartram.myfeeder.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FeedFetcherTest {

    private MockRestServiceServer server;
    private FeedFetcher fetcher;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        fetcher = new FeedFetcher(builder);
    }

    @Test
    void decodesBodyUsingResponseCharset() {
        byte[] latin1 = "<rss><title>café</title></rss>".getBytes(StandardCharsets.ISO_8859_1);
        server.expect(requestTo("https://example.com/feed"))
                .andRespond(withSuccess(latin1,
                        new MediaType("application", "rss+xml", StandardCharsets.ISO_8859_1)));

        FetchResult result = fetcher.fetch("https://example.com/feed");

        assertThat(result.body()).contains("café");
        assertThat(result.notModified()).isFalse();
    }

    @Test
    void sendsConditionalHeadersAndMaps304() {
        server.expect(requestTo("https://example.com/feed"))
                .andExpect(header(HttpHeaders.IF_NONE_MATCH, "\"abc\""))
                .andExpect(header(HttpHeaders.IF_MODIFIED_SINCE, "Tue, 01 Jul 2026 00:00:00 GMT"))
                .andRespond(withStatus(HttpStatus.NOT_MODIFIED));

        FetchResult result = fetcher.fetch("https://example.com/feed", "\"abc\"",
                "Tue, 01 Jul 2026 00:00:00 GMT");

        assertThat(result.notModified()).isTrue();
    }

    @Test
    void capturesCachingHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setETag("\"v2\"");
        headers.set(HttpHeaders.LAST_MODIFIED, "Wed, 02 Jul 2026 00:00:00 GMT");
        server.expect(requestTo("https://example.com/feed"))
                .andRespond(withSuccess("<rss/>", MediaType.APPLICATION_XML).headers(headers));

        FetchResult result = fetcher.fetch("https://example.com/feed");

        assertThat(result.etag()).isEqualTo("\"v2\"");
        assertThat(result.lastModified()).isEqualTo("Wed, 02 Jul 2026 00:00:00 GMT");
    }

    @Test
    void throwsOnHttpErrorStatus() {
        server.expect(requestTo("https://example.com/feed"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> fetcher.fetch("https://example.com/feed"))
                .isInstanceOf(FeedFetchException.class)
                .hasMessageContaining("404");
    }
}
