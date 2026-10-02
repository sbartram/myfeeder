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
import java.math.BigDecimal;
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

    /** The default engagement constants at tiers 70 / 40, and the file the driver writes for them. */
    private static final String CANDIDATE = "100:70:40:0.25:0.5:8";
    private static final String CANDIDATE_TSV = "replay-pp100-hi70-ne40-op0.25-sv0.5-cap8.tsv";

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

    /**
     * D-07 / D-10: the simulated backfill counts every star and board row as a save under the live rules. At
     * learn-rate 2, save 0.5 and hinge 0.9 one save adds 0.9 to go's eng_raw: S (starred), B (board only) and O
     * (opened and starred, counted once as a save) give 2.7. V (voted), U (unscored) and N (negative topic only)
     * add nothing. The real learned model counts only O's open: 2 x 0.25 x 0.9 = 0.45.
     */
    @Test
    void backfillCountsStarsAndBoardsAsSavesUnderTheLiveRules() throws Exception {
        BackfillFixture fx = backfillFixture();
        long go = fx.go();
        long neg = fx.neg();

        runReplay(CANDIDATE);
        Path tsv = outDir.resolve(CANDIDATE_TSV);

        Map<Long, String[]> backfill = byTopic(rows(tsv, "backfill-learned"));
        Map<Long, String[]> learned = byTopic(rows(tsv, "learned"));
        assertThat(backfill.keySet()).containsExactlyInAnyOrder(go, neg);
        assertThat(learned.keySet()).containsExactlyInAnyOrder(go, neg);

        assertThat(new BigDecimal(backfill.get(go)[7])).as("go backfill eng_raw").isEqualByComparingTo("2.700");
        assertThat(new BigDecimal(backfill.get(go)[8])).as("go backfill eng").isEqualByComparingTo("2.700");
        assertThat(backfill.get(go)[9]).as("go backfill eng_at_cap").isEqualTo("f");
        assertThat(new BigDecimal(learned.get(go)[7])).as("go real eng_raw").isEqualByComparingTo("0.450");
        assertThat(new BigDecimal(backfill.get(neg)[7])).as("neg backfill eng_raw").isEqualByComparingTo("0.000");
        assertThat(new BigDecimal(learned.get(neg)[7])).as("neg real eng_raw").isEqualByComparingTo("0.000");

        List<String[]> summary = rows(tsv, "summary");
        List<String[]> backfillSummary = rows(tsv, "backfill-summary");
        assertThat(summary).hasSize(1);
        assertThat(backfillSummary).hasSize(1);
        assertThat(backfillSummary.get(0)).as("backfill-summary columns").hasSameSizeAs(summary.get(0));
        assertThat(backfillSummary.get(0)[4]).as("scored_unread").isEqualTo(summary.get(0)[4]).isEqualTo("5");
    }

    /** The S, B, O, V, U and N articles of the backfill fixture and its two topics. */
    private record BackfillFixture(long go, long neg, long s, long b, long o, long v, long u, long n) {}

    /**
     * Topics go (+10) and neg (-10). S: SCORED, go 0.95, starred. B: SCORED, go 0.95, on a board. O: SCORED,
     * go 0.95, opened and starred. V: SCORED, go 0.95, starred, +1 vote. U: starred, no score row. N: SCORED,
     * neg 0.95 only, starred.
     */
    private BackfillFixture backfillFixture() {
        long go = insertTopic("replay-run-go", 10);
        long neg = insertTopic("replay-run-neg", -10);
        long board = insertBoard("replay-run-board");

        long s = scored(false, go);
        star(s);
        long b = scored(false, go);
        insertBoardArticle(board, b);
        long o = scored(false, go);
        insertEngagement(o, "OPEN_ORIGINAL");
        star(o);
        long v = scored(false, go);
        star(v);
        insertFeedback(v, 1);
        long u = insertArticle(false);
        star(u);
        long n = insertArticle(false);
        insertScored(n, 2.0, 4);
        insertTopicScore(n, neg, 0.95);
        star(n);
        return new BackfillFixture(go, neg, s, b, o, v, u, n);
    }

    /** backfill-pool: 6 starred or boarded, O already engaged, 5 added, of which S, B and N are SCORED and unvoted. */
    @Test
    void backfillPoolCountsMatchTheFixture() throws Exception {
        backfillFixture();

        runReplay(CANDIDATE);

        List<String[]> pool = rows(outDir.resolve(CANDIDATE_TSV), "backfill-pool");
        assertThat(pool).hasSize(1);
        assertThat(pool.get(0)).containsExactly("backfill-pool", "6", "1", "5", "3");
    }

    /**
     * D-09: the dormant row counts distinct engaged articles. E1 OPEN_ORIGINAL SCORED unread; E2 STAR SCORED voted;
     * E3 BOARD with no score row, read; E4 RAINDROP FAILED; E5 OPEN_ORIGINAL and STAR, SKIPPED, published 30 days
     * ago; E6 STAR SCORED read.
     */
    @Test
    void dormantAndKindCountsMatchTheFixture() throws Exception {
        long e1 = insertArticle(false);
        insertScored(e1, 2.0, 4);
        insertEngagement(e1, "OPEN_ORIGINAL");
        long e2 = insertArticle(false);
        insertScored(e2, 2.0, 4);
        insertEngagement(e2, "STAR");
        insertFeedback(e2, 1);
        long e3 = insertArticle(true);
        insertEngagement(e3, "BOARD");
        long e4 = insertArticle(false);
        insertScoreRow(e4, "FAILED");
        insertEngagement(e4, "RAINDROP");
        long e5 = insertArticle(false, now.minus(Duration.ofDays(30)));
        insertScoreRow(e5, "SKIPPED");
        insertEngagement(e5, "OPEN_ORIGINAL");
        insertEngagement(e5, "STAR");
        long e6 = insertArticle(true);
        insertScored(e6, 2.0, 4);
        insertEngagement(e6, "STAR");

        runReplay(CANDIDATE);
        Path tsv = outDir.resolve(CANDIDATE_TSV);

        List<String[]> dormant = rows(tsv, "dormant");
        assertThat(dormant).hasSize(1);
        assertThat(dormant.get(0)).containsExactly("dormant", "6", "3", "2", "1", "3", "1", "1", "1", "1", "1", "50.0");

        assertThat(rows(tsv, "dormant-kind")).containsExactly(
                new String[] {"dormant-kind", "BOARD", "1", "1"},
                new String[] {"dormant-kind", "OPEN_ORIGINAL", "2", "1"},
                new String[] {"dormant-kind", "RAINDROP", "1", "1"},
                new String[] {"dormant-kind", "STAR", "3", "1"});
    }

    /**
     * D-02 / CAL-03 boundary: the floor is met at exactly 30 counted articles and 3 topics. A voted engaged article
     * raises neither count, and neither a noul of exactly 0.5 nor a negative-base topic counts as a topic.
     */
    @Test
    void floorIsMetExactlyAtThirtyCountedAndThreeTopics() throws Exception {
        long t1 = insertTopic("replay-run-t1", 10);
        long t2 = insertTopic("replay-run-t2", 10);
        long t3 = insertTopic("replay-run-t3", 10);
        long neg = insertTopic("replay-run-neg", -10);
        long edge = insertTopic("replay-run-edge", 10);

        List<Long> counted = new ArrayList<>();
        for (int i = 0; i < 29; i++) {
            counted.add(floorArticle(t1, t2, t3));
        }
        long voted = floorArticle(t1, t2, t3);
        insertFeedback(voted, 1);

        assertThat(floorRow()).containsExactly("floor", "29", "3", "f");

        counted.add(floorArticle(t1, t2, t3));
        assertThat(floorRow()).containsExactly("floor", "30", "3", "t");

        jdbc.update("DELETE FROM article_topic_score WHERE topic_id = ?", t3);
        for (long id : counted) {
            insertTopicScore(id, neg, 0.9);
            insertTopicScore(id, edge, 0.5);
        }
        insertTopicScore(voted, edge, 0.9);
        assertThat(floorRow()).containsExactly("floor", "30", "2", "f");
    }

    /** One OPEN_ORIGINAL-engaged SCORED article that judged each topic at noul 0.9. */
    private long floorArticle(long... topics) {
        long id = insertArticle(false);
        insertScored(id, 2.0, 4);
        for (long topic : topics) {
            insertTopicScore(id, topic, 0.9);
        }
        insertEngagement(id, "OPEN_ORIGINAL");
        return id;
    }

    /** Runs the replay and returns its single floor row. */
    private String[] floorRow() throws Exception {
        runReplay(CANDIDATE);
        List<String[]> floor = rows(outDir.resolve(CANDIDATE_TSV), "floor");
        assertThat(floor).hasSize(1);
        return floor.get(0);
    }

    /**
     * D-04: eng_articles counts the articles in the statement's engaged CTE that are SCORED and match the topic
     * above noul 0.5. go is matched by 3 engaged SCORED unvoted articles; a voted one, an engaged FAILED one and
     * one at exactly 0.5 do not count. The backfill also counts a starred-only SCORED match.
     */
    @Test
    void engArticlesCountsUnvotedScoredMatches() throws Exception {
        long go = insertTopic("replay-run-go", 10);
        for (int i = 0; i < 3; i++) {
            insertEngagement(scored(false, go), "OPEN_ORIGINAL");
        }
        long votedArticle = scored(false, go);
        insertEngagement(votedArticle, "STAR");
        insertFeedback(votedArticle, 1);
        long failed = insertArticle(false);
        insertScoreRow(failed, "FAILED");
        insertTopicScore(failed, go, 0.95);
        insertEngagement(failed, "OPEN_ORIGINAL");
        long atHalf = insertArticle(false);
        insertScored(atHalf, 2.0, 4);
        insertTopicScore(atHalf, go, 0.5);
        insertEngagement(atHalf, "OPEN_ORIGINAL");
        star(scored(false, go));

        runReplay(CANDIDATE);
        Path tsv = outDir.resolve(CANDIDATE_TSV);

        String[] learned = byTopic(rows(tsv, "learned")).get(go);
        String[] backfill = byTopic(rows(tsv, "backfill-learned")).get(go);
        assertThat(learned).hasSize(11);
        assertThat(backfill).hasSize(11);
        assertThat(learned[10]).as("learned eng_articles").isEqualTo("3");
        assertThat(backfill[10]).as("backfill-learned eng_articles").isEqualTo("4");
    }

    /** Learned-shaped rows keyed by topic id (column 2). */
    private static Map<Long, String[]> byTopic(List<String[]> rows) {
        return rows.stream().collect(Collectors.toMap(r -> Long.parseLong(r[1]), r -> r));
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
        return insertArticle(read, now.minus(Duration.ofHours(1)));
    }

    private long insertArticle(boolean read, Instant publishedAt) {
        String guid = "replay-run-" + (guidSeq++);
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

    /** A FAILED or SKIPPED score row (no profile score). */
    private void insertScoreRow(long articleId, String status) {
        jdbc.update("INSERT INTO article_score (article_id, status, attempts) VALUES (?, ?, 1)", articleId, status);
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

    private void star(long articleId) {
        jdbc.update("UPDATE article SET starred = true WHERE id = ?", articleId);
    }

    private long insertBoard(String name) {
        return jdbc.queryForObject("INSERT INTO board (name) VALUES (?) RETURNING id", Long.class, name);
    }

    private void insertBoardArticle(long boardId, long articleId) {
        jdbc.update("INSERT INTO board_article (board_id, article_id) VALUES (?, ?)", boardId, articleId);
    }
}
