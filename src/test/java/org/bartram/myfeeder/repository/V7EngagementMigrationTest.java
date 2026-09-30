package org.bartram.myfeeder.repository;

import org.bartram.myfeeder.TestcontainersConfiguration;
import org.bartram.myfeeder.model.EngagementKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the V7 schema shape, constraints and cascades against real Postgres, plus the static guards on
 * the migration file and the ranking SQL. Postgres aborts the test transaction after a constraint error,
 * so each method triggers at most one expected violation, as its last statement.
 */
@DataJdbcTest
@Import(TestcontainersConfiguration.class)
class V7EngagementMigrationTest {

    private static final Path V7 = Path.of("src/main/resources/db/migration/V7__engagement.sql");

    /** Copied from InterestCalibrationReplaySqlTest.WRITE_KEYWORD (the carried-forward keyword decision). */
    private static final Pattern WRITE_KEYWORD = Pattern.compile(
            "(?i)\\b(insert|update|delete|create|drop|alter|truncate|grant|copy|into)\\b");

    @Autowired private JdbcTemplate jdbc;

    private long feedId;

    @BeforeEach
    void setUp() {
        feedId = jdbc.queryForObject(
                "INSERT INTO feed (url, title, feed_type) VALUES (?, ?, ?) RETURNING id",
                Long.class, "https://example.com/v7-feed.xml", "V7 Feed", "RSS");
    }

    @Test
    void engagementTableHasExactlyArticleKindAndCreatedAt() {
        assertThat(columns("article_engagement")).containsExactly("article_id", "kind", "created_at");
    }

    @Test
    void dismissalTableHasNoTopicColumn() {
        assertThat(columns("topic_suggestion_dismissal")).containsExactly("article_id", "reason", "created_at");
    }

