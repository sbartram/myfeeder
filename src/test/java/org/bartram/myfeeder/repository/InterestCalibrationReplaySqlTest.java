package org.bartram.myfeeder.repository;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Drift guard for the calibration replay (Phase 7, D-09): the replay SQL must hold the app's blend and
 * badge text byte for byte, so identical constants give the badge the running app serves. Every blend
 * line and every badge copy must match, not just one. It must also stay read-only. Plain JUnit, no
 * Spring and no Docker; paths are relative to the project root.
 */
class InterestCalibrationReplaySqlTest {

    private static final Path SQL = Path.of("scripts/interest-calibration-replay.sql");
    private static final Path DRIVER = Path.of("scripts/interest-calibration-replay.sh");

    /** The window cross-check scope (Pitfall 9): every SCORED article inside the window, read or unread. */
    private static final String WINDOW_SCOPE =
            "COALESCE(a.published_at, a.fetched_at) > now() - :windowDays * interval '1 day'";

    /** The learned section's opening text, as {@code replaysTheLearnedModelVerbatim} builds it. */
    private static final String LEARNED_SECTION = InterestScoreQueries.LEARNED_CTE + " SELECT 'learned' AS section";

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

    @Test
    void everyCopyInTheReplayIsVerbatim() throws IOException {
        assertEveryCopyIsVerbatim(Files.readString(SQL));
    }

    @Test
    void driftInAnySingleBlendCopyFails() throws IOException {
        String sql = Files.readString(SQL);
        assertThatCode(() -> assertEveryCopyIsVerbatim(sql)).doesNotThrowAnyException();

        record Copy(String label, String needle, int expected) {}
        List<Copy> copies = List.of(
                new Copy("unread", InterestScoreQueries.blendCte(InterestScoreQueries.UNREAD_SCOPE), 3),
                new Copy("window", InterestScoreQueries.blendCte(WINDOW_SCOPE), 1),
                new Copy("learned", LEARNED_SECTION, 1));
        for (Copy copy : copies) {
            assertThat(occurrences(sql, copy.needle())).as(copy.label() + " copies").isEqualTo(copy.expected());
            for (int i = 0; i < copy.expected(); i++) {
                String drifted = driftOneByte(sql, copy.needle(), i);
                assertThatCode(() -> assertEveryCopyIsVerbatim(drifted))
                        .as(copy.label() + " copy " + i)
                        .isInstanceOf(AssertionError.class);
            }
        }
    }

    @Test
    void driftInAnySingleBadgeCopyFails() throws IOException {
        String sql = Files.readString(SQL);
        assertThatCode(() -> assertEveryCopyIsVerbatim(sql)).doesNotThrowAnyException();

        assertThat(occurrences(sql, InterestScoreQueries.INTEREST_SCORE)).as("badge copies").isEqualTo(4);
        for (int i = 0; i < 4; i++) {
            String drifted = driftOneByte(sql, InterestScoreQueries.INTEREST_SCORE, i);
            assertThatCode(() -> assertEveryCopyIsVerbatim(drifted))
                    .as("badge copy " + i)
                    .isInstanceOf(AssertionError.class);
        }
    }

    /** One step either side of the shipped counts (5 blend lines, 4 badge copies) fails. */
    @Test
    void aMissingOrExtraCopyFails() throws IOException {
        String sql = Files.readString(SQL);
        assertThatCode(() -> assertEveryCopyIsVerbatim(sql)).doesNotThrowAnyException();

        String unread = InterestScoreQueries.blendCte(InterestScoreQueries.UNREAD_SCOPE);
        String badge = InterestScoreQueries.INTEREST_SCORE;
        StringBuilder withoutBottom = new StringBuilder();
        int unreadSeen = 0;
        for (String line : sql.lines().toList()) {
            if (line.equals(unread) && ++unreadSeen == 3) {
                continue;
            }
            withoutBottom.append(line).append('\n');
        }
        int lastBadge = sql.lastIndexOf(badge);

        Map<String, String> variants = new LinkedHashMap<>();
        variants.put("bottom blend line removed", withoutBottom.toString());
        variants.put("extra unread blend line", sql + "\n" + unread + "\n");
        variants.put("last badge copy replaced",
                sql.substring(0, lastBadge) + "NULL" + sql.substring(lastBadge + badge.length()));
        variants.put("extra badge copy", sql + "\n" + "SELECT " + badge + ";\n");

        variants.forEach((name, variant) -> assertThatCode(() -> assertEveryCopyIsVerbatim(variant))
                .as(name)
                .isInstanceOf(AssertionError.class));
    }

    /**
     * Every statement line that starts with {@code WITH learned AS} must be one of the allowed Java texts, in
     * file order: the unread blend three times (summary, top, bottom), the window blend, then the learned
     * section. The raw line is tested, so the {@code --} header comment that mentions the phrase is never
     * counted. The blend kinds use equality, so text appended to a line also fails. The badge expression
     * must occur exactly 4 times outside comments, so a copy pasted into a comment cannot hide a drifted
     * statement copy. On failure only labels and counts are shown, never a 1.6k-character SQL line.
     */
    private static void assertEveryCopyIsVerbatim(String sql) {
        String unread = InterestScoreQueries.blendCte(InterestScoreQueries.UNREAD_SCOPE);
        String window = InterestScoreQueries.blendCte(WINDOW_SCOPE);
        List<String> labels = sql.lines()
                .filter(line -> line.startsWith("WITH learned AS"))
                .map(line -> line.equals(unread) ? "unread"
                        : line.equals(window) ? "window"
                        : line.startsWith(LEARNED_SECTION) ? "learned"
                        : "drifted")
                .toList();

        assertThat(labels).as("kind of each 'WITH learned AS' line, in file order")
                .containsExactly("unread", "unread", "unread", "window", "learned");
        assertThat(occurrences(withoutComments(sql), InterestScoreQueries.INTEREST_SCORE))
                .as("verbatim INTEREST_SCORE copies outside comments")
                .isEqualTo(4);
    }

    /** The statement text: drops {@code --} lines, the same filter as {@code replayIsReadOnly}. */
    private static String withoutComments(String sql) {
        return sql.lines()
                .filter(line -> !line.strip().startsWith("--"))
                .collect(Collectors.joining("\n"));
    }

    /** Non-overlapping count of {@code needle} in {@code haystack}. */
    private static int occurrences(String haystack, String needle) {
        int count = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            count++;
        }
        return count;
    }

    /**
     * Returns {@code text} with one character changed in the middle of the 0-based {@code occurrence}-th
     * match of {@code needle}. In memory only.
     */
    private static String driftOneByte(String text, String needle, int occurrence) {
        int start = text.indexOf(needle);
        for (int n = 0; n < occurrence && start >= 0; n++) {
            start = text.indexOf(needle, start + needle.length());
        }
        if (start < 0) {
            throw new IllegalArgumentException("no occurrence " + occurrence);
        }
        int at = start + needle.length() / 2;
        char replacement = text.charAt(at) == '#' ? '%' : '#';
        return text.substring(0, at) + replacement + text.substring(at + 1);
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
