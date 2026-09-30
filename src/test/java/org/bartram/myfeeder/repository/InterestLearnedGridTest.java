package org.bartram.myfeeder.repository;

import org.assertj.core.api.SoftAssertions;
import org.bartram.myfeeder.TestcontainersConfiguration;
import org.bartram.myfeeder.config.MyfeederProperties;
import org.bartram.myfeeder.repository.InterestScoreQueries.TopicContribution;
import org.bartram.myfeeder.repository.InterestScoreQueries.TopicWeight;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the thumbs/engagement split of the learned model exactly, on real Postgres, across a grid of
 * 14 bases x 13 thumbs values x 8 engagement values (LRN-03, LRN-04, D-09).
 *
 * <p>The grid binds its own constants: learnRate 32, learned-cap 20, open 0.25, save 0.5, cap 8. With
 * {@code noul = 0.5 + k/64} one voted article gives {@code learnedRaw = +/-k}, and with
 * {@code noul = 0.5 + e/32} one saved article gives {@code engagementRaw = e}, both exactly in float8, so
 * every clamp boundary is reachable without rounding noise and a {@link BigDecimal} oracle can be compared
 * with {@code compareTo == 0}. The oracle applies one clamp (sign clamp plus +/-50) to the whole sum.
 */
@DataJdbcTest
@Import(TestcontainersConfiguration.class)
@EnableConfigurationProperties(MyfeederProperties.class)
class InterestLearnedGridTest {

    private static final int[] BASES = {-50, -45, -30, -10, -5, -1, 0, 1, 5, 10, 30, 42, 45, 50};
    private static final int[] THUMBS = {-32, -24, -20, -10, -5, -1, 0, 1, 5, 10, 20, 24, 32};
    private static final String[] ENGAGEMENT = {"0", "0.5", "2", "4", "7.5", "8", "10", "16"};
    private static final int CELLS = BASES.length * THUMBS.length * ENGAGEMENT.length;

    private static final BigDecimal LEARNED_CAP = BigDecimal.valueOf(20);
    private static final BigDecimal ENGAGEMENT_CAP = BigDecimal.valueOf(8);
    private static final BigDecimal FIFTY = BigDecimal.valueOf(50);

    @Autowired private JdbcClient jdbcClient;
    @Autowired private JdbcTemplate jdbc;

    private InterestScoreQueries grid;
    private final List<Cell> cells = new ArrayList<>();
    private final Map<Long, Cell> cellsByTopic = new HashMap<>();

    /** One grid cell: its topic, its oracle, and the article that matched only that topic (voted if any). */
    private record Cell(int base, int thumbs, BigDecimal engagement, long topicId, Long voteArticle,
                        Long engagedArticle) {

        String name() {
            return name(base, thumbs, engagement);
        }

        static String name(int base, int thumbs, BigDecimal engagement) {
            return "grid:" + base + ":" + thumbs + ":" + engagement.toPlainString();
        }

        BigDecimal b() {
            return BigDecimal.valueOf(base);
        }

        BigDecimal thumbsLearned() {
            return BigDecimal.valueOf(thumbs).max(LEARNED_CAP.negate()).min(LEARNED_CAP);
        }

        BigDecimal engagementRaw() {
            return base < 0 ? BigDecimal.ZERO : engagement;
        }

        BigDecimal engagementLearned() {
            return base < 0 ? BigDecimal.ZERO : engagement.min(ENGAGEMENT_CAP);
        }

        BigDecimal thumbsSum() {
            return b().add(thumbsLearned());
        }

        BigDecimal fullSum() {
            return thumbsSum().add(engagementLearned());
        }

        BigDecimal thumbsEffective() {
            return clamp(base, thumbsSum());
        }

        BigDecimal effective() {
            return clamp(base, fullSum());
        }

        /** Any article of this cell, for a breakdown: the voted one when present. */
        Long article() {
            return voteArticle != null ? voteArticle : engagedArticle;
        }
    }

    /** The sign clamp plus the +/-50 range, applied once to the whole sum (D-09). */
    private static BigDecimal clamp(int base, BigDecimal sum) {
        BigDecimal ranged = sum.max(FIFTY.negate()).min(FIFTY);
        if (base > 0) {
            return ranged.max(BigDecimal.ZERO);
        }
        if (base < 0) {
            return ranged.min(BigDecimal.ZERO);
        }
        return ranged;
    }

