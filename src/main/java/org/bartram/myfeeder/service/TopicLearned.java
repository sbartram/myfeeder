package org.bartram.myfeeder.service;

/**
 * One topic's weights under the learned model, for the topic editor (FDBK-07). The editor prints these
 * server values and never recomputes them: {@code baseWeight} is the stored weight, {@code learned} the
 * capped learned adjustment, {@code effectiveWeight} the sign-clamped weight within +/-50 that the ranking
 * uses, and {@code limit} names the rule that held the value back. Read-only: nothing here is stored.
 */
public record TopicLearned(long topicId, double baseWeight, double learned, double effectiveWeight,
                           LearnedLimit limit) {}
