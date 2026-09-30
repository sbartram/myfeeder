package org.bartram.myfeeder.service;

/**
 * One topic's weights under the learned model, for the topic editor (FDBK-07). The editor prints these
 * server values and never recomputes them: {@code baseWeight} is the stored weight, {@code learned} the
 * capped learned adjustment before the clamp, {@code effectiveWeight} the sign-clamped weight within +/-50
 * that the ranking uses, and {@code limit} names the rule that held the value back. Read-only: nothing
 * here is stored.
 *
 * <p>{@code learned} combines votes and engagement ({@code learned = thumbsLearned + engagementLearned},
 * each capped, D-15). The two parts are appended after {@code limit} so existing clients keep working and
 * Phase 10 can split the editor line (D-10).
 */
public record TopicLearned(long topicId, double baseWeight, double learned, double effectiveWeight,
                           LearnedLimit limit, double thumbsLearned, double engagementLearned) {}
