package org.bartram.myfeeder.service;

/**
 * One topic's weights under the learned model, for the topic editor (FDBK-07). The editor prints these
 * server values and never recomputes them: {@code baseWeight} is the stored weight, {@code learned} the
 * capped learned adjustment before the clamp, {@code effectiveWeight} the sign-clamped weight within +/-50
 * that the ranking uses, and {@code limit} names the rule that held the value back. Read-only: nothing
 * here is stored.
 *
 * <p>{@code learned} combines votes and engagement ({@code learned = thumbsLearned + engagementLearned},
 * each capped, D-15). The two parts are appended after {@code limit} so existing clients keep working.
 *
 * <p>{@code engagementAtCap}, appended last, is true when the topic's engagement points reach the
 * engagement cap, from {@link LearnedLimit#engagementAtCap}, whichever limit is reported. It lets the
 * Interests line say "engaged … at max" even when a binding clamp or range is the reported limit (D-15).
 */
public record TopicLearned(long topicId, double baseWeight, double learned, double effectiveWeight,
                           LearnedLimit limit,
                           double thumbsLearned, double engagementLearned, boolean engagementAtCap) {}
