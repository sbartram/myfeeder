package org.bartram.myfeeder.service;

import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.integration.JevApiClient;
import org.bartram.myfeeder.repository.ArticleScoreStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterestRescoreServiceTest {

    private static final String REFUSAL = "Re-score needs a TypeSafe API key and a profile or at least one topic";

    @Mock private JevApiClient jevApiClient;
    @Mock private InterestService interestService;
    @Mock private ArticleScoreStore store;

    private InterestRescoreService service;

    @BeforeEach
    void setUp() {
        service = new InterestRescoreService(jevApiClient, interestService, store, new MyfeederProperties());
        lenient().when(jevApiClient.isConfigured()).thenReturn(true);
        lenient().when(interestService.isColdStart()).thenReturn(false);
    }

    @Test
    void countUsesTheRescoreScope() {
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        when(store.countRescoreScope(cutoff.capture())).thenReturn(312L);

        assertThat(service.count()).isEqualTo(new RescoreCount(312, 14));
        Instant expected = Instant.now().minus(Duration.ofDays(14));
        assertThat(cutoff.getValue()).isCloseTo(expected, within(Duration.ofSeconds(5)));
    }

    @Test
    void rescoreDeletesTheScope() {
        when(store.deleteRescoreScope(any())).thenReturn(312);

        assertThat(service.rescore()).isEqualTo(new RescoreCount(312, 14));
    }

    @Test
    void rescoreRefusesWhenNotConfigured() {
        when(jevApiClient.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> service.rescore())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(REFUSAL);
        verify(store, never()).deleteRescoreScope(any());
    }

    @Test
    void rescoreRefusesInColdStart() {
        when(interestService.isColdStart()).thenReturn(true);

        assertThatThrownBy(() -> service.rescore())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(REFUSAL);
        verify(store, never()).deleteRescoreScope(any());
    }

    @Test
    void countIsNotGuarded() {
        lenient().when(jevApiClient.isConfigured()).thenReturn(false);
        lenient().when(interestService.isColdStart()).thenReturn(true);
        when(store.countRescoreScope(any())).thenReturn(7L);

        assertThat(service.count().count()).isEqualTo(7);
    }
}
