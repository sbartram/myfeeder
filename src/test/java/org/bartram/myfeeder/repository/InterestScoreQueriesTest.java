package org.bartram.myfeeder.repository;

import org.bartram.myfeeder.TestcontainersConfiguration;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.repository.InterestScoreQueries.BreakdownInputs;
import org.bartram.myfeeder.repository.InterestScoreQueries.PriorityRow;
import org.bartram.myfeeder.repository.InterestScoreQueries.SortKey;
import org.bartram.myfeeder.repository.InterestScoreQueries.TopicContribution;
import org.bartram.myfeeder.repository.InterestScoreQueries.TopicWeight;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Proves the blend CTE and the Priority keyset pages against real Postgres on a fixed fixture.
 *
 * <p>Topics: Rust +20, WebAssembly +14, Politics -30, Zero 0, Gardening +10. Expected raws: a4 133.32;
 * a1, a2, a3 82.2 (tied); a8 75; a6 28.5; a7 10; a5 -30. Unscored: u1 (no row), u2 (FAILED), u3
 * (SKIPPED), u4 (no row, outside the window), u5 (no row, undated). r1 and r2 are read.
 */
@DataJdbcTest
@Import({TestcontainersConfiguration.class, InterestScoreQueries.class})
@EnableConfigurationProperties(MyfeederProperties.class)
class InterestScoreQueriesTest {

    @Autowired private InterestScoreQueries queries;
    @Autowired private JdbcTemplate jdbc;

    private long feedId;
    private Instant now;

    private long rust, webAssembly, politics, zero, gardening;
    private long a1, a2, a3, a4, a5, a6, a7, a8, r1;
    private long u1, u2, u3, u4, u5, r2;

    @BeforeEach
    void setUp() {
        // Inside the rolled-back test transaction, so the fixture is the whole table.
        jdbc.update("DELETE FROM article_score");
        jdbc.update("DELETE FROM article");
        jdbc.update("DELETE FROM interest_topic");
        feedId = jdbc.queryForObject(
                "INSERT INTO feed (url, title, feed_type) VALUES (?, ?, ?) RETURNING id",
                Long.class, "https://example.com/priority-feed.xml", "Priority Feed", "RSS");
        now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        seedFixture();
    }

    @Test
    void fullOrderIsScoreThenDateThenId() {
        assertThat(ids(queries.priorityFirstPage(100)))
                .containsExactly(a4, a3, a1, a2, a8, a6, a7, a5, u1, u3, u5, u2, u4);
    }

    @Test
    void pagedWalksMatchTheUnpagedOrder() {
        List<Long> full = ids(queries.priorityFirstPage(100));
        for (int n : new int[] {3, 5}) {
            assertThat(walk(n)).as("walk in pages of %d", n).containsExactlyElementsOf(full);
        }
    }

    @Test
    void cursorReadBetweenPagesStillContinues() {
        List<PriorityRow> page1 = queries.priorityFirstPage(3);
        List<PriorityRow> page2 = queries.priorityPageAfter(page1.getLast().key(), 3);
        assertThat(ids(page2)).containsExactly(a2, a8, a6);

        markRead(a6); // scored cursor
        List<PriorityRow> page3 = queries.priorityPageAfter(page2.getLast().key(), 3);
        assertThat(ids(page3)).containsExactly(a7, a5, u1);

        markRead(u1); // unscored cursor
        assertThat(ids(queries.priorityPageAfter(page3.getLast().key(), 3))).containsExactly(u3, u5, u2);
    }

    @Test
    void cursorScoreDropBetweenPagesSkipsNothing() {
        List<PriorityRow> page1 = queries.priorityFirstPage(3);
        assertThat(ids(page1)).containsExactly(a4, a3, a1);

        // a1 drops from 82.2 to 18.2 (its topics only) after page 1 was served
        jdbc.update("UPDATE article_score SET profile_score = 0 WHERE article_id = ?", a1);

        assertThat(walkFrom(page1, 3))
                .containsExactly(a4, a3, a1, a2, a8, a6, a1, a7, a5, u1, u3, u5, u2, u4);
    }

