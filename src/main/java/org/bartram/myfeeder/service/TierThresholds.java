package org.bartram.myfeeder.service;

/**
 * Badge tier thresholds served on {@code GET /api/interest/status} (D-13): a display score at or
 * above {@code high} is high, at or above {@code neutral} is neutral, else low. Values come from
 * {@code myfeeder.interest.blend.tiers.*}; the client falls back to 70/40 until status loads.
 */
public record TierThresholds(int high, int neutral) {
}
