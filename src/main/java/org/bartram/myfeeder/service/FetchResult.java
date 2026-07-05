package org.bartram.myfeeder.service;

/** Outcome of one HTTP feed fetch. On 304, notModified is true and all other fields are null. */
public record FetchResult(String body, String etag, String lastModified, boolean notModified) {
    public static FetchResult notModified304() {
        return new FetchResult(null, null, null, true);
    }
}
