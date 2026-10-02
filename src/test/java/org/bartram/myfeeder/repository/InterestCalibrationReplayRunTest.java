package org.bartram.myfeeder.repository;

import org.bartram.myfeeder.TestcontainersConfiguration;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs the calibration replay end to end (Phase 12, CAL-02 / D-13): the real driver and SQL, through host
 * {@code psql}, against the migrated Testcontainers database, and proves that the {@code engaged} section's
 * badges equal {@link InterestScoreQueries#displayScores} under the same constants.
 *
 * <p>The class runs outside a test transaction ({@code NOT_SUPPORTED}) so the fixture commits and the separate
 * psql session can see it. The replay reads whole tables, so the fixture is the whole table set: every article
 * and topic is deleted before and after each test. The interest profile row is never touched.
 *
 * <p>Constants: profile-points 100, learn-rate 2, learned-cap 20, open 0.25, save 0.5, cap 8 or 0. At noul 0.95
 * the hinge is 0.9.
 */
@DataJdbcTest
@Import(TestcontainersConfiguration.class)
@EnableConfigurationProperties(MyfeederProperties.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class InterestCalibrationReplayRunTest {

    private static final Path DRIVER = Path.of("scripts/interest-calibration-replay.sh");
    private static final String FEED_URL = "https://example.test/replay-run-feed.xml";

    @Autowired private JdbcClient jdbcClient;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PostgreSQLContainer postgres;

    @TempDir
    Path outDir;

    private long feedId;
    private Instant now;
    private int guidSeq;

    @BeforeAll
    static void requirePsql() throws Exception {
        boolean available;
        try {
            Process process = new ProcessBuilder("psql", "--version")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            available = process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException e) {
            available = false;
        }
        assumeTrue(available, "host psql is required for the replay run test");
    }

    @BeforeEach
    void setUp() {
        clean();
        feedId = jdbc.queryForObject(
                "INSERT INTO feed (url, title, feed_type) VALUES (?, ?, ?) RETURNING id",
                Long.class, FEED_URL, "Replay Run Feed", "RSS");
        now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    /** Deletes the whole fixture; article deletes cascade to scores, topic scores, votes, engagement and boards. */
    private void clean() {
        jdbc.update("DELETE FROM article");
        jdbc.update("DELETE FROM interest_topic");
        jdbc.update("DELETE FROM board WHERE name LIKE 'replay-run-%'");
        jdbc.update("DELETE FROM feed WHERE url = ?", FEED_URL);
    }

    @Test
    void engagedBadgesEqualTheAppAtTheDefaultsAndWithEngagementOff() throws Exception {
        long rust = insertTopic("replay-run-rust", 20);
        long politics = insertTopic("replay-run-politics", -10);

        List<Long> starred = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            long id = scored(i <= 4, rust);
            insertEngagement(id, "STAR");
            starred.add(id);
        }
        long s5 = starred.get(4);

        long o1 = scored(false, rust);
        insertTopicScore(o1, politics, 0.9);
        insertEngagement(o1, "OPEN_ORIGINAL");

        long vt = scored(false, rust);
        insertEngagement(vt, "STAR");
        insertFeedback(vt, 1);

        long d1 = insertArticle(false);
        insertEngagement(d1, "BOARD");

        long n1 = scored(false, rust);

        runReplay("100:70:40:0.25:0.5:8", "100:70:40:0.25:0.5:0");

        Path cap8 = outDir.resolve("replay-pp100-hi70-ne40-op0.25-sv0.5-cap8.tsv");
        Path cap0 = outDir.resolve("replay-pp100-hi70-ne40-op0.25-sv0.5-cap0.tsv");
        try (Stream<Path> files = Files.list(outDir)) {
            assertThat(files.map(p -> p.getFileName().toString()).toList())
                    .containsExactlyInAnyOrder(cap8.getFileName().toString(), cap0.getFileName().toString());
        }

        Set<Long> expectedIds = new HashSet<>(starred);
        expectedIds.add(o1);
        expectedIds.add(vt);
        Set<Long> read = new HashSet<>(starred.subList(0, 4));

        Map<Long, Integer> badgesAtCap8 = assertEngagedSectionMatchesTheApp(cap8, 8, expectedIds, read, vt);
        Map<Long, Integer> badgesAtCap0 = assertEngagedSectionMatchesTheApp(cap0, 0, expectedIds, read, vt);

        assertThat(expectedIds).doesNotContain(d1, n1);
        // 9 counted saves of 0.9 each fill the cap of 8: rust's engagement part goes 0 -> 8, so S5 gains 7.2 points.
        assertThat(badgesAtCap8.get(s5)).isGreaterThan(badgesAtCap0.get(s5));
    }

    /** Checks one replay file's engaged section against displayScores under the same constants; returns its badges. */
    private Map<Long, Integer> assertEngagedSectionMatchesTheApp(Path tsv, double cap, Set<Long> expectedIds,
                                                                 Set<Long> read, long voted) throws IOException {
        List<String[]> engaged = rows(tsv, "engaged");
        Map<Long, Integer> badges = engaged.stream()
                .collect(Collectors.toMap(r -> Long.parseLong(r[1]), r -> Integer.parseInt(r[2])));
        assertThat(badges.keySet()).as(tsv.getFileName() + " engaged ids").isEqualTo(expectedIds);
        assertThat(engaged).as(tsv.getFileName() + " rows").hasSize(expectedIds.size());

        Map<Long, Integer> app = new InterestScoreQueries(jdbcClient, properties(cap)).displayScores(expectedIds);
        assertThat(badges).as(tsv.getFileName() + " badges equal displayScores").isEqualTo(app);

        for (String[] row : engaged) {
            long id = Long.parseLong(row[1]);
            assertThat(row[4]).as(tsv.getFileName() + " is_read of " + id).isEqualTo(read.contains(id) ? "t" : "f");
            assertThat(row[5]).as(tsv.getFileName() + " voted of " + id).isEqualTo(id == voted ? "t" : "f");
        }
        return badges;
    }

    private static MyfeederProperties properties(double cap) {
        MyfeederProperties props = new MyfeederProperties();
        MyfeederProperties.Interest.Blend blend = props.getInterest().getBlend();
        blend.setProfilePoints(100);
        blend.setLearnRate(2);
        blend.setLearnedCap(20);
        blend.getEngagement().setOpenWeight(0.25);
        blend.getEngagement().setSaveWeight(0.5);
        blend.getEngagement().setCap(cap);
        return props;
    }

    /** Runs the driver against the Testcontainers database with no inherited blend overrides; asserts exit 0. */
    private void runReplay(String... candidates) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(
                Stream.concat(Stream.of("bash", DRIVER.toString()), Stream.of(candidates)).toList());
        Map<String, String> environment = builder.environment();
        for (String name : List.of("LEARN_RATE", "LEARNED_CAP", "WINDOW_DAYS",
                "ENGAGEMENT_OPEN_WEIGHT", "ENGAGEMENT_SAVE_WEIGHT", "ENGAGEMENT_CAP")) {
            environment.remove(name);
        }
        environment.put("MYFEEDER_PG_PASSWORD", postgres.getPassword());
        environment.put("PGHOST", postgres.getHost());
        environment.put("PGPORT", String.valueOf(postgres.getMappedPort(5432)));
        environment.put("PGUSER", postgres.getUsername());
        environment.put("PGDATABASE", postgres.getDatabaseName());
        environment.put("OUT_DIR", outDir.toString());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).as("replay finished within 60 s").isTrue();
        assertThat(process.exitValue()).as("replay exit code; output:\n" + output).isZero();
    }

    /** The tab-split rows of {@code tsv} whose first column is {@code section}. */
    private static List<String[]> rows(Path tsv, String section) throws IOException {
        return Files.readAllLines(tsv, StandardCharsets.UTF_8).stream()
                .map(line -> line.split("\t", -1))
                .filter(columns -> columns[0].equals(section))
                .toList();
    }

    /** One SCORED article (profile 2 of 4) that judged {@code topicId} at noul 0.95. */
    private long scored(boolean read, long topicId) {
        long id = insertArticle(read);
        insertScored(id, 2.0, 4);
        insertTopicScore(id, topicId, 0.95);
        return id;
    }

    private long insertArticle(boolean read) {
        String guid = "replay-run-" + (guidSeq++);
        Instant publishedAt = now.minus(Duration.ofHours(1));
        return jdbc.queryForObject(
                "INSERT INTO article (feed_id, guid, title, url, summary, content, published_at, fetched_at, \"read\") "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class, feedId, guid, "Title " + guid, "https://example.test/" + guid,
                "Summary " + guid, "Content " + guid, Timestamp.from(publishedAt), Timestamp.from(now), read);
    }

    private long insertTopic(String name, int weight) {
        return jdbc.queryForObject(
                "INSERT INTO interest_topic (name, description, weight) VALUES (?, ?, ?) RETURNING id",
                Long.class, name, "Articles about " + name, weight);
    }

    private void insertScored(long articleId, Double profileScore, Integer profileMaxLevel) {
        jdbc.update("INSERT INTO article_score (article_id, status, profile_score, profile_max_level, attempts) "
                + "VALUES (?, 'SCORED', ?, ?, 1)", articleId, profileScore, profileMaxLevel);
    }

    private void insertTopicScore(long articleId, long topicId, double noul) {
        jdbc.update("INSERT INTO article_topic_score (article_id, topic_id, noul, topic_version) VALUES (?, ?, ?, 1)",
                articleId, topicId, noul);
    }

    private void insertFeedback(long articleId, int vote) {
        jdbc.update("INSERT INTO article_feedback (article_id, vote, topics_narrowed) VALUES (?, ?, false)",
                articleId, vote);
    }

    private void insertEngagement(long articleId, String kind) {
        jdbc.update("INSERT INTO article_engagement (article_id, kind) VALUES (?, ?)", articleId, kind);
    }
}
