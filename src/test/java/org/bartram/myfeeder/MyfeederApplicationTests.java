package org.bartram.myfeeder;

import org.bartram.myfeeder.config.InterestScoringConfig;
import org.bartram.myfeeder.controller.FeedController;
import org.bartram.myfeeder.controller.ArticleController;
import org.bartram.myfeeder.controller.IntegrationConfigController;
import org.bartram.myfeeder.scheduler.FeedPollingScheduler;
import org.bartram.myfeeder.service.RetentionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MyfeederApplicationTests {

    @Autowired private FeedController feedController;
    @Autowired private ArticleController articleController;
    @Autowired private IntegrationConfigController integrationConfigController;
    @Autowired private FeedPollingScheduler feedPollingScheduler;
    @Autowired private RetentionService retentionService;
    @Autowired private ApplicationContext ctx;

    @Test
    void contextLoads() {
        assertThat(feedController).isNotNull();
        assertThat(articleController).isNotNull();
        assertThat(integrationConfigController).isNotNull();
        assertThat(feedPollingScheduler).isNotNull();
        assertThat(retentionService).isNotNull();

        // defaultCandidate = false on the scoring executor must leave Boot's own executor in place
        assertThat(ctx.containsBean("applicationTaskExecutor")).isTrue();
        ThreadPoolTaskExecutor scoring = ctx.getBean(InterestScoringConfig.EXECUTOR, ThreadPoolTaskExecutor.class);
        assertThat(scoring.getCorePoolSize()).isEqualTo(1);
        assertThat(scoring.getMaxPoolSize()).isEqualTo(1);
        assertThat(scoring.getQueueCapacity()).isEqualTo(1000);
        assertThat(scoring.getThreadNamePrefix()).isEqualTo("jev-score-");
    }
}
