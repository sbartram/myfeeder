package org.bartram.myfeeder.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drift guard for the calibration replay (Phase 7, D-09): the replay SQL must hold the app's blend and
 * badge text byte for byte, so identical constants give the badge the running app serves. It must also
 * stay read-only. Plain JUnit, no Spring and no Docker; paths are relative to the project root.
 */
class InterestCalibrationReplaySqlTest {

    private static final Path SQL = Path.of("scripts/interest-calibration-replay.sql");
    private static final Path DRIVER = Path.of("scripts/interest-calibration-replay.sh");

    private static final Pattern WRITE_KEYWORD = Pattern.compile(
            "(?i)\\b(insert|update|delete|create|drop|alter|truncate|grant|copy|into)\\b");

    @Test
    void replaysTheAppsUnreadBlendVerbatim() throws IOException {
        String sql = Files.readString(SQL);

        assertThat(sql).contains(InterestScoreQueries.blendCte(InterestScoreQueries.UNREAD_SCOPE));
        assertThat(sql).contains(InterestScoreQueries.INTEREST_SCORE);
    }

    @Test
    void replayIsReadOnly() throws IOException {
        String statements = Files.readString(SQL).lines()
                .filter(line -> !line.strip().startsWith("--"))
                .collect(Collectors.joining("\n"));

        assertThat(WRITE_KEYWORD.matcher(statements).find()).isFalse();
        assertThat(statements.lines()).noneMatch(line -> line.startsWith("\\"));

        String driver = Files.readString(DRIVER);
        assertThat(driver).contains("PGOPTIONS='-c default_transaction_read_only=on'");
        assertThat(driver).doesNotContain("set -x");
    }
}
