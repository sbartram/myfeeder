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
 * </ul>
 *
 * <p>Phase 4 appends the eligible-unscored and failed counts (JEV-05) as NEW components and never
 * renames these three.
 */
public record InterestStatus(boolean configured, String breakerState, boolean coldStart) {}
