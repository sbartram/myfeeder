package org.bartram.myfeeder.service;

/**
 * A Re-score count (INT-05). For {@code GET /api/interest/rescore} it is the number of score rows
 * Re-score would reset; for {@code POST /api/interest/rescore} it is the number of rows it reset.
 * {@code windowDays} is the eligibility window the confirmation copy names ("from the last N days").
 */
public record RescoreCount(long count, int windowDays) {
}
