package org.bartram.myfeeder.service;

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

    private final RestClient restClient;

    public FeedFetcher(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    /** Unconditional fetch, used at subscribe time. */
    public FetchResult fetch(String url) {
        return fetch(url, null, null);
    }

    /**
     * Conditional fetch: sends If-None-Match / If-Modified-Since when etag / lastModified are
     * non-null. Returns FetchResult.notModified() on 304; throws FeedFetchException on 4xx/5xx.
     */
    public FetchResult fetch(String url, String etag, String lastModified) {
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
                    String body = new String(response.getBody().readAllBytes(), charsetOf(response.getHeaders()));
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