    @Test
    void cursorScoreRiseBetweenPagesRepeatsNothing() {
        List<PriorityRow> page1 = queries.priorityFirstPage(3);
        assertThat(ids(page1)).containsExactly(a4, a3, a1);

        // a1 rises from 82.2 to 118.2 after page 1 was served
        jdbc.update("UPDATE article_score SET profile_score = 4.0 WHERE article_id = ?", a1);

        assertThat(ids(queries.priorityPageAfter(page1.getLast().key(), 3))).containsExactly(a2, a8, a6);
    }

    @Test
    void servedKeysCarryTheSortTuple() {
        Map<Long, SortKey> keys = new HashMap<>();
        for (PriorityRow row : queries.priorityFirstPage(100)) {
            assertThat(row.key().id()).isEqualTo(row.article().getId());
            keys.put(row.article().getId(), row.key());
        }
        assertThat(keys.get(a1).score()).isEqualTo(82.2);
        assertThat(keys.get(a4).score()).isEqualTo(133.32);
        for (long unscored : List.of(u1, u2, u3, u4, u5)) {
            assertThat(keys.get(unscored).score()).as("sort score of %d", unscored)
                    .isEqualTo(Double.NEGATIVE_INFINITY);
        }
        // u5 has no published date, so its date key is fetched_at
        assertThat(keys.get(u5).date()).isEqualTo(now.minus(Duration.ofHours(3)));
    }

    @Test
    void cursorWalkIsZoneIndependent() {
        List<Long> full = ids(queries.priorityFirstPage(100));
        TimeZone saved = TimeZone.getDefault();
        try {
            // +05:30 differs from the zone the pooled session started with; a shifted cursor date
            // would break the a3/a1 tie at the first page boundary
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
            assertThat(walk(3)).as("walk in pages of 3").containsExactlyElementsOf(full);
            assertThat(walk(5)).as("walk in pages of 5").containsExactlyElementsOf(full);
        } finally {
            TimeZone.setDefault(saved);
        }
    }

    @Test
    void unscoredRowsHaveNullInterestScore() {
        Map<Long, Integer> scores = scores(queries.priorityFirstPage(100));
        for (long unscored : List.of(u1, u2, u3, u4, u5)) {
            assertThat(scores).containsKey(unscored);
            assertThat(scores.get(unscored)).as("interestScore of %d", unscored).isNull();
        }
    }

    @Test
    void displayIsClampedAndRoundsHalfAwayFromZero() {
        Map<Long, Integer> scores = scores(queries.priorityFirstPage(100));
        assertThat(scores.get(a4)).isEqualTo(100);
        assertThat(scores.get(a5)).isEqualTo(0);
        assertThat(scores.get(a6)).isEqualTo(29);
        assertThat(scores.get(a1)).isEqualTo(82);
        assertThat(scores.get(a7)).isEqualTo(10);
        assertThat(scores.get(a8)).isEqualTo(75);
    }

    @Test
    void readArticlesAreExcluded() {
        assertThat(ids(queries.priorityFirstPage(100))).doesNotContain(r1, r2);
        assertThat(ids(queries.priorityPageAfter(queries.priorityFirstPage(1).getFirst().key(), 100)))
                .doesNotContain(r1, r2);
    }

    @Test
    void weightChangeReordersWithoutRescoring() {
        jdbc.update("UPDATE interest_topic SET weight = 30 WHERE name = 'Politics'");

        List<PriorityRow> page = queries.priorityFirstPage(100);
        Map<Long, Integer> scores = scores(page);
        assertThat(scores.get(a1)).isEqualTo(94);
        assertThat(scores.get(a2)).isEqualTo(94);
        assertThat(scores.get(a3)).isEqualTo(94);
        assertThat(scores.get(a5)).isEqualTo(30);
        assertThat(ids(page)).containsExactly(a4, a3, a1, a2, a8, a5, a6, a7, u1, u3, u5, u2, u4);
    }

    @Test
    void topicAddedAfterScoringContributesNothing() {
        List<Long> before = ids(queries.priorityFirstPage(100));
        insertTopic("Later", 50);

        List<PriorityRow> page = queries.priorityFirstPage(100);
        assertThat(scores(page).get(a1)).isEqualTo(82);
        assertThat(ids(page)).containsExactlyElementsOf(before);
    }

