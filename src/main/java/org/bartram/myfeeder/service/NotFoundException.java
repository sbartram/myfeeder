package org.bartram.myfeeder.service;

/** Thrown when an entity addressed by a path variable does not exist. Mapped to HTTP 404. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
