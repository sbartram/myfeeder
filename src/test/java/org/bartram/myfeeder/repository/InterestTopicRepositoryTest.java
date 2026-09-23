package org.bartram.myfeeder.repository;

import org.bartram.myfeeder.TestcontainersConfiguration;
import org.bartram.myfeeder.model.InterestTopic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.NestedExceptionUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@DataJdbcTest
@Import(TestcontainersConfiguration.class)
class InterestTopicRepositoryTest {

    @Autowired
    private InterestTopicRepository topicRepository;

    @Test
    void saveAssignsIdAndRoundTrips() {
        InterestTopic saved = topicRepository.save(topic("Rust", "Articles about the Rust language", 30));

        assertThat(saved.getId()).isNotNull();
        InterestTopic loaded = topicRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getName()).isEqualTo("Rust");
        assertThat(loaded.getDescription()).isEqualTo("Articles about the Rust language");
        assertThat(loaded.getWeight()).isEqualTo(30);
        assertThat(loaded.getVersion()).isEqualTo(1);
    }

    @Test
    void findAllOrderedReturnsTopicsByIdAscending() {
        topicRepository.save(topic("Rust", "Rust", 20));
        topicRepository.save(topic("Postgres", "Postgres", 10));
        topicRepository.save(topic("Crypto", "Cryptocurrency", -40));

        List<InterestTopic> topics = topicRepository.findAllOrdered();

        assertThat(topics).hasSize(3);
        assertThat(topics).extracting(InterestTopic::getId).isSorted();
        assertThat(topics).extracting(InterestTopic::getName).containsExactly("Rust", "Postgres", "Crypto");
    }

    @Test
    void weightOnlySaveLeavesVersionUnchanged() {
        InterestTopic saved = topicRepository.save(topic("Rust", "Rust", 20));

        saved.setWeight(-15);
        topicRepository.save(saved);

        InterestTopic reloaded = topicRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getVersion()).isEqualTo(1);
        assertThat(reloaded.getWeight()).isEqualTo(-15);
    }

    @Test
    void saveRejectsWeightOutsideCheck() {
        Throwable thrown = catchThrowable(() -> topicRepository.save(topic("Too heavy", "Too heavy", 51)));

        assertThat(thrown).isNotNull();
        assertThat(NestedExceptionUtils.getMostSpecificCause(thrown).getMessage())
                .contains("interest_topic_weight_check");
    }

    private static InterestTopic topic(String name, String description, int weight) {
        Instant now = Instant.now();
        InterestTopic t = new InterestTopic();
        t.setName(name);
        t.setDescription(description);
        t.setWeight(weight);
        t.setVersion(1);
        t.setCreatedAt(now);
        t.setUpdatedAt(now);
        return t;
    }
}