    @Test
    void deletedTopicContributionCascadesAway() {
        jdbc.update("DELETE FROM interest_topic WHERE name = 'WebAssembly'");

        Map<Long, Integer> scores = scores(queries.priorityFirstPage(100));
        assertThat(scores.get(a1)).isEqualTo(75);
        assertThat(scores.get(a6)).isEqualTo(25);
        assertThat(scores.get(a4)).isEqualTo(100);
    }

    @Test
    void emptyWhenNoUnreadArticles() {
        jdbc.update("UPDATE article SET \"read\" = true");

        assertThat(queries.priorityFirstPage(100)).isEmpty();
    }

    @Test
    void displayScoresAreIdScopedIncludingReadArticles() {
        Map<Long, Integer> scores = queries.displayScores(List.of(a1, a4, a5, r1, u1, u2, u3));

        assertThat(scores).isEqualTo(Map.of(a1, 82, a4, 100, a5, 0, r1, 100));
    }

    @Test
    void displayScoresOfNoIdsIsEmpty() {
        assertThat(queries.displayScores(List.of())).isEmpty();
    }

    @Test
    void breakdownInputsMatchTheBadge() {
        BreakdownInputs in = queries.breakdownInputs(a1).orElseThrow();

        assertThat(in.raw()).isEqualByComparingTo("82.200000");
        assertThat(in.raw().scale()).isEqualTo(6);
        assertThat(in.total()).isEqualTo(82);
        assertThat(in.display()).isEqualTo(82);
        assertThat(in.profileScore()).isEqualTo(2.56);
        assertThat(in.profileMaxLevel()).isEqualTo(4);
        assertThat(in.profileExact()).isEqualByComparingTo("64.000000");

        Map<String, TopicContribution> topics = new HashMap<>();
        in.topics().forEach(t -> topics.put(t.name(), t));
        assertThat(topics).containsOnlyKeys("Rust", "WebAssembly", "Politics", "Gardening");

        TopicContribution rustRow = topics.get("Rust");
        assertThat(rustRow.topicId()).isEqualTo(rust);
        assertThat(rustRow.noul()).isEqualTo(0.93);
        assertThat(rustRow.hinge()).isCloseTo(0.86, within(1e-9));
        assertThat(rustRow.weight()).isEqualTo(20.0);
        assertThat(rustRow.exact()).isEqualByComparingTo("17.200000");
        assertThat(topics.get("WebAssembly").exact()).isEqualByComparingTo("7.000000");
        assertThat(topics.get("Politics").exact()).isEqualByComparingTo("-6.000000");
        assertThat(topics.get("Politics").weight()).isEqualTo(-30.0);
        assertThat(topics.get("Gardening").hinge()).isZero();
        assertThat(topics.get("Gardening").exact()).isEqualByComparingTo("0");
    }

    @Test
    void breakdownInputsForCappedAndFlooredArticles() {
        BreakdownInputs capped = queries.breakdownInputs(a4).orElseThrow();
        assertThat(capped.total()).isEqualTo(133);
        assertThat(capped.display()).isEqualTo(100);

        BreakdownInputs floored = queries.breakdownInputs(a5).orElseThrow();
        assertThat(floored.total()).isEqualTo(-30);
        assertThat(floored.display()).isEqualTo(0);

        BreakdownInputs halfUp = queries.breakdownInputs(a6).orElseThrow();
        assertThat(halfUp.total()).isEqualTo(29);
        assertThat(halfUp.display()).isEqualTo(29);
    }

    @Test
    void breakdownInputsForNoProfileQuestion() {
        BreakdownInputs in = queries.breakdownInputs(a7).orElseThrow();

        assertThat(in.profileScore()).isNull();
        assertThat(in.profileMaxLevel()).isNull();
        assertThat(in.profileExact()).isNull();
        assertThat(in.total()).isEqualTo(10);
    }

