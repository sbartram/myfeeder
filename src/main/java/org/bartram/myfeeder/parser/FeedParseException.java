package org.bartram.myfeeder.parser;

public class FeedParseException extends RuntimeException {
    public FeedParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public FeedParseException(String message) {
        super(message);
    }
}
