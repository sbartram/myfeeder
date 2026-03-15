package org.bartram.myfeeder.service;

import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.repository.ArticleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetentionServiceTest {

    @Mock private ArticleRepository articleRepository;
    @Mock private MyfeederProperties properties;

    @InjectMocks
    private RetentionService retentionService;

    @Test
    void shouldClearContentOlderThanConfiguredDays() {
        var retention = new MyfeederProperties.Retention();
        retention.setFullContentDays(30);
        when(properties.getRetention()).thenReturn(retention);

        Instant before = Instant.now().minus(30, ChronoUnit.DAYS);
        retentionService.cleanupOldContent();
        Instant after = Instant.now().minus(30, ChronoUnit.DAYS);

        var captor = ArgumentCaptor.forClass(Instant.class);
        verify(articleRepository).clearContentOlderThan(captor.capture());
        assertThat(captor.getValue()).isBetween(before, after.plusSeconds(1));
    }
}
