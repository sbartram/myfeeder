package org.bartram.myfeeder;

import org.bartram.myfeeder.controller.FeedController;
import org.bartram.myfeeder.controller.ArticleController;
import org.bartram.myfeeder.controller.IntegrationConfigController;
import org.bartram.myfeeder.scheduler.FeedPollingScheduler;
import org.bartram.myfeeder.service.RetentionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MyfeederApplicationTests {

    @Autowired private FeedController feedController;
    @Autowired private ArticleController articleController;
    @Autowired private IntegrationConfigController integrationConfigController;
    @Autowired private FeedPollingScheduler feedPollingScheduler;
    @Autowired private RetentionService retentionService;

    @Test
    void contextLoads() {
        assertThat(feedController).isNotNull();
        assertThat(articleController).isNotNull();
        assertThat(integrationConfigController).isNotNull();
        assertThat(feedPollingScheduler).isNotNull();
        assertThat(retentionService).isNotNull();
    }
}
