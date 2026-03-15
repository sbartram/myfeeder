package org.bartram.myfeeder.parser;

public class OpmlParseException extends RuntimeException {
    public OpmlParseException(String message) {
        super(message);
    }

    public OpmlParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
