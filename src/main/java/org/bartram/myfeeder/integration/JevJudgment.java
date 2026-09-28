package org.bartram.myfeeder.integration;

import org.springaicommunity.typesafe.response.SystemOneResponse;
import org.springaicommunity.typesafe.response.Usage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SDK-free result of one Jev call, consumed by the topic preview, the scorer and the blend.
 * No TypeSafe SDK type crosses the {@link JevApiClient} boundary.
 *
 * <ul>
 *   <li>{@code model} is the model id from the response body, never the configured default.
 *       It is stored with every score.</li>
 *   <li>{@code requestId} is the {@code x-typesafe-request-id} response header; may be null.</li>
 *   <li>{@code nouls} and {@code scores} are unmodifiable and iterate in the response's answer
 *       order. Values are carried exactly as the SDK parsed them.</li>
 *   <li>{@code inputTokens} and {@code outputTokens} are null when the response has no usage.</li>
 *   <li>{@link JevScore#maxLevel()} is the highest legend level, or -1 when the response has no legend.</li>
 * </ul>
 */
public record JevJudgment(String model, String requestId, Map<String, Double> nouls,
                          Map<String, JevScore> scores, Integer inputTokens, Integer outputTokens) {

    public record JevScore(double value, int maxLevel, double confidence) {}

    public static JevJudgment from(SystemOneResponse response) {
        Map<String, Double> nouls = new LinkedHashMap<>();
        response.nouls().forEach((name, answer) -> nouls.put(name, answer.value()));

        Map<String, JevScore> scores = new LinkedHashMap<>();
        response.scores().forEach((name, answer) ->
                scores.put(name, new JevScore(answer.value(), answer.maxLevel(), answer.confidence())));

        Usage usage = response.usage();
        Integer inputTokens = usage == null ? null : usage.inputTokens();
        Integer outputTokens = usage == null ? null : usage.outputTokens();

        return new JevJudgment(response.model(), response.requestId(),
                Collections.unmodifiableMap(nouls), Collections.unmodifiableMap(scores),
                inputTokens, outputTokens);
    }
}
