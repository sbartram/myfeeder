package org.bartram.myfeeder.repository;

import org.bartram.myfeeder.TestcontainersConfiguration;
import org.bartram.myfeeder.model.IntegrationConfig;
import org.bartram.myfeeder.model.IntegrationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJdbcTest
@Import(TestcontainersConfiguration.class)
class IntegrationConfigRepositoryTest {

    @Autowired
    private IntegrationConfigRepository repository;

    @Test
    void shouldFindByType() {
        var config = new IntegrationConfig();
        config.setType(IntegrationType.RAINDROP);
        config.setConfig("{\"apiToken\":\"test\",\"collectionId\":12345}");
        config.setEnabled(true);
        repository.save(config);

        var found = repository.findByType(IntegrationType.RAINDROP);
        assertThat(found).isPresent();
        assertThat(found.get().getConfig()).contains("test");
    }
}