    @BeforeEach
    void setUp() {
        // Inside the rolled-back test transaction, so the grid is the whole table.
        jdbc.update("DELETE FROM article_score");
        jdbc.update("DELETE FROM article");
        jdbc.update("DELETE FROM interest_topic");

        MyfeederProperties props = new MyfeederProperties();
        MyfeederProperties.Interest.Blend blend = props.getInterest().getBlend();
        blend.setProfilePoints(100);
        blend.setLearnRate(32);
        blend.setLearnedCap(20);
        blend.getEngagement().setOpenWeight(0.25);
        blend.getEngagement().setSaveWeight(0.5);
        blend.getEngagement().setCap(8);
        grid = new InterestScoreQueries(jdbcClient, props);

        seedGrid();
    }

    @Test
    void everyCellMatchesTheExactOracle() {
        List<TopicWeight> weights = grid.allTopicWeights();
        assertThat(weights).hasSize(CELLS);

        SoftAssertions soft = new SoftAssertions();
        for (TopicWeight w : weights) {
            Cell c = cellsByTopic.get(w.topicId());
            String at = c.name();
            soft.assertThat(bd(w.base())).as("base %s", at).isEqualByComparingTo(c.b());
            soft.assertThat(bd(w.learnedRaw())).as("learnedRaw %s", at).isEqualByComparingTo(BigDecimal.valueOf(c.thumbs));
            soft.assertThat(bd(w.thumbsLearned())).as("thumbsLearned %s", at).isEqualByComparingTo(c.thumbsLearned());
            soft.assertThat(bd(w.engagementRaw())).as("engagementRaw %s", at).isEqualByComparingTo(c.engagementRaw());
            soft.assertThat(bd(w.engagementLearned())).as("engagementLearned %s", at)
                    .isEqualByComparingTo(c.engagementLearned());
            soft.assertThat(bd(w.learned())).as("learned %s", at)
                    .isEqualByComparingTo(c.thumbsLearned().add(c.engagementLearned()));
            soft.assertThat(bd(w.learned())).as("learned = thumbsLearned + engagementLearned %s", at)
                    .isEqualByComparingTo(bd(w.thumbsLearned()).add(bd(w.engagementLearned())));
            soft.assertThat(bd(w.thumbsEffective())).as("thumbsEffective %s", at).isEqualByComparingTo(c.thumbsEffective());
            soft.assertThat(bd(w.effective())).as("effective %s", at).isEqualByComparingTo(c.effective());

            BigDecimal engagementApplied = bd(w.effective()).subtract(bd(w.thumbsEffective()));
            soft.assertThat(engagementApplied.signum()).as("engagement applied >= 0 %s", at).isGreaterThanOrEqualTo(0);
            if (c.base < 0) {
                soft.assertThat(engagementApplied.signum()).as("negative base unmoved %s", at).isZero();
                soft.assertThat(w.engagementRaw()).as("negative base engagementRaw %s", at).isZero();
                soft.assertThat(w.engagementLearned()).as("negative base engagementLearned %s", at).isZero();
            }
            soft.assertThat(bd(w.effective()).abs()).as("inside +/-50 %s", at).isLessThanOrEqualTo(FIFTY);
        }
        soft.assertAll();

        assertEveryBranchIsHit(cells);
    }

    @Test
    void workedCellsSplitAsDecided() {
        assertWorkedCell(10, -20, "8", "0", "0", "-10", "0");
        assertWorkedCell(0, -5, "8", "-5", "3", "-5", "8");
        assertWorkedCell(42, 5, "16", "47", "50", "5", "3");
        assertWorkedCell(-5, 10, "16", "0", "0", "5", "0");
        assertWorkedCell(45, 5, "4", "50", "50", "5", "0");
    }

