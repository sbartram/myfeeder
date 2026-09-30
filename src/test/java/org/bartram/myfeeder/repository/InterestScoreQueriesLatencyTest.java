package org.bartram.myfeeder.repository;

import org.bartram.myfeeder.TestcontainersConfiguration;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.repository.InterestScoreQueries.TopicWeight;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Records and guards the latency of the extended learned model (D-13, D-17, roadmap SC-5).
 *
 * <p>The fixture follows the research recipe (09-RESEARCH.md, "Latency test recipe (D-13)"): 10 topics
 * with mixed bases, 30,000 articles (70% read), the first 5,000 SCORED with 10 nouls each, the next 500
 * FAILED, 130 votes on SCORED articles (10 % narrowed to one pick), and then about 20,000
 * {@code article_engagement} rows over about 13,000 articles. Each query is timed as the median of 5 warm
 * runs after 3 warm-ups, first with no engagement rows (the baseline) and then with them, and must stay
 * within {@code 10 x baseline + 250 ms} (D-17). The guard only catches catastrophic plans; the printed
 * {@code LATENCY} and {@code EXPLAIN} lines are the record for VERIFICATION.
 *
 * <p>Rows are ranked by id ({@code rn}) and every choice is an {@code rn} modulo rule, so the fixture is
 * deterministic. It adds no index, migration or query hint (D-13).
 */
@DataJdbcTest
@Import(TestcontainersConfiguration.class)
@EnableConfigurationProperties(MyfeederProperties.class)
class InterestScoreQueriesLatencyTest {

    private static final int WARM_UPS = 3;
    private static final int RUNS = 5;

    /** Every article with its 1-based rank by id. */
    private static final String RANKED = "(SELECT id, row_number() OVER (ORDER BY id) AS rn FROM article) r";

    /** About 15,900 of the 30,000 articles are candidates; 17 is coprime with the kind moduli 3, 4, 5 and 7. */
    private static final String CHOSEN = "r.rn % 17 < 9";

    /** 130 voted SCORED articles: rn 1, 39, ..., 4903. */
    private static final String VOTED = "r.rn % 38 = 1 AND r.rn <= 4940";

    @Autowired private JdbcClient jdbcClient;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void extendedLearnedCteStaysWithinTheLatencyBudget() {
        InterestScoreQueries queries = new InterestScoreQueries(jdbcClient, properties());
        seedWithoutEngagement();
        long engagedArticle = breakdownArticle();

        assertThat(queries.allTopicWeights()).extracting(TopicWeight::engagementLearned).containsOnly(0.0);
        double basePriority = medianMillis(() -> queries.priorityFirstPage(50));
        double baseBreakdown = medianMillis(() -> queries.breakdownInputs(engagedArticle));
        double baseWeights = medianMillis(queries::allTopicWeights);

        seedEngagement();
        assertThat(queries.priorityFirstPage(50)).hasSize(50);
        long rows = count("SELECT count(*) FROM article_engagement");
        long engagedArticles = count("SELECT count(DISTINCT article_id) FROM article_engagement");
        long scoredEngaged = count("SELECT count(DISTINCT g.article_id) FROM article_engagement g "
                + "JOIN article_score s ON s.article_id = g.article_id AND s.status = 'SCORED'");
        long votedEngaged = count("SELECT count(DISTINCT g.article_id) FROM article_engagement g "
                + "JOIN article_feedback f ON f.article_id = g.article_id");
        assertThat(rows).as("engagement rows").isBetween(18_000L, 22_000L);
        assertThat(scoredEngaged).as("SCORED engaged articles").isGreaterThanOrEqualTo(1_000L);
        assertThat(votedEngaged).as("engaged articles that also carry a vote").isGreaterThanOrEqualTo(10L);
        assertThat(count("SELECT count(*) FROM article_engagement WHERE article_id = " + engagedArticle))
                .as("the breakdown article is engaged").isPositive();
        assertThat(queries.allTopicWeights()).extracting(TopicWeight::engagementLearned)
                .as("the engagement branch is exercised").anyMatch(e -> e > 0);

        double extPriority = medianMillis(() -> queries.priorityFirstPage(50));
        double extBreakdown = medianMillis(() -> queries.breakdownInputs(engagedArticle));
        double extWeights = medianMillis(queries::allTopicWeights);

        report("priority", basePriority, extPriority);
        report("breakdown", baseBreakdown, extBreakdown);
        report("topic-weights", baseWeights, extWeights);
        System.out.printf("LATENCY engagement-rows=%d engaged-articles=%d scored-engaged=%d voted-engaged=%d%n",
                rows, engagedArticles, scoredEngaged, votedEngaged);
        explain("learned", InterestScoreQueries.LEARNED_CTE + " SELECT * FROM eff2");
        // sum(raw_n) keeps the planner from dropping the unused learned joins that a bare count(*) lets it remove.
        explain("blend", InterestScoreQueries.blendCte(InterestScoreQueries.UNREAD_SCOPE)
                + " SELECT count(*), sum(b.raw_n) FROM blended b");

        assertWithinBudget("priority", basePriority, extPriority);
        assertWithinBudget("breakdown", baseBreakdown, extBreakdown);
        assertWithinBudget("topic-weights", baseWeights, extWeights);
    }

