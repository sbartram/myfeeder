package org.bartram.myfeeder.controller;

/**
 * The Re-score confirmation. Requiring a JSON body makes a cross-origin POST need a CORS preflight
 * (which the server never approves), so a foreign page cannot trigger the destructive, billed reset.
 */
public record RescoreRequest(boolean confirm) {}
