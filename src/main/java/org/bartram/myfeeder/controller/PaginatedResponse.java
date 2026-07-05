package org.bartram.myfeeder.controller;

import java.util.List;
import java.util.function.Function;

public record PaginatedResponse<T>(List<T> items, Long nextCursor) {

    /**
     * Builds a page from a list fetched with limit + 1 rows: the extra row, if present,
     * signals another page and is trimmed; nextCursor is the last returned item's id.
     */
    public static <T> PaginatedResponse<T> of(List<T> fetched, int limit, Function<T, Long> id) {
        boolean hasMore = fetched.size() > limit;
        List<T> items = hasMore ? fetched.subList(0, limit) : fetched;
        Long nextCursor = hasMore ? id.apply(items.getLast()) : null;
        return new PaginatedResponse<>(items, nextCursor);
    }
}
