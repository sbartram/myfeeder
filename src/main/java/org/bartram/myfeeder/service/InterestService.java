package org.bartram.myfeeder.service;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.model.InterestProfile;
import org.bartram.myfeeder.model.InterestTopic;
import org.bartram.myfeeder.repository.InterestProfileRepository;
import org.bartram.myfeeder.repository.InterestTopicRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * The interest profile and topic rubric. All limits are enforced here (not only in the UI),
 * and every limit message is fixed text that never echoes the submitted value.
 */
@Service
@RequiredArgsConstructor
public class InterestService {
    public static final int MAX_PROFILE_CHARS = 2000;
    public static final int MAX_TOPICS = 25;
    public static final int MIN_WEIGHT = -50;
    public static final int MAX_WEIGHT = 50;
    public static final int DEFAULT_WEIGHT = 20;
    public static final int MAX_NAME_CHARS = 40;
    public static final int MAX_DESCRIPTION_CHARS = 500;

    private static final int PROFILE_ID = 1;

    private final InterestProfileRepository profileRepository;
    private final InterestTopicRepository topicRepository;

    public InterestProfile getProfile() {
        return profileRepository.findById(PROFILE_ID)
                .orElseThrow(() -> new IllegalStateException("interest_profile row 1 is missing"));
    }

    /**
     * Stores the profile text as sent. Length is counted in UTF-16 code units
     * ({@link String#length()}), the same unit as the browser {@code maxLength}. The version
     * increases by 1 only when the text changes; an empty text clears the profile.
     */
    public InterestProfile updateProfile(String text) {
        if (text == null) {
            throw new IllegalArgumentException("profileText is required");
        }
        if (text.length() > MAX_PROFILE_CHARS) {
            throw new IllegalArgumentException("The profile can be at most 2,000 characters");
        }
        InterestProfile profile = getProfile();
        if (!text.equals(profile.getProfileText())) {
            profile.setVersion(profile.getVersion() + 1);
        }
        profile.setProfileText(text);
        profile.setUpdatedAt(Instant.now());
        return profileRepository.save(profile);
    }

    public List<InterestTopic> listTopics() {
        return topicRepository.findAllOrdered();
    }

    /**
     * Creates a topic; a null weight means {@link #DEFAULT_WEIGHT}. The 25-topic cap is a
     * count-then-insert in one transaction; concurrent creates are not serialized (single user).
     */
    @Transactional
    public InterestTopic createTopic(String name, String description, Integer weight) {
        if (topicRepository.count() >= MAX_TOPICS) {
            throw new IllegalArgumentException("A maximum of 25 topics is allowed");
        }
        int effectiveWeight = weight == null ? DEFAULT_WEIGHT : weight;
        validate(name, description, effectiveWeight);
        InterestTopic topic = new InterestTopic();
        topic.setName(name.trim());
        topic.setDescription(description.trim());
        topic.setWeight(effectiveWeight);
        topic.setVersion(1);
        Instant now = Instant.now();
        topic.setCreatedAt(now);
        topic.setUpdatedAt(now);
        return topicRepository.save(topic);
    }

    /**
     * Updates the topic in place (same id, D-08). The version increases by 1 only when the
     * trimmed description changes; name and weight edits never bump it.
     */
    public InterestTopic updateTopic(Long id, String name, String description, Integer weight) {
        InterestTopic topic = topicRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Topic not found: " + id));
        if (weight == null) {
            throw new IllegalArgumentException("weight is required");
        }
        validate(name, description, weight);
        String trimmedDescription = description.trim();
        if (!trimmedDescription.equals(topic.getDescription())) {
            topic.setVersion(topic.getVersion() + 1);
        }
        topic.setName(name.trim());
        topic.setDescription(trimmedDescription);
        topic.setWeight(weight);
        topic.setUpdatedAt(Instant.now());
        return topicRepository.save(topic);
    }

    /** Deletes the topic; the V6 foreign keys cascade to its scores and feedback picks. */
    public void deleteTopic(Long id) {
        topicRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Topic not found: " + id));
        topicRepository.deleteById(id);
    }

    /**
     * The single cold-start predicate (D-05, C3): true only when the profile text is blank
     * after trim AND there are zero topics. The status endpoint (plan 03-04) and the Phase 4
     * SCOR-06 scorer gate call this method and must not reimplement it.
     */
    public boolean isColdStart() {
        String text = profileRepository.findById(PROFILE_ID)
                .map(InterestProfile::getProfileText)
                .orElse("");
        return (text == null || text.isBlank()) && topicRepository.count() == 0;
    }

    /** Fixed-text messages only: never include the submitted value (T-03-10). */
    private static void validate(String name, String description, int weight) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (name.trim().length() > MAX_NAME_CHARS) {
            throw new IllegalArgumentException("name can be at most 40 characters");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description is required");
        }
        if (description.trim().length() > MAX_DESCRIPTION_CHARS) {
            throw new IllegalArgumentException("description can be at most 500 characters");
        }
        if (weight < MIN_WEIGHT || weight > MAX_WEIGHT) {
            throw new IllegalArgumentException("Weight must be a whole number from -50 to +50");
        }
    }
}
