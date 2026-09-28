package org.bartram.myfeeder.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drift guard for the calibration replay (Phase 7, D-09): the replay SQL must hold the app's blend and
 * badge text byte for byte, so identical constants give the badge the running app serves. It must also
 * stay read-only. Plain JUnit, no Spring and no Docker; paths are relative to the project root.
 */
class InterestCalibrationReplaySqlTest {

    private static final Path SQL = Path.of("scripts/interest-calibration-replay.sql");
    private static final Path DRIVER = Path.of("scripts/interest-calibration-replay.sh");

    /** The window cross-check scope (Pitfall 9): every SCORED article inside the window, read or unread. */
    private static final String WINDOW_SCOPE =
            "COALESCE(a.published_at, a.fetched_at) > now() - :windowDays * interval '1 day'";

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

    @Test
    void replaysTheWindowCrossCheckVerbatim() throws IOException {
        assertThat(Files.readString(SQL)).contains(InterestScoreQueries.blendCte(WINDOW_SCOPE));
    }

    @Test
    void replaysTheLearnedModelVerbatim() throws IOException {
        assertThat(Files.readString(SQL)).contains(InterestScoreQueries.LEARNED_CTE + " SELECT 'learned' AS section");
    }

    @Test
    void driverRejectsANonNumericCandidate() throws Exception {
        DriverRun run = runDriver(Map.of("MYFEEDER_PG_PASSWORD", "x"), "100:70:4x");

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("invalid candidate: 100:70:4x");
    }

    @Test
    void driverRequiresThePassword() throws Exception {
        DriverRun run = runDriver(Map.of(), "100:70:40");

        assertThat(run.exitCode()).isEqualTo(2);
        assertThat(run.stderr()).contains("MYFEEDER_PG_PASSWORD is required");
    }

    private record DriverRun(int exitCode, String stderr) {}

    /**
     * Runs the driver against a dead local port, so a validation bug could never reach a real database.
     * The password is removed unless {@code env} supplies it.
     */
    private static DriverRun runDriver(Map<String, String> env, String... candidates) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(
                Stream.concat(Stream.of("bash", DRIVER.toString()), Stream.of(candidates)).toList());
        Map<String, String> environment = builder.environment();
        environment.remove("MYFEEDER_PG_PASSWORD");
        environment.put("PGHOST", "127.0.0.1");
        environment.put("PGPORT", "1");
        environment.putAll(env);
        builder.redirectErrorStream(false);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        Process process = builder.start();
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
        return new DriverRun(process.exitValue(), stderr);
    }
}
