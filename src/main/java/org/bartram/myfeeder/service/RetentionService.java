package org.bartram.myfeeder.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.repository.ArticleRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetentionService {

    private final ArticleRepository articleRepository;
    private final MyfeederProperties properties;

    @Scheduled(cron = "${myfeeder.retention.cleanup-cron}")
    public void cleanupOldContent() {
        int days = properties.getRetention().getFullContentDays();
        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
        articleRepository.clearContentOlderThan(cutoff);
        log.info("Retention cleanup: cleared content older than {} days", days);
    }
}