    @Test
    void acceptsTheFourKindsForOneArticle() {
        long articleId = insertArticle("g-four");
        for (EngagementKind kind : EngagementKind.values()) {
            insertEngagement(articleId, kind.name());
        }

        assertThat(countWhere("article_engagement", "article_id", articleId)).isEqualTo(4);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM article_engagement WHERE article_id = ? AND created_at IS NOT NULL",
                Integer.class, articleId)).isEqualTo(4);
    }

    @Test
    void rejectsAnUnknownKind() {
        long articleId = insertArticle("g-unknown-kind");
        assertThatThrownBy(() -> insertEngagement(articleId, "COPY_LINK"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsASecondRowOfTheSameKind() {
        long articleId = insertArticle("g-dup-kind");
        insertEngagement(articleId, "STAR");
        assertThatThrownBy(() -> insertEngagement(articleId, "STAR"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingTheArticleCascadesEngagementAndDismissal() {
        long articleId = insertArticle("g-article-delete");
        insertEngagement(articleId, "OPEN_ORIGINAL");
        insertDismissal(articleId, "DISMISSED");

        jdbc.update("DELETE FROM article WHERE id = ?", articleId);

        assertThat(countWhere("article_engagement", "article_id", articleId)).isZero();
        assertThat(countWhere("topic_suggestion_dismissal", "article_id", articleId)).isZero();
    }

    @Test
    void deletingTheFeedCascadesEngagement() {
        long articleId = insertArticle("g-feed-delete");
        insertEngagement(articleId, "RAINDROP");

        jdbc.update("DELETE FROM feed WHERE id = ?", feedId);

        assertThat(countWhere("article_engagement", "article_id", articleId)).isZero();
    }

    @Test
    void deletingABoardOrItsArticleKeepsEngagement() {
        long articleId = insertArticle("g-board");
        long keptBoard = insertBoard("v7-kept");
        long deletedBoard = insertBoard("v7-deleted");
        insertBoardArticle(keptBoard, articleId);
        insertBoardArticle(deletedBoard, articleId);
        insertEngagement(articleId, "BOARD");

        jdbc.update("DELETE FROM board_article WHERE board_id = ? AND article_id = ?", keptBoard, articleId);
        jdbc.update("DELETE FROM board WHERE id = ?", deletedBoard);

        // Nothing references the board table, so board removal and board delete leave engagement (CAPT-05)
        assertThat(countWhere("board_article", "article_id", articleId)).isZero();
        assertThat(countWhere("article_engagement", "article_id", articleId)).isEqualTo(1);
    }

    @Test
    void acceptsDismissedAndTopicCreated() {
        insertDismissal(insertArticle("g-dismissed"), "DISMISSED");
        long created = insertArticle("g-topic-created");
        insertDismissal(created, "TOPIC_CREATED");

        assertThat(countWhere("topic_suggestion_dismissal", "article_id", created)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT created_at IS NOT NULL FROM topic_suggestion_dismissal WHERE article_id = ?",
                Boolean.class, created)).isTrue();
    }

    @Test
    void rejectsAnUnknownReason() {
        long articleId = insertArticle("g-unknown-reason");
        assertThatThrownBy(() -> insertDismissal(articleId, "IGNORED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsASecondDismissalForAnArticle() {
        long articleId = insertArticle("g-dup-dismissal");
        insertDismissal(articleId, "DISMISSED");
        assertThatThrownBy(() -> insertDismissal(articleId, "TOPIC_CREATED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsADismissalForAMissingArticle() {
        assertThatThrownBy(() -> insertDismissal(999_999_999L, "DISMISSED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingATopicKeepsItsDismissal() {
        long articleId = insertArticle("g-topic-dismissal");
        long topicId = insertTopic("V7 Rust");
        insertDismissal(articleId, "TOPIC_CREATED");

        jdbc.update("DELETE FROM interest_topic WHERE id = ?", topicId);

        // D-14: a handled suggestion stays handled, whatever happens to the topic
        assertThat(countWhere("topic_suggestion_dismissal", "article_id", articleId)).isEqualTo(1);
    }

    @Test
    void v7OnlyCreatesTables() throws IOException {
        StringBuilder code = new StringBuilder();
        for (String line : Files.readAllLines(V7)) {
            int comment = line.indexOf("--");
            code.append(comment >= 0 ? line.substring(0, comment) : line).append('\n');
        }
        List<String> statements = Arrays.stream(code.toString().split(";"))
                .map(String::strip)
                .filter(statement -> !statement.isEmpty())
                .toList();

        // No backfill (locked) and no index: every statement is a CREATE TABLE
        assertThat(statements).hasSize(2);
        assertThat(statements).allSatisfy(statement ->
                assertThat(statement).matches("(?is)^create\\s+table\\b.*"));
    }

    @Test
    void namesPassTheReplayWriteKeywordCheck() {
        List<String> names = new ArrayList<>();
        for (EngagementKind kind : EngagementKind.values()) {
            names.add(kind.name());
        }
        names.addAll(List.of("DISMISSED", "TOPIC_CREATED"));
        names.addAll(List.of("article_engagement", "topic_suggestion_dismissal"));
        names.addAll(columns("article_engagement"));
        names.addAll(columns("topic_suggestion_dismissal"));

        assertThat(names).hasSize(14).noneMatch(name -> WRITE_KEYWORD.matcher(name).find());
    }

    /**
     * Phase 9 (LRN-01, CAL-01) feeds engagement into the learned CTE and the calibration replay, so both
     * read {@code article_engagement}. Neither reads {@code topic_suggestion_dismissal}: Phase 11 reads that
     * table from its own service.
     */
    @Test
    void rankingSqlReadsEngagementButNotDismissals() throws IOException {
        String queries = Files.readString(Path.of("src/main/java/org/bartram/myfeeder/repository/InterestScoreQueries.java"));
        String replay = Files.readString(Path.of("scripts/interest-calibration-replay.sql"));

        assertThat(queries).contains("article_engagement");
        assertThat(replay).contains("article_engagement");
        assertThat(queries).doesNotContain("topic_suggestion_dismissal");
        assertThat(replay).doesNotContain("topic_suggestion_dismissal");
    }

    private List<String> columns(String table) {
        return jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = ? ORDER BY ordinal_position",
                String.class, table);
    }

    private long insertArticle(String guid) {
        return jdbc.queryForObject(
                "INSERT INTO article (feed_id, guid, title, url) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, feedId, "v7-" + guid, "Title " + guid, "https://example.com/v7-" + guid);
    }

    private void insertEngagement(long articleId, String kind) {
        jdbc.update("INSERT INTO article_engagement (article_id, kind) VALUES (?, ?)", articleId, kind);
    }

    private void insertDismissal(long articleId, String reason) {
        jdbc.update("INSERT INTO topic_suggestion_dismissal (article_id, reason) VALUES (?, ?)", articleId, reason);
    }

    private long insertBoard(String name) {
        return jdbc.queryForObject("INSERT INTO board (name) VALUES (?) RETURNING id", Long.class, name);
    }

    private void insertBoardArticle(long boardId, long articleId) {
        jdbc.update("INSERT INTO board_article (board_id, article_id) VALUES (?, ?)", boardId, articleId);
    }

    private long insertTopic(String name) {
        return jdbc.queryForObject(
                "INSERT INTO interest_topic (name, description) VALUES (?, ?) RETURNING id",
                Long.class, name, "Articles about " + name);
    }

    private int countWhere(String table, String column, long value) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, value);
    }
}