    @Test
    void breakdownInputsEmptyForUnscored() {
        assertThat(queries.breakdownInputs(u1)).isEmpty();
        assertThat(queries.breakdownInputs(u2)).isEmpty();
        assertThat(queries.breakdownInputs(u3)).isEmpty();
    }

    @Test
    void topicAddedAfterScoringIsNotListed() {
        insertTopic("Later", 50);

        assertThat(queries.breakdownInputs(a1).orElseThrow().topics())
                .extracting(TopicContribution::name)
                .containsExactlyInAnyOrder("Rust", "WebAssembly", "Politics", "Gardening");
    }

    @Test
    void oneNumberEverywhere() {
        List<Long> scored = List.of(a1, a2, a3, a4, a5, a6, a7, a8, r1);
        Map<Long, Integer> display = queries.displayScores(scored);
        Map<Long, Integer> priority = scores(queries.priorityFirstPage(100));

        for (long id : scored) {
            Optional<BreakdownInputs> in = queries.breakdownInputs(id);
            assertThat(in).as("breakdown of %d", id).isPresent();
            assertThat(in.get().display()).as("breakdown vs badge of %d", id).isEqualTo(display.get(id));
            if (id != r1) {
                assertThat(priority.get(id)).as("Priority vs badge of %d", id).isEqualTo(display.get(id));
            }
        }
    }

    @Test
    void learnedFollowsOneUpVote() {
        insertFeedback(a1, 1, false);

        Map<Long, TopicWeight> w = queries.topicWeights(List.of(rust, webAssembly, politics, zero, gardening));

        assertWeight(w.get(rust), 1.72, 21.72);
        assertWeight(w.get(webAssembly), 1.0, 15.0);
        assertWeight(w.get(politics), 0.4, -29.6);
        assertWeight(w.get(gardening), 0.0, 10.0);
        assertWeight(w.get(zero), 0.0, 0.0);
    }

    @Test
    void learnedIsCappedAtTwenty() {
        insertMatchingArticles(rust, 1.0, 12).forEach(id -> insertFeedback(id, 1, false));

        TopicWeight w = queries.topicWeights(List.of(rust)).get(rust);

        assertThat(w.learnedRaw()).isCloseTo(24.0, within(1e-6));
        assertWeight(w, 20.0, 40.0);
    }

    @Test
    void positiveBaseNeverGoesBelowZero() {
        long small = insertTopic("Small", 5);
        insertMatchingArticles(small, 1.0, 12).forEach(id -> insertFeedback(id, -1, false));

        assertWeight(queries.topicWeights(List.of(small)).get(small), -20.0, 0.0);
    }

    @Test
    void negativeBaseNeverGoesAboveZero() {
        long mild = insertTopic("Mild", -5);
        insertMatchingArticles(mild, 1.0, 12).forEach(id -> insertFeedback(id, 1, false));

        assertWeight(queries.topicWeights(List.of(mild)).get(mild), 20.0, 0.0);
    }

    @Test
    void effectiveWeightStaysWithinFifty() {
        long big = insertTopic("Big", 45);
        long deep = insertTopic("Deep", -45);
        insertMatchingArticles(big, 1.0, 12).forEach(id -> insertFeedback(id, 1, false));
        insertMatchingArticles(deep, 1.0, 12).forEach(id -> insertFeedback(id, -1, false));

        Map<Long, TopicWeight> w = queries.topicWeights(List.of(big, deep));

        assertWeight(w.get(big), 20.0, 50.0);
        assertWeight(w.get(deep), -20.0, -50.0);
    }

    @Test
    void zeroBaseMovesBothWays() {
        insertFeedback(a8, 1, false);
        assertThat(queries.topicWeights(List.of(zero)).get(zero).effective()).isCloseTo(1.6, within(1e-6));

        jdbc.update("UPDATE article_feedback SET vote = -1 WHERE article_id = ?", a8);
        assertThat(queries.topicWeights(List.of(zero)).get(zero).effective()).isCloseTo(-1.6, within(1e-6));
    }

