package org.bartram.myfeeder;

import org.bartram.myfeeder.config.InterestScoringConfig;
import org.bartram.myfeeder.controller.FeedController;
import org.bartram.myfeeder.controller.ArticleController;
import org.bartram.myfeeder.controller.IntegrationConfigController;
import org.bartram.myfeeder.scheduler.FeedPollingScheduler;
import org.bartram.myfeeder.scheduler.InterestScoringSweep;
import org.bartram.myfeeder.service.RetentionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.config.Task;
import org.springframework.scheduling.support.ScheduledMethodRunnable;

import java.time.Duration;
import java.util.List;

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

    @Test
    void sweepIsScheduledWithConfiguredDelays() {
        assertThat(ctx.getBeansOfType(InterestScoringSweep.class)).hasSize(1);

        List<Task> sweepTasks = ctx.getBeansOfType(ScheduledTaskHolder.class).values().stream()
                .flatMap(holder -> holder.getScheduledTasks().stream())
                .map(ScheduledTask::getTask)
                .filter(MyfeederApplicationTests::isSweep)
                .toList();

        assertThat(sweepTasks).hasSize(1);
        assertThat(sweepTasks.getFirst()).isInstanceOf(FixedDelayTask.class);
        FixedDelayTask task = (FixedDelayTask) sweepTasks.getFirst();
        assertThat(task.getIntervalDuration()).isEqualTo(Duration.ofMinutes(2));
        // the test YAML's PT1H keeps the suite's contexts from sweeping
        assertThat(task.getInitialDelayDuration()).isEqualTo(Duration.ofHours(1));
    }

    private static boolean isSweep(Task task) {
        if (task.getRunnable() instanceof ScheduledMethodRunnable smr) {
            return smr.getMethod().getDeclaringClass() == InterestScoringSweep.class
                    && smr.getMethod().getName().equals("sweep");
        }
        // Spring may wrap the method runnable; its toString names the target method
        return task.toString().contains("InterestScoringSweep.sweep");
    }
}
