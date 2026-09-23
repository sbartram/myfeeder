package org.bartram.myfeeder.integration;

import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.autoconfigure.TypeSafeProperties;
import org.springaicommunity.typesafe.question.Question;
import org.springaicommunity.typesafe.response.SystemOneResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class JevApiClientImpl implements JevApiClient {

    private final TypeSafeClient client;
    private final TypeSafeProperties properties;

    public JevApiClientImpl(TypeSafeClient client, TypeSafeProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    private void requireConfigured() {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new JevNotConfiguredException();
        }
    }

    @Override
    public JevJudgment judge(Map<String, ?> state, Map<String, ? extends Question> questions) {
        requireConfigured();
        SystemOneResponse response = client.systemOne(new LinkedHashMap<>(state), questions);
        return JevJudgment.from(response);
    }
}
