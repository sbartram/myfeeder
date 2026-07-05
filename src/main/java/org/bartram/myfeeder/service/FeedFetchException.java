package org.bartram.myfeeder.service;

/** The remote server answered with an HTTP error status while fetching a feed. Mapped to 422. */
public class FeedFetchException extends RuntimeException {
    public FeedFetchException(String message) {
        super(message);
    }
}
