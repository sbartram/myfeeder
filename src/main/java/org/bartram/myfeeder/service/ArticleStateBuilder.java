package org.bartram.myfeeder.service;

import org.bartram.myfeeder.model.Article;
import org.jsoup.Jsoup;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the Jev article state. Pure: no Spring, no I/O.
 *
 * <p>The state carries factual article text only: {@code feed}, {@code title} and {@code summary},
 * in that order, with blank fields omitted. Evaluative text (the reader profile, the topic
 * descriptions) goes into the questions, never into the state (vendor guidance: keep facts in
 * state, instructions in questions). Shared verbatim by the topic preview and the Phase 4 scorer.
 */
public final class ArticleStateBuilder {

    public static final int MAX_SUMMARY_CHARS = 1500;
    static final int MAX_RAW_HTML_CHARS = 50_000;

    private ArticleStateBuilder() {
    }

    public static Map<String, Object> build(String feedTitle, Article article) {
        Map<String, Object> state = new LinkedHashMap<>();
        putIfText(state, "feed", toText(feedTitle));
        putIfText(state, "title", toText(article.getTitle()));
        String body = toText(article.getSummary());
        if (body.isEmpty()) {
            body = toText(article.getContent()); // SCOR-01 content fallback
        }
        putIfText(state, "summary", body);
        return state;
    }

    static String toText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return Jsoup.parse(html).text().trim();
    }

    public static boolean hasJudgeableText(Map<String, ?> state) {
        return state.containsKey("title") || state.containsKey("summary");
    }

    private static void putIfText(Map<String, Object> state, String key, String value) {
        if (!value.isEmpty()) {
            state.put(key, value);
        }
    }
}