    @Test
    void breakdownSplitEqualsTopicWeightSplit() {
        List<Cell> sample = cells.stream()
                .filter(c -> List.of(-45, -5, 0, 10, 45).contains(c.base))
                .filter(c -> List.of(-24, -5, 24).contains(c.thumbs))
                .filter(c -> c.engagement.signum() == 0 || c.engagement.compareTo(BigDecimal.valueOf(16)) == 0)
                .toList();
        assertThat(sample).hasSizeGreaterThanOrEqualTo(25);
        assertEveryBranchIsHit(sample);

        Map<Long, TopicWeight> weights = grid.topicWeights(sample.stream().map(Cell::topicId).toList());
        SoftAssertions soft = new SoftAssertions();
        for (Cell c : sample) {
            String at = c.name();
            TopicWeight w = weights.get(c.topicId());
            TopicContribution t = onlyTopic(c);
            BigDecimal base = bd(t.baseWeight());
            BigDecimal thumbs = bd(t.thumbsWeight());
            BigDecimal engagement = bd(t.engagementWeight());
            soft.assertThat(base.add(thumbs).add(engagement)).as("base + thumbs + engagement = weight %s", at)
                    .isEqualByComparingTo(bd(t.weight()));
            soft.assertThat(thumbs.add(engagement)).as("learned = thumbs + engagement %s", at)
                    .isEqualByComparingTo(bd(t.learnedWeight()));
            soft.assertThat(thumbs).as("thumbs = thumbsEffective - base %s", at)
                    .isEqualByComparingTo(bd(w.thumbsEffective()).subtract(bd(w.base())));
            soft.assertThat(engagement).as("engagement = effective - thumbsEffective %s", at)
                    .isEqualByComparingTo(bd(w.effective()).subtract(bd(w.thumbsEffective())));
            soft.assertThat(bd(t.weight())).as("weight %s", at).isEqualByComparingTo(c.effective());
        }
        soft.assertAll();
    }

    private void assertWorkedCell(int base, int thumbs, String engagement, String thumbsEffective, String effective,
                                  String thumbsWeight, String engagementWeight) {
        Cell c = cells.stream()
                .filter(x -> x.name().equals(Cell.name(base, thumbs, new BigDecimal(engagement))))
                .findFirst().orElseThrow();
        TopicWeight w = grid.topicWeights(List.of(c.topicId())).get(c.topicId());
        String at = c.name();
        assertThat(bd(w.thumbsEffective())).as("thumbsEffective %s", at).isEqualByComparingTo(thumbsEffective);
        assertThat(bd(w.effective())).as("effective %s", at).isEqualByComparingTo(effective);

        TopicContribution t = onlyTopic(c);
        assertThat(bd(t.baseWeight())).as("baseWeight %s", at).isEqualByComparingTo(BigDecimal.valueOf(base));
        assertThat(bd(t.thumbsWeight())).as("thumbsWeight %s", at).isEqualByComparingTo(thumbsWeight);
        assertThat(bd(t.engagementWeight())).as("engagementWeight %s", at).isEqualByComparingTo(engagementWeight);
        assertThat(bd(t.weight())).as("weight %s", at).isEqualByComparingTo(effective);
    }

    /** The single judged topic of the cell's article (each grid article matched only its own topic). */
    private TopicContribution onlyTopic(Cell c) {
        List<TopicContribution> topics = grid.breakdownInputs(c.article()).orElseThrow().topics();
        assertThat(topics).as("topics of %s", c.name()).hasSize(1);
        assertThat(topics.getFirst().topicId()).isEqualTo(c.topicId());
        return topics.getFirst();
    }

    /** Every clamp branch (both caps, the sign clamp for both signs, +50 and -50) is hit at least once. */
    private static void assertEveryBranchIsHit(List<Cell> set) {
        SoftAssertions soft = new SoftAssertions();
        soft.assertThat(count(set, c -> Math.abs(c.thumbs) > 20)).as("thumbs cap binds").isPositive();
        soft.assertThat(count(set, c -> c.base >= 0 && c.engagement.compareTo(ENGAGEMENT_CAP) > 0))
                .as("engagement cap binds").isPositive();
        soft.assertThat(count(set, c -> c.base > 0 && c.fullSum().signum() < 0))
                .as("positive base sign-clamped at 0").isPositive();
        soft.assertThat(count(set, c -> c.base < 0 && c.fullSum().signum() > 0))
                .as("negative base sign-clamped at 0").isPositive();
        soft.assertThat(count(set, c -> c.fullSum().compareTo(FIFTY) > 0)).as("+50 range binds").isPositive();
        soft.assertThat(count(set, c -> c.fullSum().compareTo(FIFTY.negate()) < 0)).as("-50 range binds").isPositive();
        soft.assertThat(count(set, c -> c.effective().compareTo(FIFTY) == 0)).as("effective exactly +50").isPositive();
        soft.assertThat(count(set, c -> c.effective().compareTo(FIFTY.negate()) == 0)).as("effective exactly -50").isPositive();
        soft.assertThat(count(set, c -> c.effective().signum() == 0 && c.base != 0)).as("effective clamped to 0").isPositive();
        soft.assertAll();
    }

    private static long count(List<Cell> set, Predicate<Cell> branch) {
        return set.stream().filter(branch).count();
    }

