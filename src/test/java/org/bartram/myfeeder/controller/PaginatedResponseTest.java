package org.bartram.myfeeder.controller;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class PaginatedResponseTest {

    @Test
    void trimsExtraRowAndSetsCursorWhenMorePagesExist() {
        PaginatedResponse<Long> page = PaginatedResponse.of(List.of(10L, 20L, 30L), 2, Function.identity());
        assertThat(page.items()).containsExactly(10L, 20L);
        assertThat(page.nextCursor()).isEqualTo(20L);
    }

    @Test
    void returnsAllItemsAndNullCursorOnLastPage() {
        PaginatedResponse<Long> page = PaginatedResponse.of(List.of(10L), 2, Function.identity());
        assertThat(page.items()).containsExactly(10L);
        assertThat(page.nextCursor()).isNull();
    }
}
