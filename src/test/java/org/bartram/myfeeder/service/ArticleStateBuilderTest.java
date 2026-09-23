package org.bartram.myfeeder.service;

import org.bartram.myfeeder.model.Article;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class ArticleStateBuilderTest {

    private static final char ELLIPSIS = '…';

    private static Article article(String title, String summary, String content) {
        Article article = new Article();
        article.setTitle(title);
        article.setSummary(summary);
        article.setContent(content);
        return article;
    }

    private static String summaryOf(String summary) {
        return (String) ArticleStateBuilder.build("Feed", article("T", summary, null)).get("summary");
    }

    @Test
    void buildsFeedTitleSummaryInOrder() {
        Map<String, Object> state = ArticleStateBuilder.build("Feed", article("Title", "Summary", null));

        assertThat(new ArrayList<>(state.keySet())).containsExactly("feed", "title", "summary");
    }

    @Test
    void stripsHtmlDecodesEntitiesAndDropsScripts() {
        Map<String, Object> state = ArticleStateBuilder.build("Feed", article("AT&amp;T",
                "<p>Rust &amp; <b>Go</b></p><script>alert('owned')</script>", null));

        assertThat(state.get("summary")).isEqualTo("Rust & Go");
        assertThat((String) state.get("summary")).doesNotContain("owned");
        assertThat(state.get("title")).isEqualTo("AT&T");
    }

    @Test
    void fallsBackToContentWhenSummaryBlank() {
        assertThat(ArticleStateBuilder.build("Feed", article("T", null, "<div>Body text</div>")).get("summary"))
                .isEqualTo("Body text");
        assertThat(ArticleStateBuilder.build("Feed", article("T", "  ", "<div>Body text</div>")).get("summary"))
                .isEqualTo("Body text");
    }

    @Test
    void omitsBlankFields() {
        assertThat(ArticleStateBuilder.build(null, article("T", "S", null))).doesNotContainKey("feed");
        assertThat(ArticleStateBuilder.build("Feed", article("T", " ", "  "))).doesNotContainKey("summary");

        Map<String, Object> allBlank = ArticleStateBuilder.build("Feed", article(null, null, null));
        assertThat(allBlank).containsExactly(Map.entry("feed", "Feed"));
    }

    @Test
    void textAtLimitIsUnchanged() {
        String text = "x".repeat(ArticleStateBuilder.MAX_SUMMARY_CHARS);

        String summary = summaryOf(text);

        assertThat(summary).isEqualTo(text);
        assertThat(summary).doesNotContain(String.valueOf(ELLIPSIS));
    }

    @Test
    void truncatesAtWhitespaceWithEllipsis() {
        StringBuilder source = new StringBuilder();
        for (int i = 0; source.length() < 3000; i++) {
            source.append("word").append(i).append(' ');
        }
        String text = source.toString().trim();

        String summary = summaryOf(text);

        assertThat(summary.length()).isLessThanOrEqualTo(ArticleStateBuilder.MAX_SUMMARY_CHARS);
        assertThat(summary.charAt(summary.length() - 1)).isEqualTo(ELLIPSIS);
        String kept = summary.substring(0, summary.length() - 1);
        assertThat(Character.isWhitespace(kept.charAt(kept.length() - 1))).isFalse();
        assertThat(text).startsWith(kept);
        assertThat(Character.isWhitespace(text.charAt(kept.length()))).isTrue();
    }

    @Test
    void neverSplitsSurrogatePair() {
        String text = "😀".repeat(2000);

        String summary = summaryOf(text);

        assertThat(summary.length()).isLessThanOrEqualTo(ArticleStateBuilder.MAX_SUMMARY_CHARS);
        assertThat(summary.charAt(summary.length() - 1)).isEqualTo(ELLIPSIS);
        assertThat(Character.isHighSurrogate(summary.charAt(summary.length() - 2))).isFalse();
    }

    @Test
    void boundsRawHtmlBeforeParsing() {
        StringBuilder html = new StringBuilder();
        while (html.length() < 1_000_000) {
            html.append("<b>word</b> ");
        }
        String huge = html.substring(0, 1_000_000);

        String summary = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> summaryOf(huge));

        assertThat(summary.length()).isLessThanOrEqualTo(ArticleStateBuilder.MAX_SUMMARY_CHARS);
    }

    @Test
    void hasJudgeableTextRequiresTitleOrSummary() {
        assertThat(ArticleStateBuilder.hasJudgeableText(Map.of("feed", "F"))).isFalse();
        assertThat(ArticleStateBuilder.hasJudgeableText(Map.of("feed", "F", "title", "T"))).isTrue();
        assertThat(ArticleStateBuilder.hasJudgeableText(Map.of("summary", "S"))).isTrue();
    }
}
