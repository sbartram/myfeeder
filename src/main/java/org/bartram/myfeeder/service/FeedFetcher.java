package org.bartram.myfeeder.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Owns HTTP retrieval of feed documents: conditional requests (ETag / Last-Modified),
 * charset-correct body decoding, and error-status handling. The RestClient.Builder is the
 * auto-configured bean, so the myfeeder User-Agent customizer applies (see RestClientConfig).
 */
@Component
public class FeedFetcher {

    /** Default cap on a decoded feed body; bounds heap use so a huge response can't OOM the pod. */
    static final int DEFAULT_MAX_FEED_BYTES = 10 * 1024 * 1024;

    private final RestClient restClient;
    private final FeedUrlValidator urlValidator;
    private final int maxFeedBytes;

    @Autowired
    public FeedFetcher(RestClient.Builder builder, FeedUrlValidator urlValidator) {
        this(builder, urlValidator, DEFAULT_MAX_FEED_BYTES);
    }

    /** Overloaded constructor exposing the size cap so callers/tests can bound the read explicitly. */
    FeedFetcher(RestClient.Builder builder, FeedUrlValidator urlValidator, int maxFeedBytes) {
        this.restClient = builder.build();
        this.urlValidator = urlValidator;
        this.maxFeedBytes = maxFeedBytes;
    }

    /** Unconditional fetch, used at subscribe time. */
    public FetchResult fetch(String url) {
        return fetch(url, null, null);
    }

    /**
     * Conditional fetch: sends If-None-Match / If-Modified-Since when etag / lastModified are
     * non-null. Returns FetchResult.notModified304() on 304; throws FeedFetchException on 4xx/5xx.
     */
    public FetchResult fetch(String url, String etag, String lastModified) {
        urlValidator.validate(url);
        return restClient.get()
                .uri(url)
                .headers(headers -> {
                    if (etag != null) {
                        headers.setIfNoneMatch(etag);
                    }
                    if (lastModified != null) {
                        headers.set(HttpHeaders.IF_MODIFIED_SINCE, lastModified);
                    }
                })
                .exchange((request, response) -> {
                    if (response.getStatusCode().value() == 304) {
                        return FetchResult.notModified304();
                    }
                    if (response.getStatusCode().isError()) {
                        throw new FeedFetchException(
                                "HTTP " + response.getStatusCode().value() + " fetching " + url);
                    }
                    // Bounded read: pull at most maxFeedBytes + 1 so an unbounded/streaming body
                    // can't exhaust heap. Exceeding the cap is a fetch failure (mapped to 422).
                    byte[] bytes = response.getBody().readNBytes(maxFeedBytes + 1);
                    if (bytes.length > maxFeedBytes) {
                        throw new FeedFetchException(
                                "Feed body exceeds " + maxFeedBytes + " bytes fetching " + url);
                    }
                    String body = new String(bytes, charsetOf(response.getHeaders()));
                    return new FetchResult(
                            body,
                            response.getHeaders().getETag(),
                            response.getHeaders().getFirst(HttpHeaders.LAST_MODIFIED),
                            false);
                });
    }

    private Charset charsetOf(HttpHeaders headers) {
        MediaType contentType = headers.getContentType();
        return contentType != null && contentType.getCharset() != null
                ? contentType.getCharset() : StandardCharsets.UTF_8;
    }
}
