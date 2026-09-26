package org.bartram.myfeeder.controller;

import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.repository.InterestScoreQueries.PriorityRow;
import org.bartram.myfeeder.repository.InterestScoreQueries.SortKey;
import org.bartram.myfeeder.service.NotFoundException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class PriorityPageTest {

    private static final Instant DATE = Instant.parse("2026-09-25T12:34:56.123456Z");

    private static final List<SortKey> KEYS = List.of(
            new SortKey(82.2, DATE, 42),
            new SortKey(Double.NEGATIVE_INFINITY, DATE, 7),
            new SortKey(-30.0, Instant.parse("1969-12-31T23:59:59.999999Z"), 1),
            new SortKey(133.32, Instant.parse("2026-01-01T00:00:00Z"), Long.MAX_VALUE));

    @Test
    void cursorRoundTripsExactly() {
        for (SortKey key : KEYS) {
            assertThat(PriorityPage.decodeCursor(PriorityPage.encodeCursor(key))).as("round trip of %s", key)
                    .isEqualTo(key);
        }
    }

    @Test
    void cursorWireFormatIsPinned() {
        SortKey key = new SortKey(Double.NEGATIVE_INFINITY, Instant.parse("2025-09-25T11:33:20.123456Z"), 42);

        assertThat(PriorityPage.encodeCursor(key)).isEqualTo("LUluZmluaXR5fDE3NTg4MDAwMDAxMjM0NTZ8NDI");
        assertThat(PriorityPage.decodeCursor("LUluZmluaXR5fDE3NTg4MDAwMDAxMjM0NTZ8NDI")).isEqualTo(key);
    }

    @Test
    void cursorIsUrlSafe() {
        for (SortKey key : KEYS) {
            assertThat(PriorityPage.encodeCursor(key)).matches("^[A-Za-z0-9_-]+$");
        }
    }

    @Test
    void unreadableCursorIsNotFound() {
        List<String> inputs = List.of("not-a-cursor", "12345", "", "%%%",
                b64("1|2"), b64("NaN|0|1"), b64("x|0|1"), b64("1|0|y"), b64("1|0|1|2"),
                b64("1|" + Long.MIN_VALUE + "|1"), b64("1|" + Long.MAX_VALUE + "|1"));
        for (String input : inputs) {
            NotFoundException e = catchThrowableOfType(NotFoundException.class, () -> PriorityPage.decodeCursor(input));
            assertThat(e).as("cursor '%s'", input).isNotNull();
            assertThat(e.getMessage()).isEqualTo(PriorityPage.UNREADABLE_CURSOR);
            if (!input.isEmpty()) {
                assertThat(e.getMessage()).doesNotContain(input);
            }
        }
    }

    @Test
    void cursorDateOneMicrosecondOutsideTheBoundsIsNotFound() {
        List<SortKey> outside = List.of(
                new SortKey(1.0, Instant.parse("0001-01-01T00:00:00Z").minus(1, ChronoUnit.MICROS), 1),
                new SortKey(1.0, Instant.parse("9999-12-31T23:59:59.999999Z").plus(1, ChronoUnit.MICROS), 1));
        for (SortKey key : outside) {
            String cursor = PriorityPage.encodeCursor(key);
            NotFoundException e = catchThrowableOfType(NotFoundException.class, () -> PriorityPage.decodeCursor(cursor));
            assertThat(e).as("cursor dated %s", key.date()).isNotNull();
            assertThat(e.getMessage()).isEqualTo(PriorityPage.UNREADABLE_CURSOR);
        }
    }

    @Test
    void cursorDateBoundsAreInclusive() {
        List<SortKey> edges = List.of(
                new SortKey(1.0, Instant.parse("0001-01-01T00:00:00Z"), 1),
                new SortKey(1.0, Instant.parse("9999-12-31T23:59:59.999999Z"), 1));
        for (SortKey key : edges) {
            assertThat(PriorityPage.decodeCursor(PriorityPage.encodeCursor(key))).as("round trip of %s", key)
                    .isEqualTo(key);
        }
    }

    @Test
    void ofTrimsTheLookAheadRowAndEncodesTheLastKeptKey() {
        PriorityRow r1 = row(1, 90.0);
        PriorityRow r2 = row(2, 80.0);
        PriorityRow r3 = row(3, 70.0);

        PriorityPage more = PriorityPage.of(List.of(r1, r2, r3), 2);
        assertThat(more.items()).containsExactly(r1.article(), r2.article());
        assertThat(more.nextCursor()).isEqualTo(PriorityPage.encodeCursor(r2.key()));

        PriorityPage last = PriorityPage.of(List.of(r1, r2), 2);
        assertThat(last.items()).containsExactly(r1.article(), r2.article());
        assertThat(last.nextCursor()).isNull();

        PriorityPage empty = PriorityPage.of(List.of(), 2);
        assertThat(empty.items()).isEmpty();
        assertThat(empty.nextCursor()).isNull();
    }

    private static PriorityRow row(long id, double score) {
        Article article = new Article();
        article.setId(id);
        return new PriorityRow(article, new SortKey(score, DATE, id));
    }

    private static String b64(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
}
