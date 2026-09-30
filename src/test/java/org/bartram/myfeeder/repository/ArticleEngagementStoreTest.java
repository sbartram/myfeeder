package org.bartram.myfeeder.repository;

import org.bartram.myfeeder.TestcontainersConfiguration;
import org.bartram.myfeeder.model.EngagementKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Proves the engagement store's contract against real Postgres: idempotency, kind order, delete count and a
 * quiet failure. Postgres aborts the test transaction after a constraint error, so the one failing statement
 * in a method is its last.
 */
@DataJdbcTest
@Import({TestcontainersConfiguration.class, ArticleEngagementStore.class})
@ExtendWith(OutputCaptureExtension.class)
class ArticleEngagementStoreTest {

    @Autowired private ArticleEngagementStore store;
    @Autowired private JdbcTemplate jdbc;

    private long feedId;

    @BeforeEach
    void setUp() {
        feedId = jdbc.queryForObject(
                "INSERT INTO feed (url, title, feed_type) VALUES (?, ?, ?) RETURNING id",
                Long.class, "https://example.com/engagement-store-feed.xml", "Test Feed", "RSS");
    }

    @Test
    void recordIsIdempotent() {
        long a = insertArticle("a");

        assertThat(store.record(a, EngagementKind.OPEN_ORIGINAL)).isTrue();
        assertThat(store.record(a, EngagementKind.OPEN_ORIGINAL)).isFalse();

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM article_engagement WHERE article_id = ?", Integer.class, a)).isEqualTo(1);
    }

    @Test
    void kindsFollowTheEnumOrder() {
        long a = insertArticle("a");
        store.record(a, EngagementKind.RAINDROP);
        store.record(a, EngagementKind.OPEN_ORIGINAL);
        store.record(a, EngagementKind.BOARD);

        assertThat(store.kinds(a))
                .containsExactly(EngagementKind.OPEN_ORIGINAL, EngagementKind.BOARD, EngagementKind.RAINDROP);
    }

    @Test
    void kindsIsEmptyWithoutEngagement() {
        long a = insertArticle("a");

        assertThat(store.kinds(a)).isEmpty();
    }

    @Test
    void deleteAllReturnsTheRowCount() {
        long a = insertArticle("a");
        store.record(a, EngagementKind.OPEN_ORIGINAL);
        store.record(a, EngagementKind.STAR);

        assertThat(store.deleteAll(a)).isEqualTo(2);
        assertThat(store.deleteAll(a)).isZero();
        assertThat(store.kinds(a)).isEmpty();
    }

    @Test
    void recordQuietlySwallowsAFailureAndLogsTheClassNameOnly(CapturedOutput output) {
        // The FK violation on a nonexistent article is this method's last database statement
        assertThatCode(() -> store.recordQuietly(999_999_999L, EngagementKind.STAR))
                .doesNotThrowAnyException();

        assertThat(output).contains(
                "Engagement STAR not recorded for article 999999999: DataIntegrityViolationException");
        assertThat(output).doesNotContain("violates");
    }

    private long insertArticle(String guid) {
        return jdbc.queryForObject(
                "INSERT INTO article (feed_id, guid, title, url, published_at, read) "
                        + "VALUES (?, ?, ?, ?, ?, false) RETURNING id",
                Long.class, feedId, "engagement-store-" + guid, "Article " + guid,
                "https://example.com/engagement-store-" + guid, Timestamp.from(Instant.parse("2026-09-20T10:00:00Z")));
    }
}
