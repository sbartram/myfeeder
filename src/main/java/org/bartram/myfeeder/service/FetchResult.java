package org.bartram.myfeeder.service;

/**
 * Outcome of one HTTP feed fetch. On 304, notModified is true and all other fields are null.
 * body holds the undecoded transport bytes and contentType the raw Content-Type header value
 * (null if absent); charset resolution is the parser's job (header charset, else BOM/prolog).
 */
public record FetchResult(byte[] body, String contentType, String etag, String lastModified,
                          boolean notModified) {
    public static FetchResult notModified304() {
        return new FetchResult(null, null, null, null, true);
    }
}
