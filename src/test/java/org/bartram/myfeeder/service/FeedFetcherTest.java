package org.bartram.myfeeder.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FeedFetcherTest {

    /** Resolves every host to a public IP, so URL validation never blocks the stubbed test hosts. */
    private static final FeedUrlValidator ALLOW_ALL =
            new FeedUrlValidator(host -> new InetAddress[]{InetAddress.getByName("8.8.8.8")});

    private MockRestServiceServer server;
    private FeedFetcher fetcher;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        fetcher = new FeedFetcher(builder, ALLOW_ALL);
    }

    @Test
    void returnsRawBodyBytesWithContentType() {
        byte[] latin1 = "<rss><title>café</title></rss>".getBytes(StandardCharsets.ISO_8859_1);
        server.expect(requestTo("https://example.com/feed"))
                .andRespond(withSuccess(latin1,
                        new MediaType("application", "rss+xml", StandardCharsets.ISO_8859_1)));

        FetchResult result = fetcher.fetch("https://example.com/feed");

        assertThat(result.body()).isEqualTo(latin1);
        assertThat(result.contentType()).containsIgnoringCase("charset=ISO-8859-1");
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

    @Test
    void rejectsBodyExceedingMaxSize() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer cappedServer = MockRestServiceServer.bindTo(builder).build();
        FeedFetcher cappedFetcher = new FeedFetcher(builder, ALLOW_ALL, 16);
        cappedServer.expect(requestTo("https://example.com/big"))
                .andRespond(withSuccess("this body is well over sixteen bytes long",
                        MediaType.APPLICATION_XML));

        assertThatThrownBy(() -> cappedFetcher.fetch("https://example.com/big"))
                .isInstanceOf(FeedFetchException.class)
                .hasMessageContaining("exceeds");
    }

    @Test
    void acceptsBodyWithinMaxSize() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer cappedServer = MockRestServiceServer.bindTo(builder).build();
        FeedFetcher cappedFetcher = new FeedFetcher(builder, ALLOW_ALL, 1024);
        cappedServer.expect(requestTo("https://example.com/small"))
                .andRespond(withSuccess("<rss><title>ok</title></rss>", MediaType.APPLICATION_XML));

        FetchResult result = cappedFetcher.fetch("https://example.com/small");

        assertThat(new String(result.body(), StandardCharsets.UTF_8)).contains("ok");
    }

    @Test
    void rejectsNonPublicUrlBeforeFetching() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        // No request expectations: if validation were skipped, the fetch would fail differently.
        MockRestServiceServer.bindTo(builder).build();
        FeedUrlValidator blocking =
                new FeedUrlValidator(host -> new InetAddress[]{InetAddress.getByName("127.0.0.1")});
        FeedFetcher blockedFetcher = new FeedFetcher(builder, blocking);

        assertThatThrownBy(() -> blockedFetcher.fetch("http://localhost/feed"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-public");
    }
}
