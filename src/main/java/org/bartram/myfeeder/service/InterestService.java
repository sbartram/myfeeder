package org.bartram.myfeeder.service;

import lombok.RequiredArgsConstructor;
import org.bartram.myfeeder.model.InterestProfile;
import org.bartram.myfeeder.repository.InterestProfileRepository;
import org.bartram.myfeeder.repository.InterestTopicRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

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
}
