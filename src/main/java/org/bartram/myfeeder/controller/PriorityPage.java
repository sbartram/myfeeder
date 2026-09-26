package org.bartram.myfeeder.controller;

import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.repository.InterestScoreQueries.PriorityRow;
import org.bartram.myfeeder.repository.InterestScoreQueries.SortKey;
import org.bartram.myfeeder.service.NotFoundException;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;

/**
 * One Priority page. Same {@code {items, nextCursor}} JSON shape as {@link PaginatedResponse}, but the
 * cursor is an opaque string holding the last row's served sort tuple {@code (score, date, id)}, so the
 * next page compares against the values the client saw rather than the cursor article's live score
 * (G-05-7). {@link PaginatedResponse} stays unchanged for the Long-cursor endpoints.
 *
 * <p>Wire format: base64url without padding of the UTF-8 text {@code <score>|<epochMicros>|<id>}, where
 * the score is {@link Double#toString(double)} ({@code -Infinity} for unscored rows).
 */
public record PriorityPage(List<Article> items, String nextCursor) {

    static final String UNREADABLE_CURSOR = "Priority cursor not recognized";

    /**
     * Builds a page from rows fetched with limit + 1: the extra row, if present, signals another page and
     * is trimmed; nextCursor encodes the last kept row's served key.
     */
    public static PriorityPage of(List<PriorityRow> fetched, int limit) {
        boolean hasMore = fetched.size() > limit;
        List<PriorityRow> kept = hasMore ? fetched.subList(0, limit) : fetched;
        String nextCursor = hasMore ? encodeCursor(kept.getLast().key()) : null;
        return new PriorityPage(kept.stream().map(PriorityRow::article).toList(), nextCursor);
    }

    /** Encodes a served sort key losslessly: shortest exact double, epoch microseconds, id. */
    public static String encodeCursor(SortKey key) {
        Instant date = key.date();
        long micros = Math.addExact(Math.multiplyExact(date.getEpochSecond(), 1_000_000L), date.getNano() / 1_000);
        String text = Double.toString(key.score()) + "|" + micros + "|" + key.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The exact reverse of {@link #encodeCursor(SortKey)}. Anything it cannot read is a 404 with fixed
     * text that never echoes the input.
     *
     * <p>404 rather than 400 because 404 is the existing "restart from page 1" signal that
     * {@code PriorityList} handles (R4): a tab loaded before this format still sends a numeric id, and it
     * should restart instead of failing "Load more" forever.
     */
    public static SortKey decodeCursor(String cursor) {
        try {
            String text = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = text.split("\\|", -1);
            if (parts.length != 3) {
                throw new NotFoundException(UNREADABLE_CURSOR);
            }
            double score = Double.parseDouble(parts[0]);
            if (Double.isNaN(score)) {
                throw new NotFoundException(UNREADABLE_CURSOR);
            }
            long micros = Long.parseLong(parts[1]);
            long id = Long.parseLong(parts[2]);
            return new SortKey(score, Instant.EPOCH.plus(micros, ChronoUnit.MICROS), id);
        } catch (IllegalArgumentException | ArithmeticException | DateTimeException e) {
            throw new NotFoundException(UNREADABLE_CURSOR);
        }
    }
}