    private static MyfeederProperties properties() {
        MyfeederProperties props = new MyfeederProperties();
        MyfeederProperties.Interest.Blend blend = props.getInterest().getBlend();
        blend.setLearnRate(2);
        blend.setLearnedCap(20);
        blend.setProfilePoints(100);
        blend.getEngagement().setOpenWeight(0.25);
        blend.getEngagement().setSaveWeight(0.5);
        blend.getEngagement().setCap(8);
        return props;
    }

    /** Topics, articles, scores, nouls and votes, inside the rolled-back test transaction. */
    private void seedWithoutEngagement() {
        jdbc.update("DELETE FROM article_score");
        jdbc.update("DELETE FROM article");
        jdbc.update("DELETE FROM interest_topic");
        long feedId = jdbc.queryForObject(
                "INSERT INTO feed (url, title, feed_type) VALUES (?, ?, ?) RETURNING id",
                Long.class, "https://example.com/latency-feed.xml", "Latency Feed", "RSS");

        jdbc.update("INSERT INTO interest_topic (name, description, weight) "
                + "SELECT 'latency-topic-' || i, 'Latency topic ' || i, "
                + "(ARRAY[40, 30, 20, 20, 10, 5, 0, -10, -20, -30])[i] FROM generate_series(1, 10) i");
        jdbc.update("INSERT INTO article (feed_id, guid, title, url, published_at, \"read\") "
                + "SELECT ?, 'latency-' || i, 'Latency article ' || i, 'https://example.com/latency/' || i, "
                + "now() - i * interval '1 minute', i % 10 < 7 FROM generate_series(1, 30000) i", feedId);
        jdbc.update("INSERT INTO article_score (article_id, status, profile_score, profile_max_level, "
                + "profile_version, attempts) SELECT r.id, 'SCORED', r.id % 5, 4, 1, 1 FROM " + RANKED
                + " WHERE r.rn <= 5000");
        jdbc.update("INSERT INTO article_score (article_id, status, attempts, last_error) "
                + "SELECT r.id, 'FAILED', 1, 'latency fixture' FROM " + RANKED
                + " WHERE r.rn > 5000 AND r.rn <= 5500");
        jdbc.update("INSERT INTO article_topic_score (article_id, topic_id, noul, topic_version) "
                + "SELECT s.article_id, t.id, ((s.article_id * 7919 + t.id * 104729) % 1000) / 999.0, 1 "
                + "FROM article_score s CROSS JOIN interest_topic t WHERE s.status = 'SCORED'");
        jdbc.update("INSERT INTO article_feedback (article_id, vote, topics_narrowed) "
                + "SELECT r.id, CASE WHEN r.rn % 4 = 3 THEN -1 ELSE 1 END, r.rn % 380 = 1 FROM " + RANKED
                + " WHERE " + VOTED);
        jdbc.update("INSERT INTO article_feedback_topic (article_id, topic_id) "
                + "SELECT f.article_id, (SELECT min(id) FROM interest_topic) + f.article_id % 10 "
                + "FROM article_feedback f WHERE f.topics_narrowed");
        assertThat(count("SELECT count(*) FROM article_feedback")).isEqualTo(130);
        assertThat(count("SELECT count(*) FROM article_feedback WHERE topics_narrowed")).isEqualTo(13);
        assertThat(count("SELECT count(*) FROM article_topic_score")).isEqualTo(50_000);
        analyze();
    }

