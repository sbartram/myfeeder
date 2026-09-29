package org.bartram.myfeeder.repository;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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

    /** The select-list item every badge line carries. */
    private static final String BADGE_ITEM = ", " + InterestScoreQueries.INTEREST_SCORE + " AS interest_score";

    private static final Pattern SCORE_NAME = Pattern.compile("(?i)\\binterest_score\\b");

    /** A block-comment span, or an unclosed {@code /*} through the end of the line. */
    private static final Pattern COMMENT_SPAN = Pattern.compile("/\\*.*?(?:\\*/|$)");

    /** Opens a learned CTE, in any case or whitespace and anywhere in a line (deliberately unanchored). */
    private static final Pattern BLEND_START = Pattern.compile("(?i)\\bwith\\s+learned\\s+as\\b");

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
     * A one-token drift of the top badge fails whatever verbatim copy survives: in a comment on the same line,
     * in an appended section, or in code on the same line. Cases 1-6 keep 4 raw copies, so only the
     * per-statement check can catch them; case 7 adds a fifth copy with no drift.
     */
    @Test
    void aDriftedBadgeFailsDespiteAVerbatimDecoy() throws IOException {
        String sql = Files.readString(SQL);
        assertThatCode(() -> assertEveryCopyIsVerbatim(sql)).doesNotThrowAnyException();

        String verbatim = InterestScoreQueries.INTEREST_SCORE;
        String drifted = verbatim.replace("ROUND(b.raw_n)", "ROUND(b.raw_n, 1)");
        assertThat(drifted).isNotEqualTo(verbatim);

        Map<String, String> variants = new LinkedHashMap<>();
        variants.put("verbatim kept in a trailing -- comment",
                editTopBadgeLine(sql, line -> line.replace(verbatim, drifted) + " -- was " + verbatim));
        variants.put("verbatim kept in a block comment",
                editTopBadgeLine(sql, line -> line.replace(verbatim + " AS interest_score",
                        drifted + " /* was " + verbatim + " */ AS interest_score")));
        variants.put("verbatim badge used by an appended section",
                editTopBadgeLine(sql, line -> line.replace(verbatim, drifted))
                        + "\nSELECT 'extra' AS section" + BADGE_ITEM + " FROM blended b;\n");
        variants.put("verbatim kept as another column",
                editTopBadgeLine(sql, line -> line.replace(verbatim + " AS interest_score",
                        drifted + " AS interest_score, " + verbatim + " AS old_score")));
        variants.put("verbatim wrapped in an expression",
                editTopBadgeLine(sql, line -> line.replace(BADGE_ITEM, ", 100 - " + verbatim + " AS interest_score")));
        variants.put("a second interest_score column",
                editTopBadgeLine(sql, line -> line.replace(BADGE_ITEM, BADGE_ITEM + ", " + drifted + " interest_score")));
        String extraCopy = "an extra verbatim copy on a comment line";
        variants.put(extraCopy, sql + "\n-- " + verbatim + "\n");

        variants.forEach((name, variant) -> {
            if (name.equals(extraCopy)) {
                assertThat(occurrences(variant, verbatim)).as(name + " holds 5 raw copies").isEqualTo(5);
            } else {
                assertThat(occurrences(variant, verbatim)).as(name + " keeps 4 raw copies").isEqualTo(4);
            }
        });

        SoftAssertions.assertSoftly(softly -> variants.forEach((name, variant) -> softly
                .assertThatCode(() -> assertEveryCopyIsVerbatim(variant))
                .as(name)
                .isInstanceOf(AssertionError.class)));
    }

    /** An extra drifted blend statement fails whether it is indented, lower-case or opened mid-line (IN-07). */
    @Test
    void anExtraBlendStatementInAnyFormFails() throws IOException {
        String sql = Files.readString(SQL);
        assertThatCode(() -> assertEveryCopyIsVerbatim(sql)).doesNotThrowAnyException();

        String unread = InterestScoreQueries.blendCte(InterestScoreQueries.UNREAD_SCOPE);
        String drifted = driftOneByte(unread, unread, 0);

        Map<String, String> variants = new LinkedHashMap<>();
        variants.put("indented", sql + "\n  " + drifted + "\n");
        variants.put("lower-case", sql + "\nwith learned as" + drifted.substring("WITH learned AS".length()) + "\n");
        variants.put("opened mid-line", sql + "\nSELECT * FROM (" + drifted + " SELECT * FROM blended) t;\n");

        SoftAssertions.assertSoftly(softly -> variants.forEach((name, variant) -> softly
                .assertThatCode(() -> assertEveryCopyIsVerbatim(variant))
                .as(name)
                .isInstanceOf(AssertionError.class)));
    }

    /**
     * Checks, and only checks, the following:
     * <ol>
     * <li>The code of the file opens a learned CTE ({@code with learned as} in any case, with any whitespace,
     * anywhere in a line) exactly 5 times. Together with the next check this means no statement anywhere in
     * the code opens a learned CTE other than the five verbatim lines.</li>
     * <li>The lines that start with {@code WITH learned AS} are, in file order, the unread blend three times
     * (summary, top, bottom), the window blend, then the learned section. The blend lines are compared with
     * the Java text by equality, so text appended to a line also fails.</li>
     * <li>{@code INTEREST_SCORE} occurs exactly 4 times in the whole file, comments included, so a copy in any
     * comment form is a fifth copy and fails.</li>
     * <li>The code of the line after each unread or window blend line (the badge line of summary, top, bottom
     * and window-summary) holds {@code INTEREST_SCORE} once and the item
     * {@code , <INTEREST_SCORE> AS interest_score} once, and names {@code interest_score} once.</li>
     * <li>"Code" means the line as {@link #codeOf(String)} returns it: {@code /* *}{@code /} spans removed and
     * any {@code --} tail cut, per line.</li>
     * </ol>
     * Not checked: the rest of each statement (such as FILTERs, ORDER BY and other columns) and any statement
     * that opens no learned CTE. On failure only labels, counts and line numbers are shown, never an SQL line.
     */
    private static void assertEveryCopyIsVerbatim(String sql) {
        String unread = InterestScoreQueries.blendCte(InterestScoreQueries.UNREAD_SCOPE);
        String window = InterestScoreQueries.blendCte(WINDOW_SCOPE);
        String code = sql.lines().map(InterestCalibrationReplaySqlTest::codeOf).collect(Collectors.joining("\n"));
        assertThat(BLEND_START.matcher(code).results().count())
                .as("statements that open the learned CTE in the code, in any case, whitespace or position")
                .isEqualTo(5);

        List<String> labels = sql.lines()
                .filter(line -> line.startsWith("WITH learned AS"))
                .map(line -> line.equals(unread) ? "unread"
                        : line.equals(window) ? "window"
                        : line.startsWith(LEARNED_SECTION) ? "learned"
                        : "drifted")
                .toList();

        assertThat(labels).as("kind of each 'WITH learned AS' line, in file order")
                .containsExactly("unread", "unread", "unread", "window", "learned");
        assertThat(occurrences(sql, InterestScoreQueries.INTEREST_SCORE))
                .as("verbatim INTEREST_SCORE copies anywhere in the file, comments included")
                .isEqualTo(4);

        List<String> lines = sql.lines().toList();
        int badgeLines = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).equals(unread) && !lines.get(i).equals(window)) {
                continue;
            }
            String where = "the line after blend line " + (i + 1);
            String badgeLine = i + 1 < lines.size() ? codeOf(lines.get(i + 1)) : "";
            assertThat(occurrences(badgeLine, InterestScoreQueries.INTEREST_SCORE))
                    .as("INTEREST_SCORE copies in the code of " + where)
                    .isEqualTo(1);
            assertThat(occurrences(badgeLine, BADGE_ITEM))
                    .as("', <INTEREST_SCORE> AS interest_score' items in the code of " + where)
                    .isEqualTo(1);
            assertThat(SCORE_NAME.matcher(badgeLine).results().count())
                    .as("interest_score names in the code of " + where)
                    .isEqualTo(1);
            badgeLines++;
        }
        assertThat(badgeLines).as("badge lines checked").isEqualTo(4);
    }

    /**
     * The code of one line: every {@code /* *}{@code /} span is removed (an unclosed {@code /*} runs to the end
     * of the line), then the line is cut at the first {@code --}. It works per line, with no string-literal or
     * nesting awareness, so a block comment that spans lines counts as code. The four badge lines and five
     * blend lines hold no {@code --} or {@code /*} today, so it never cuts real SQL on them.
     */
    private static String codeOf(String line) {
        String code = COMMENT_SPAN.matcher(line).replaceAll(" ");
        int dashes = code.indexOf("--");
        return dashes < 0 ? code : code.substring(0, dashes);
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

    /** Returns {@code sql} with its one {@code SELECT 'top' AS section} line replaced by {@code edit}. In memory only. */
    private static String editTopBadgeLine(String sql, UnaryOperator<String> edit) {
        List<String> lines = new ArrayList<>(sql.lines().toList());
        List<Integer> top = IntStream.range(0, lines.size())
                .filter(i -> lines.get(i).startsWith("SELECT 'top' AS section"))
                .boxed()
                .toList();
        assertThat(top).as("top badge lines").hasSize(1);

        int at = top.get(0);
        String edited = edit.apply(lines.get(at));
        assertThat(edited).as("edit changed the top badge line").isNotEqualTo(lines.get(at));
        lines.set(at, edited);
        return String.join("\n", lines) + "\n";
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
