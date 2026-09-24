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
 *
 * <p>Titles and summaries come from untrusted feeds (a prompt-injection surface, research Pitfall 14).
 * They travel only as data in the state object and are never interpolated into question text.
 * Raw HTML is capped at {@link #MAX_RAW_HTML_CHARS} before parsing (jsoup 1.11.2 predates the
 * CVE-2021-37714 fix, Pitfall 4), and the summary is truncated to {@link #MAX_SUMMARY_CHARS}.
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
        putIfText(state, "summary", truncate(body, MAX_SUMMARY_CHARS));
        return state;
    }

    static String toText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String bounded = html.length() > MAX_RAW_HTML_CHARS ? html.substring(0, MAX_RAW_HTML_CHARS) : html;
        // Strips tags, drops script/style bodies, decodes entities and collapses whitespace
        return Jsoup.parse(bounded).text().trim();
    }

    /**
     * Returns the text unchanged when it fits. Otherwise cuts at the last whitespace at or after
     * {@code max / 2} within the first {@code max - 1} characters, so a word-boundary cut always keeps
     * at least half the text (D-13); else hard-cuts at {@code max - 1}, never splitting a surrogate
     * pair. Strips trailing whitespace and appends an ellipsis, so the result is never longer than
     * {@code max}.
     */
    static String truncate(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        int cut = -1;
        for (int i = max - 2; i >= max / 2; i--) {
            if (Character.isWhitespace(text.charAt(i))) {
                cut = i;
                break;
            }
        }
        if (cut <= 0) {
            cut = max - 1;
            if (Character.isHighSurrogate(text.charAt(cut - 1))) {
                cut--;
            }
        }
        return text.substring(0, cut).stripTrailing() + '\u2026';
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