    /**
     * About 20,000 engagement rows over about 13,000 articles, scored and unscored, some voted: opens on
     * 2/3 of the candidates, STAR on 1/4, BOARD on 1/5 and RAINDROP on 1/7.
     */
    private void seedEngagement() {
        String[][] kinds = {
                {"OPEN_ORIGINAL", "r.rn % 3 <> 0"},
                {"STAR", "r.rn % 4 = 0"},
                {"BOARD", "r.rn % 5 = 0"},
                {"RAINDROP", "r.rn % 7 = 0"}};
        for (String[] kind : kinds) {
            jdbc.update("INSERT INTO article_engagement (article_id, kind) SELECT r.id, '" + kind[0] + "' FROM "
                    + RANKED + " WHERE " + CHOSEN + " AND " + kind[1]);
        }
        analyze();
    }

    /** An unread SCORED article that gets an open and carries no vote, chosen before engagement exists. */
    private long breakdownArticle() {
        return jdbc.queryForObject("SELECT r.id FROM " + RANKED + " JOIN article a ON a.id = r.id "
                + "WHERE r.rn <= 5000 AND NOT a.\"read\" AND " + CHOSEN + " AND r.rn % 3 <> 0 "
                + "AND NOT (" + VOTED + ") ORDER BY r.rn LIMIT 1", Long.class);
    }

    private void analyze() {
        jdbc.execute("ANALYZE interest_topic, article, article_score, article_topic_score, article_feedback, "
                + "article_feedback_topic, article_engagement");
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    /** The median wall time of {@value #RUNS} runs after {@value #WARM_UPS} warm-ups, in milliseconds. */
    private static double medianMillis(Runnable query) {
        for (int i = 0; i < WARM_UPS; i++) {
            query.run();
        }
        double[] millis = new double[RUNS];
        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            query.run();
            millis[i] = (System.nanoTime() - start) / 1_000_000.0;
        }
        Arrays.sort(millis);
        return millis[RUNS / 2];
    }

    private static double bound(double baseline) {
        return 10 * baseline + 250;
    }

    private static void report(String query, double baseline, double extended) {
        System.out.printf("LATENCY %s baseline=%.1f extended=%.1f bound=%.1f%n",
                query, baseline, extended, bound(baseline));
    }

    private static void assertWithinBudget(String query, double baseline, double extended) {
        assertThat(extended)
                .as("%s: extended median %.1f ms against baseline median %.1f ms (D-17)", query, extended, baseline)
                .isLessThanOrEqualTo(bound(baseline));
    }

    /** Prints EXPLAIN (ANALYZE, BUFFERS) of {@code sql}, bound with the six parameters the blend binds. */
    private void explain(String label, String sql) {
        MyfeederProperties.Interest.Blend blend = properties().getInterest().getBlend();
        List<String> plan = jdbcClient.sql("EXPLAIN (ANALYZE, BUFFERS) " + sql)
                .param("learnRate", blend.getLearnRate())
                .param("learnedCap", blend.getLearnedCap())
                .param("profilePoints", blend.getProfilePoints())
                .param("engagementOpenWeight", blend.getEngagement().getOpenWeight())
                .param("engagementSaveWeight", blend.getEngagement().getSaveWeight())
                .param("engagementCap", blend.getEngagement().getCap())
                .query(String.class)
                .list();
        plan.forEach(line -> System.out.println("EXPLAIN " + label + ": " + line));
    }
}