    /** Recovers the 6-decimal value SQL returned: each double has at most 9 significant digits here. */
    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }

    /**
     * Seeds one topic per cell (weight = base). A cell with thumbs t != 0 gets a SCORED article matching
     * only its topic at noul 0.5 + |t|/64 with an un-narrowed vote of sign(t); a cell with engagement e > 0
     * gets a separate SCORED article matching only its topic at noul 0.5 + e/32 carrying all four
     * engagement kinds, which collapse to one save.
     */
    private void seedGrid() {
        long feedId = jdbc.queryForObject(
                "INSERT INTO feed (url, title, feed_type) VALUES (?, ?, ?) RETURNING id",
                Long.class, "https://example.com/grid-feed.xml", "Grid Feed", "RSS");

        List<Object[]> topics = new ArrayList<>();
        for (int b : BASES) {
            for (int t : THUMBS) {
                for (String e : ENGAGEMENT) {
                    topics.add(new Object[] {Cell.name(b, t, new BigDecimal(e)), "Grid cell", b});
                }
            }
        }
        jdbc.batchUpdate("INSERT INTO interest_topic (name, description, weight) VALUES (?, ?, ?)", topics);
        Map<String, Long> topicIds = new HashMap<>();
        jdbc.query("SELECT id, name FROM interest_topic", rs -> {
            topicIds.put(rs.getString("name"), rs.getLong("id"));
        });

        List<Object[]> articles = new ArrayList<>();
        for (Object[] topic : topics) {
            String name = (String) topic[0];
            String[] parts = name.split(":");
            if (Integer.parseInt(parts[2]) != 0) {
                articles.add(article(feedId, "v:" + name));
            }
            if (new BigDecimal(parts[3]).signum() > 0) {
                articles.add(article(feedId, "e:" + name));
            }
        }
        jdbc.batchUpdate("INSERT INTO article (feed_id, guid, title, url) VALUES (?, ?, ?, ?)", articles);
        Map<String, Long> articleIds = new HashMap<>();
        jdbc.query("SELECT id, guid FROM article WHERE feed_id = ?", rs -> {
            articleIds.put(rs.getString("guid"), rs.getLong("id"));
        }, feedId);

        List<Object[]> scores = new ArrayList<>();
        List<Object[]> topicScores = new ArrayList<>();
        List<Object[]> votes = new ArrayList<>();
        List<Object[]> engagement = new ArrayList<>();
        for (int b : BASES) {
            for (int t : THUMBS) {
                for (String e : ENGAGEMENT) {
                    BigDecimal eng = new BigDecimal(e);
                    String name = Cell.name(b, t, eng);
                    long topicId = topicIds.get(name);
                    Long voteArticle = articleIds.get("v:" + name);
                    Long engagedArticle = articleIds.get("e:" + name);
                    if (voteArticle != null) {
                        scores.add(new Object[] {voteArticle});
                        topicScores.add(new Object[] {voteArticle, topicId, 0.5 + Math.abs(t) / 64.0});
                        votes.add(new Object[] {voteArticle, Integer.signum(t)});
                    }
                    if (engagedArticle != null) {
                        scores.add(new Object[] {engagedArticle});
                        topicScores.add(new Object[] {engagedArticle, topicId, 0.5 + eng.doubleValue() / 32.0});
                        for (String kind : List.of("OPEN_ORIGINAL", "STAR", "BOARD", "RAINDROP")) {
                            engagement.add(new Object[] {engagedArticle, kind});
                        }
                    }
                    Cell cell = new Cell(b, t, eng, topicId, voteArticle, engagedArticle);
                    cells.add(cell);
                    cellsByTopic.put(topicId, cell);
                }
            }
        }
        jdbc.batchUpdate("INSERT INTO article_score (article_id, status, attempts) VALUES (?, 'SCORED', 1)", scores);
        jdbc.batchUpdate("INSERT INTO article_topic_score (article_id, topic_id, noul, topic_version) "
                + "VALUES (?, ?, ?, 1)", topicScores);
        jdbc.batchUpdate("INSERT INTO article_feedback (article_id, vote, topics_narrowed) VALUES (?, ?, false)", votes);
        jdbc.batchUpdate("INSERT INTO article_engagement (article_id, kind) VALUES (?, ?)", engagement);
    }

    private static Object[] article(long feedId, String guid) {
        return new Object[] {feedId, guid, "Title " + guid, "https://example.com/" + guid};
    }
}