    @Test
    void narrowedVoteMovesOnlyPickedTopics() {
        insertFeedback(a1, -1, true);
        insertPick(a1, politics);

        Map<Long, TopicWeight> w = queries.topicWeights(List.of(rust, webAssembly, politics));

        assertThat(w.get(politics).effective()).isCloseTo(-30.4, within(1e-6));
        assertThat(w.get(rust).effective()).isCloseTo(20.0, within(1e-6));
        assertThat(w.get(webAssembly).effective()).isCloseTo(14.0, within(1e-6));
    }

    @Test
    void narrowedVoteWithNoPicksMovesNothing() {
        insertFeedback(a1, -1, true);

        Map<Long, TopicWeight> w = queries.topicWeights(List.of(rust, webAssembly, politics, zero, gardening));

        assertWeight(w.get(rust), 0.0, 20.0);
        assertWeight(w.get(webAssembly), 0.0, 14.0);
        assertWeight(w.get(politics), 0.0, -30.0);
        assertWeight(w.get(zero), 0.0, 0.0);
        assertWeight(w.get(gardening), 0.0, 10.0);
    }

    @Test
    void voteOnUnscoredArticleCountsNothing() {
        insertTopicScore(u2, rust, 1.0);
        insertFeedback(u1, 1, false);
        insertFeedback(u2, 1, false);

        assertWeight(queries.topicWeights(List.of(rust)).get(rust), 0.0, 20.0);
    }

    @Test
    void deletingTheVoteRestoresExactly() {
        List<Long> all = List.of(rust, webAssembly, politics, zero, gardening);
        Map<Long, TopicWeight> before = queries.topicWeights(all);

        insertFeedback(a1, 1, false);
        assertThat(queries.topicWeights(all)).isNotEqualTo(before);
        jdbc.update("DELETE FROM article_feedback WHERE article_id = ?", a1);

        assertThat(queries.topicWeights(all)).isEqualTo(before);
    }

    @Test
    void badgeReflectsLearnedWeight() {
        insertFeedback(a1, 1, false);

        Map<Long, Integer> display = queries.displayScores(List.of(a1, a2, a4));

        assertThat(display).containsEntry(a1, 84).containsEntry(a2, 84).containsEntry(a4, 100);
        assertThat(queries.breakdownInputs(a1).orElseThrow().raw()).isEqualByComparingTo("84.2592");
    }

    @Test
    void topicWeightsOfNoIdsIsEmpty() {
        assertThat(queries.topicWeights(List.of())).isEmpty();
    }

    private static void assertWeight(TopicWeight w, double learned, double effective) {
        assertThat(w.learned()).as("learned of %s", w.name()).isCloseTo(learned, within(1e-6));
        assertThat(w.effective()).as("effective of %s", w.name()).isCloseTo(effective, within(1e-6));
    }

    private void seedFixture() {
        rust = insertTopic("Rust", 20);
        webAssembly = insertTopic("WebAssembly", 14);
        politics = insertTopic("Politics", -30);
        zero = insertTopic("Zero", 0);
        gardening = insertTopic("Gardening", 10);

        a1 = insertArticle("a1", false, now.minus(Duration.ofHours(1)), now);
        a2 = insertArticle("a2", false, now.minus(Duration.ofHours(2)), now);
        a3 = insertArticle("a3", false, now.minus(Duration.ofHours(1)), now);
        a4 = insertArticle("a4", false, now.minus(Duration.ofHours(5)), now);
        a5 = insertArticle("a5", false, now.minus(Duration.ofHours(1)), now);
        a6 = insertArticle("a6", false, now.minus(Duration.ofHours(3)), now);
        a7 = insertArticle("a7", false, now.minus(Duration.ofHours(2)), now);
        a8 = insertArticle("a8", false, now.minus(Duration.ofHours(4)), now);
        r1 = insertArticle("r1", true, now.minus(Duration.ofHours(1)), now);
        u1 = insertArticle("u1", false, now.minus(Duration.ofMinutes(30)), now);
        u2 = insertArticle("u2", false, now.minus(Duration.ofHours(4)), now);
        u3 = insertArticle("u3", false, now.minus(Duration.ofHours(2)), now);
        u4 = insertArticle("u4", false, now.minus(Duration.ofDays(20)), now);
        u5 = insertArticle("u5", false, null, now.minus(Duration.ofHours(3)));
        r2 = insertArticle("r2", true, now.minus(Duration.ofHours(1)), now);

        // Every article_score row before any article_topic_score row (FK)
        for (long tied : List.of(a1, a2, a3)) {
            insertScored(tied, 2.56, 4);
        }
        insertScored(a4, 4.0, 4);
        insertScored(a5, 0.0, 4);
        insertScored(a6, 1.0, 4);
        insertScored(a7, null, null);
        insertScored(a8, 3.0, 4);
        insertScored(r1, 4.0, 4);
        insertStatus(u2, "FAILED");
        insertStatus(u3, "SKIPPED");

        for (long tied : List.of(a1, a2, a3)) {
            insertTopicScore(tied, rust, 0.93);
            insertTopicScore(tied, webAssembly, 0.75);
            insertTopicScore(tied, politics, 0.60);
            insertTopicScore(tied, gardening, 0.20);
        }
        insertTopicScore(a4, rust, 0.99);
        insertTopicScore(a4, webAssembly, 0.99);
        insertTopicScore(a5, politics, 1.0);
        insertTopicScore(a6, webAssembly, 0.625);
        insertTopicScore(a7, rust, 0.75);
        insertTopicScore(a8, zero, 0.9);
    }

