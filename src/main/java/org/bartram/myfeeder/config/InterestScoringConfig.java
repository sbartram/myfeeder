package org.bartram.myfeeder.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * The dedicated executor that runs interest scoring off the polling thread (D-09).
 *
 * <p>The bean is registered as a non-default candidate ({@code defaultCandidate} off). A normal {@code Executor} bean
 * would make Boot's {@code @ConditionalOnMissingBean(Executor.class)} back off, silently removing
 * its {@code applicationTaskExecutor}. Consumers inject this one by name with
 * {@code @Qualifier(InterestScoringConfig.EXECUTOR)}.
 *
 * <p>Core and max pool size are both {@code myfeeder.interest.concurrency}. With a bounded queue,
 * a pool whose max exceeds its core grows new threads once the queue is full instead of rejecting.
 * The default abort rejection policy is kept on purpose: {@code ScoringQueue} catches the
 * {@code TaskRejectedException} and releases the article id so the sweep can retry it, whereas a
 * discarding policy would leave the id marked in flight forever.
 *
 * <p>No async annotation is used anywhere (D-09, research R3): work is handed to this executor
 * explicitly. Spring initializes the bean; callers that build it outside Spring must call
 * {@code initialize()}.
 */
@Configuration(proxyBeanMethods = false)
public class InterestScoringConfig {

    public static final String EXECUTOR = "interestScoringExecutor";

    @Bean(name = EXECUTOR, defaultCandidate = false)
    public ThreadPoolTaskExecutor interestScoringExecutor(MyfeederProperties properties) {
        MyfeederProperties.Interest interest = properties.getInterest();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(interest.getConcurrency());
        executor.setMaxPoolSize(interest.getConcurrency());
        executor.setQueueCapacity(interest.getQueueCapacity());
        executor.setThreadNamePrefix("jev-score-");
        return executor;
    }
}
