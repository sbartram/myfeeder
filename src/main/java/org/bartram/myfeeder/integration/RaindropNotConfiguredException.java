package org.bartram.myfeeder.integration;

public class RaindropNotConfiguredException extends RuntimeException {
    public RaindropNotConfiguredException() {
        super("Raindrop integration is not configured. Set the MYFEEDER_RAINDROP_API_TOKEN environment variable.");
    }
}
