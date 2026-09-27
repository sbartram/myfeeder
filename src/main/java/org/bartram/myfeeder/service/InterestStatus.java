package org.bartram.myfeeder.service;

/**
 * The interest status contract (D-04) read by the interest editor, the Phase 4 counts and the
 * Phase 5 Priority view.
 *
 * <ul>
 *   <li>{@code configured}: a TypeSafe key is set ({@code JevApiClient.isConfigured()}).</li>
 *   <li>{@code breakerState}: the "jev" circuit breaker state name, passed through verbatim
 *       (CLOSED, OPEN, HALF_OPEN, FORCED_OPEN, ...). A point-in-time read.</li>
 *   <li>{@code coldStart}: {@code InterestService.isColdStart()} (D-05).</li>
 *   <li>{@code eligibleUnscored}: eligible articles (unread, inside the window) with no score row
 *       or a FAILED row still under 3 attempts (D-11, D-12).</li>
 *   <li>{@code failed}: eligible articles whose FAILED row used all 3 attempts (D-11).</li>
 *   <li>{@code tiers}: the badge tier thresholds from {@code myfeeder.interest.blend.tiers} (D-13, appended in Phase 7).</li>
 * </ul>
 *
 * <p>Both counts come from one query, so they are consistent with each other. Later fields are
 * appended; existing components are never renamed.
 */
public record InterestStatus(boolean configured, String breakerState, boolean coldStart, long eligibleUnscored,
                             long failed, TierThresholds tiers) {}
