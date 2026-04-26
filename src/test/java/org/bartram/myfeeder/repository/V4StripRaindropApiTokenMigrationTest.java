package org.bartram.myfeeder.repository;

import org.bartram.myfeeder.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJdbcTest
@Import(TestcontainersConfiguration.class)
class V4StripRaindropApiTokenMigrationTest {

    @Autowired private JdbcTemplate jdbc;

    @Test
    void migrationLogicScrubsApiTokenWhenAppliedToLegacyRow() {
        // Simulate a row that survived V3 (legacy shape with apiToken).
        jdbc.update(
                "INSERT INTO integration_config (type, config, enabled) VALUES (?, ?::text, ?) "
                        + "ON CONFLICT (type) DO UPDATE SET config = EXCLUDED.config",
                "RAINDROP",
                "{\"apiToken\":\"leaked-token\",\"collectionId\":12345}",
                true);

        // Re-run the V4 migration's UPDATE manually against the inserted row.
        jdbc.update(
                "UPDATE integration_config "
                        + "SET config = jsonb_build_object('collectionId', (config::jsonb->>'collectionId')::bigint)::text "
                        + "WHERE type = 'RAINDROP' AND config IS NOT NULL AND config::jsonb ? 'apiToken'");

        String config = jdbc.queryForObject(
                "SELECT config FROM integration_config WHERE type = 'RAINDROP'",
                String.class);

        assertThat(config).doesNotContain("apiToken");
        assertThat(config).contains("\"collectionId\"");
        assertThat(config).contains("12345");
    }
}
