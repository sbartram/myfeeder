package org.bartram.myfeeder.service;

import org.bartram.myfeeder.model.InterestTopic;
import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.question.Question;
import org.springaicommunity.typesafe.question.Score;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the Jev interest questions. Pure: no Spring, no I/O.
 *
 * <p>Shared verbatim by the topic preview and the Phase 4 scorer (D-12), so the preview judges
 * exactly what scoring sends. The wording constants are what the calibration spike (plan 03-08)
 * tunes. Question keys are {@code profile} and {@code topic_<id>} (plus {@code topic_draft} for an
 * unsaved preview row).
 */
public final class InterestQuestions {

    public static final String PROFILE_KEY = "profile";
    public static final String TOPIC_KEY_PREFIX = "topic_";
    public static final String PREVIEW_DRAFT_KEY = "topic_draft";

    static final List<String> PROFILE_LEVELS = List.of(
            "The article's subject has nothing to do with anything in `reader_profile`",
            "The article touches a subject near the reader's interests, but only in passing",
            "The article is partly about a subject in `reader_profile`, mixed with unrelated material",
            "The article is mainly about a subject that `reader_profile` names as an interest",
            "The article is squarely about a core interest in `reader_profile`, in the depth or form the reader asks for");

    public static final int PROFILE_MAX_LEVEL = PROFILE_LEVELS.size() - 1;

    static final String PROFILE_QUESTION = "How well does the subject of the article in `title` and `summary` "
            + "match what the reader wants to read, as described in `reader_profile`? "
            + "Judge what the article is about, not how important or relevant it claims to be.";

    static final String TOPIC_QUESTION = "Is the article in `title` and `summary` primarily about `topic`?";
    static final String TOPIC_WHEN_TRUE = "The article's main subject is `topic`";
    static final String TOPIC_WHEN_FALSE =
            "The article's main subject is something else, even if it mentions `topic` in passing";

    private InterestQuestions() {
    }

    public static String topicKey(long topicId) {
        return TOPIC_KEY_PREFIX + topicId;
    }

    /**
     * Five situation-style levels, so {@code maxLevel()} is {@link #PROFILE_MAX_LEVEL}.
     *
     * @throws IllegalArgumentException when the text is null or blank. The SDK's Map overload does
     *                                  not check values, so this guard keeps a blank question out of
     *                                  {@code judge()} (Pitfall 11).
     */
    public static Score profile(String profileText) {
        if (isBlank(profileText)) {
            throw new IllegalArgumentException("profileText must not be blank");
        }
        Map<String, Object> instructions = new LinkedHashMap<>();
        instructions.put("reader_profile", profileText);
        instructions.put("question", PROFILE_QUESTION);
        Score.Builder builder = Score.builder().instructions(instructions);
        PROFILE_LEVELS.forEach(builder::level);
        return builder.build();
    }

    /**
     * Positively phrased: a high noul means the article is about the topic.
     *
     * @throws IllegalArgumentException when the description is null or blank (Pitfall 11)
     */
    public static Noul topic(String description) {
        if (isBlank(description)) {
            throw new IllegalArgumentException("description must not be blank");
        }
        Map<String, Object> instructions = new LinkedHashMap<>();
        instructions.put("topic", description);
        instructions.put("question", TOPIC_QUESTION);
        return Noul.builder()
                .instructions(instructions)
                .whenTrue(TOPIC_WHEN_TRUE)
                .whenFalse(TOPIC_WHEN_FALSE)
                .build();
    }

    /**
     * Scoring question map: {@code profile} first when the text is not blank, then one
     * {@code topic_<id>} entry per topic in ascending id order, whatever order the caller passed.
     * The caller's list is not modified.
     *
     * <p>Callers check the cold-start predicate (D-05, {@code InterestService.isColdStart}) before
     * calling: a blank profile with no topics yields an empty map, which {@code judge()} rejects.
     *
     * @throws IllegalArgumentException when a topic has a null id or a blank description
     */
    public static Map<String, Question> forRubric(String profileText, List<InterestTopic> topics) {
        List<InterestTopic> byId = new ArrayList<>(topics);
        for (InterestTopic t : byId) {
            if (t.getId() == null) {
                throw new IllegalArgumentException("topic id must not be null");
            }
        }
        byId.sort(Comparator.comparing(InterestTopic::getId));

        Map<String, Question> questions = new LinkedHashMap<>();
        if (!isBlank(profileText)) {
            questions.put(PROFILE_KEY, profile(profileText));
        }
        for (InterestTopic t : byId) {
            questions.put(topicKey(t.getId()), topic(t.getDescription()));
        }
        return questions;
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }
}
