package org.bartram.myfeeder.service;

import org.bartram.myfeeder.model.InterestProfile;
import org.bartram.myfeeder.model.InterestTopic;
import org.bartram.myfeeder.repository.InterestProfileRepository;
import org.bartram.myfeeder.repository.InterestTopicRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InterestServiceTest {
    @Mock private InterestProfileRepository profileRepository;
    @Mock private InterestTopicRepository topicRepository;
    @InjectMocks private InterestService interestService;

    private InterestProfile profile(String text, int version) {
        InterestProfile p = new InterestProfile();
        p.setId(1);
        p.setProfileText(text);
        p.setVersion(version);
        return p;
    }

    private InterestTopic topic(Long id, String name, String description, int weight, int version) {
        InterestTopic t = new InterestTopic();
        t.setId(id);
        t.setName(name);
        t.setDescription(description);
        t.setWeight(weight);
        t.setVersion(version);
        return t;
    }

    private void stubProfile(String text, int version) {
        when(profileRepository.findById(1)).thenReturn(Optional.of(profile(text, version)));
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubTopicSave() {
        when(topicRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // --- profile (INT-01) ---

    @Test
    void updateProfileAcceptsExactly2000Characters() {
        stubProfile("", 1);
        String text = "a".repeat(2000);
        assertThat(interestService.updateProfile(text).getProfileText()).isEqualTo(text);
    }

    @Test
    void updateProfileRejects2001Characters() {
        assertThatThrownBy(() -> interestService.updateProfile("a".repeat(2001)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The profile can be at most 2,000 characters");
        verify(profileRepository, never()).save(any());
    }

    @Test
    void updateProfileCountsUtf16Units() {
        stubProfile("", 1);
        String emoji = "😀".repeat(1000); // 1,000 emoji = 2,000 UTF-16 units
        assertThat(interestService.updateProfile(emoji).getProfileText()).isEqualTo(emoji);
        assertThatThrownBy(() -> interestService.updateProfile(emoji + "a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The profile can be at most 2,000 characters");
    }

    @Test
    void updateProfileRejectsNull() {
        assertThatThrownBy(() -> interestService.updateProfile(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("profileText is required");
    }

    @Test
    void updateProfileBumpsVersionOnlyWhenTextChanges() {
        InterestProfile stored = profile("a", 1);
        when(profileRepository.findById(1)).thenReturn(Optional.of(stored));
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(interestService.updateProfile("b").getVersion()).isEqualTo(2);
        assertThat(interestService.updateProfile("b").getVersion()).isEqualTo(2);
        InterestProfile cleared = interestService.updateProfile("");
        assertThat(cleared.getVersion()).isEqualTo(3);
        assertThat(cleared.getProfileText()).isEmpty();
    }

    // --- topics (INT-02) ---

    @Test
    void createTopicDefaultsWeightTo20AndVersionTo1() {
        when(topicRepository.count()).thenReturn(0L);
        stubTopicSave();

        InterestTopic t = interestService.createTopic("  Rust  ", "  The Rust programming language ", null);

        assertThat(t.getWeight()).isEqualTo(20);
        assertThat(t.getVersion()).isEqualTo(1);
        assertThat(t.getCreatedAt()).isNotNull();
        assertThat(t.getUpdatedAt()).isNotNull();
        assertThat(t.getName()).isEqualTo("Rust");
        assertThat(t.getDescription()).isEqualTo("The Rust programming language");
    }

    @Test
    void createTopicRejectsTwentySixth() {
        when(topicRepository.count()).thenReturn(25L, 24L);

        assertThatThrownBy(() -> interestService.createTopic("Rust", "Rust lang", 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A maximum of 25 topics is allowed");
        verify(topicRepository, never()).save(any());

        stubTopicSave();
        assertThat(interestService.createTopic("Rust", "Rust lang", 10)).isNotNull();
        verify(topicRepository).save(any());
    }

    @Test
    void createTopicAcceptsWeightBounds() {
        when(topicRepository.count()).thenReturn(0L);
        stubTopicSave();

        assertThat(interestService.createTopic("Low", "Low weight", -50).getWeight()).isEqualTo(-50);
        assertThat(interestService.createTopic("High", "High weight", 50).getWeight()).isEqualTo(50);
    }

    @Test
    void createTopicRejectsWeightOutsideBounds() {
        when(topicRepository.count()).thenReturn(0L);

        for (int weight : new int[]{-51, 51}) {
            assertThatThrownBy(() -> interestService.createTopic("Rust", "Rust lang", weight))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Weight must be a whole number from -50 to +50");
        }
        verify(topicRepository, never()).save(any());
    }

    @Test
    void createTopicRejectsBlankOrLongNameAndDescription() {
        when(topicRepository.count()).thenReturn(0L);

        assertThatThrownBy(() -> interestService.createTopic("   ", "Rust lang", 10))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("name is required");
        assertThatThrownBy(() -> interestService.createTopic(null, "Rust lang", 10))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("name is required");
        assertThatThrownBy(() -> interestService.createTopic("n".repeat(41), "Rust lang", 10))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("name can be at most 40 characters");
        assertThatThrownBy(() -> interestService.createTopic("Rust", "  ", 10))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("description is required");
        assertThatThrownBy(() -> interestService.createTopic("Rust", null, 10))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("description is required");
        assertThatThrownBy(() -> interestService.createTopic("Rust", "d".repeat(501), 10))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("description can be at most 500 characters");
        verify(topicRepository, never()).save(any());
    }

    @Test
    void updateTopicBumpsVersionOnlyOnDescriptionChange() {
        InterestTopic stored = topic(7L, "Rust", "Rust lang", 20, 1);
        when(topicRepository.findById(7L)).thenReturn(Optional.of(stored));
        stubTopicSave();

        assertThat(interestService.updateTopic(7L, "Rustlang", "Rust lang", 20).getVersion()).isEqualTo(1);
        assertThat(interestService.updateTopic(7L, "Rustlang", "Rust lang", -15).getVersion()).isEqualTo(1);
        assertThat(interestService.updateTopic(7L, "Rustlang", "  Rust lang  ", -15).getVersion()).isEqualTo(1);
        InterestTopic changed = interestService.updateTopic(7L, "Rustlang", "The Rust language", -15);
        assertThat(changed.getVersion()).isEqualTo(2);
        assertThat(changed.getDescription()).isEqualTo("The Rust language");
        assertThat(changed.getName()).isEqualTo("Rustlang");
        assertThat(changed.getWeight()).isEqualTo(-15);
    }

    @Test
    void updateTopicKeepsId() {
        InterestTopic stored = topic(7L, "Rust", "Rust lang", 20, 1);
        when(topicRepository.findById(7L)).thenReturn(Optional.of(stored));
        stubTopicSave();

        InterestTopic saved = interestService.updateTopic(7L, "Rust", "New description", 20);

        assertThat(saved.getId()).isEqualTo(7L);
        verify(topicRepository).save(stored);
    }

    @Test
    void updateTopicRejectsMissingWeight() {
        when(topicRepository.findById(7L)).thenReturn(Optional.of(topic(7L, "Rust", "Rust lang", 20, 1)));

        assertThatThrownBy(() -> interestService.updateTopic(7L, "Rust", "Rust lang", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("weight is required");
        verify(topicRepository, never()).save(any());
    }

    @Test
    void updateAndDeleteMissingTopicThrowNotFound() {
        when(topicRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> interestService.updateTopic(99L, "Rust", "Rust lang", 10))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> interestService.deleteTopic(99L))
                .isInstanceOf(NotFoundException.class);
        verify(topicRepository, never()).deleteById(any());
        verify(topicRepository, never()).save(any());
    }

    @Test
    void listTopicsUsesIdOrder() {
        List<InterestTopic> ordered = List.of(topic(1L, "A", "a", 20, 1), topic(2L, "B", "b", 20, 1));
        when(topicRepository.findAllOrdered()).thenReturn(ordered);

        assertThat(interestService.listTopics()).isSameAs(ordered);
        verify(topicRepository).findAllOrdered();
    }

    // --- cold start (D-05, INT-06) ---

    @Test
    void isColdStartTrueOnlyForBlankProfileAndNoTopics() {
        assertThat(coldStart("   ", 0)).isTrue();
        assertThat(coldStart("", 1)).isFalse();
        assertThat(coldStart("Rust", 0)).isFalse();
        assertThat(coldStart("Rust", 2)).isFalse();
    }

    private boolean coldStart(String text, long topics) {
        lenient().when(profileRepository.findById(1)).thenReturn(Optional.of(profile(text, 1)));
        lenient().when(topicRepository.count()).thenReturn(topics);
        return interestService.isColdStart();
    }

    // --- prohibition: messages never echo input (T-03-10) ---

    @Test
    void validationMessagesNeverEchoInput() {
        String secretProfile = "SECRET-TEXT".repeat(200).substring(0, 2001);
        assertThatThrownBy(() -> interestService.updateProfile(secretProfile))
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("SECRET"));

        when(topicRepository.count()).thenReturn(0L);
        String secretName = "SECRET".repeat(7).substring(0, 41);
        assertThatThrownBy(() -> interestService.createTopic(secretName, "Rust lang", 10))
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("SECRET"));
    }
}
