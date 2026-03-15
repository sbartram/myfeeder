package org.bartram.myfeeder.repository;

import org.bartram.myfeeder.TestcontainersConfiguration;
import org.bartram.myfeeder.model.Feed;
import org.bartram.myfeeder.model.FeedType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJdbcTest
@Import(TestcontainersConfiguration.class)
class FeedRepositoryTest {

    @Autowired
    private FeedRepository feedRepository;

    @Test
    void shouldSaveAndRetrieveFeed() {
        var feed = new Feed();
        feed.setUrl("https://example.com/feed.xml");
        feed.setTitle("Example Feed");
        feed.setFeedType(FeedType.RSS);
        feed.setCreatedAt(Instant.now());

        var saved = feedRepository.save(feed);

        assertThat(saved.getId()).isNotNull();
        var found = feedRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Example Feed");
    }
}