    private List<Long> walk(int n) {
        return walkFrom(queries.priorityFirstPage(n), n);
    }

    /** Continues a walk in pages of {@code n} from an already served first page. */
    private List<Long> walkFrom(List<PriorityRow> first, int n) {
        List<Long> walked = new ArrayList<>();
        List<PriorityRow> page = first;
        while (true) {
            walked.addAll(ids(page));
            if (page.size() < n) {
                return walked;
            }
            page = queries.priorityPageAfter(page.getLast().key(), n);
        }
    }

    private void markRead(long articleId) {
        jdbc.update("UPDATE article SET \"read\" = true WHERE id = ?", articleId);
    }

    private long insertArticle(String guid, boolean read, Instant publishedAt, Instant fetchedAt) {
        return jdbc.queryForObject(
                "INSERT INTO article (feed_id, guid, title, url, summary, content, published_at, fetched_at, \"read\") "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
                Long.class, feedId, guid, "Title " + guid, "https://example.com/" + guid,
                "Summary " + guid, "Content " + guid,
                publishedAt == null ? null : Timestamp.from(publishedAt), Timestamp.from(fetchedAt), read);
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

    private void insertStatus(long articleId, String status) {
        jdbc.update("INSERT INTO article_score (article_id, status, attempts) VALUES (?, ?, 1)", articleId, status);
    }

    private void insertTopicScore(long articleId, long topicId, double noul) {
        jdbc.update("INSERT INTO article_topic_score (article_id, topic_id, noul, topic_version) VALUES (?, ?, ?, 1)",
                articleId, topicId, noul);
    }

    private void insertFeedback(long articleId, int vote, boolean narrowed) {
        jdbc.update("INSERT INTO article_feedback (article_id, vote, topics_narrowed) VALUES (?, ?, ?)",
                articleId, vote, narrowed);
    }

    private void insertPick(long articleId, long topicId) {
        jdbc.update("INSERT INTO article_feedback_topic (article_id, topic_id) VALUES (?, ?)", articleId, topicId);
    }

    /** Inserts {@code count} unread SCORED articles (no profile) that each judged only {@code topicId}. */
    private List<Long> insertMatchingArticles(long topicId, double noul, int count) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long id = insertArticle("m" + topicId + "-" + i, false, now.minus(Duration.ofHours(1)), now);
            insertScored(id, null, null);
            insertTopicScore(id, topicId, noul);
            ids.add(id);
        }
        return ids;
    }

    private static List<Long> ids(List<PriorityRow> rows) {
        return rows.stream().map(row -> row.article().getId()).toList();
    }

    /** id to interestScore; a HashMap because unscored rows carry null values. */
    private static Map<Long, Integer> scores(List<PriorityRow> rows) {
        Map<Long, Integer> scores = new HashMap<>();
        rows.forEach(row -> scores.put(row.article().getId(), row.article().getInterestScore()));
        return scores;
    }
}
