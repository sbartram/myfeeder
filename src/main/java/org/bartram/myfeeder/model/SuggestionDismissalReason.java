package org.bartram.myfeeder.model;

/**
 * Why a suggested topic was handled. The names equal the V7 {@code topic_suggestion_dismissal.reason} CHECK
 * values. DISMISSED comes from Dismiss (D-17); TOPIC_CREATED from a topic saved with a sourceArticleId
 * (D-13). Both hide the suggestion permanently (Phase 8 D-14).
 */
public enum SuggestionDismissalReason {
    DISMISSED, TOPIC_CREATED
}
