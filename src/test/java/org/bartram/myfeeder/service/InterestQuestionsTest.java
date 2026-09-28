package org.bartram.myfeeder.service;

import org.bartram.myfeeder.model.InterestTopic;
import org.junit.jupiter.api.Test;
import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.question.Question;
import org.springaicommunity.typesafe.question.Score;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterestQuestionsTest {

    private static InterestTopic topic(Long id, String description) {
        InterestTopic t = new InterestTopic();
        t.setId(id);
        t.setName("t" + id);
        t.setDescription(description);
        return t;
    }

    @Test
    void keysAreStable() {
        assertThat(InterestQuestions.topicKey(7)).isEqualTo("topic_7");
        assertThat(InterestQuestions.PROFILE_KEY).isEqualTo("profile");
        assertThat(InterestQuestions.PREVIEW_DRAFT_KEY).isEqualTo("topic_draft");
    }

    @Test
    void profileHasFiveLevelsAndSubjectScopedInstructions() {
        Score profile = InterestQuestions.profile("x");

        assertThat(profile.maxLevel()).isEqualTo(4);
        Map<String, Object> instructions = profile.instructions().asMap();
        assertThat(new ArrayList<>(instructions.keySet())).containsExactly("reader_profile", "question");
        assertThat(instructions.get("reader_profile")).isEqualTo("x");
        assertThat((String) instructions.get("question")).contains("not how important");
    }

    @Test
    void buildersAreValueEqual() {
        assertThat(InterestQuestions.profile("x")).isEqualTo(InterestQuestions.profile("x"));
        assertThat(InterestQuestions.profile("x")).isNotEqualTo(InterestQuestions.profile("y"));
        assertThat(InterestQuestions.topic("Rust")).isEqualTo(InterestQuestions.topic("Rust"));
    }

    @Test
    void topicIsPositiveNoulWithCriteria() {
        Noul topic = InterestQuestions.topic("Rust");

        Map<String, Object> instructions = topic.instructions().asMap();
        assertThat(new ArrayList<>(instructions.keySet())).containsExactly("topic", "question");
        assertThat(instructions.get("topic")).isEqualTo("Rust");
        assertThat(topic.criteria()).isNotNull();
        assertThat(topic.criteria().whenTrue()).isNotNull();
        assertThat(topic.criteria().whenFalse()).isNotNull();
        assertThat(topic.criteria().whenTrue()).isNotEqualTo(topic.criteria().whenFalse());
    }

    @Test
    void topicAsksWhetherTheArticleIsSubstantiallyAboutTheTopic() {
        Noul topic = InterestQuestions.topic("Rust");

        String question = (String) topic.instructions().asMap().get("question");
        assertThat(question).contains("substantially about `topic`").doesNotContain("primarily");
        assertThat(InterestQuestions.TOPIC_WHEN_TRUE).contains("`topic`").doesNotContain("main subject");
        assertThat(InterestQuestions.TOPIC_WHEN_FALSE).contains("`topic`").contains("brief mention");
    }

    @Test
    void topicRejectsBlankDescription() {
        assertThatThrownBy(() -> InterestQuestions.topic(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InterestQuestions.topic("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InterestQuestions.topic("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void profileRejectsBlankText() {
        assertThatThrownBy(() -> InterestQuestions.profile(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InterestQuestions.profile("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void forRubricPutsProfileFirstThenTopicsById() {
        List<InterestTopic> topics = new ArrayList<>(List.of(topic(9L, "Go"), topic(3L, "Rust")));

        Map<String, Question> questions = InterestQuestions.forRubric("I like systems", topics);

        assertThat(new ArrayList<>(questions.keySet())).containsExactly("profile", "topic_3", "topic_9");
        assertThat(topics).extracting(InterestTopic::getId).containsExactly(9L, 3L);
    }

    @Test
    void forRubricOmitsBlankProfile() {
        Map<String, Question> questions = InterestQuestions.forRubric("  ", List.of(topic(3L, "Rust")));

        assertThat(new ArrayList<>(questions.keySet())).containsExactly("topic_3");
    }

    @Test
    void forRubricReturnsEmptyMapForColdStart() {
        assertThat(InterestQuestions.forRubric("  ", List.of())).isEmpty();
    }

    @Test
    void forRubricRejectsTopicWithoutId() {
        assertThatThrownBy(() -> InterestQuestions.forRubric("x", List.of(topic(null, "Rust"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
