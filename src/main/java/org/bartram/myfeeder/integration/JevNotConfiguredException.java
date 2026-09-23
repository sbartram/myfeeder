package org.bartram.myfeeder.integration;

public class JevNotConfiguredException extends RuntimeException {
    public JevNotConfiguredException() {
        super("TypeSafe Jev is not configured. Set the MYFEEDER_TYPESAFE_API_KEY environment variable.");
    }
}
