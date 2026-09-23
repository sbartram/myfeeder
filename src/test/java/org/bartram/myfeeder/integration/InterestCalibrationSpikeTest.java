package org.bartram.myfeeder.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.bartram.myfeeder.config.TypeSafeConfig;
import org.bartram.myfeeder.integration.CalibrationStats.Row;
import org.bartram.myfeeder.integration.JevJudgment.JevScore;
import org.bartram.myfeeder.model.Article;
import org.bartram.myfeeder.model.InterestTopic;
import org.bartram.myfeeder.service.ArticleStateBuilder;
import org.bartram.myfeeder.service.InterestQuestions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springaicommunity.typesafe.autoconfigure.TypeSafeAutoConfiguration;
import org.springaicommunity.typesafe.question.Question;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in calibration spike for the interest question wording (plan 03-08, roadmap research flag).
 *
 * <p>Sends exactly the Phase 4 scoring request for each input article, one {@code judge()} call with
 * {@code ArticleStateBuilder.build(feedTitle, article)} as the state and
 * {@code InterestQuestions.forRubric(profile, topics)} as the questions (D-12), then repeats the
 * first three articles once to measure consistency. It writes a report with article titles,
 * {@code topic_<id>} keys and numbers only: never the profile text, the topic descriptions or the key.
 *
 * <p>Input (default {@code $HOME/.cache/myfeeder-phase03/calibration-input.json}, override with
 * {@code JEV_CALIBRATION_INPUT}); keep it out of the repository, it holds personal interests:
 * <pre>
 * { "profile": string,
 *   "topics": [{ "id": number, "name": string, "description": string, "weight": number }],
 *   "articles": [{ "feedTitle": string, "title": string, "summary": string|null,
 *                  "content": string|null, "label": "high"|"low"|null }] }
 * </pre>
 * At least 10 articles are required (10-20 recommended).
 *
 * <p>Report: default {@code $HOME/.cache/myfeeder-phase03/calibration-report.md}, override with
 * {@code JEV_CALIBRATION_OUTPUT}. The same report is printed to stdout.
 *
 * <p>Runs only with {@code JEV_CALIBRATION=true}. The calls are billed to the key's account (about
 * one call per article plus three repeats). Run it with {@code cleanTest}, because Gradle does not
 * treat environment variables as test inputs:
 * <pre>
 * JEV_CALIBRATION=true MYFEEDER_TYPESAFE_API_KEY=... ./gradlew cleanTest test -x npmBuild -x npmInstall \
 *     --tests 'org.bartram.myfeeder.integration.InterestCalibrationSpikeTest' --info
 * </pre>
 * The context runner does not activate the Resilience4j aspects, so each call is a single raw
 * attempt with no retries.
 */
class InterestCalibrationSpikeTest {

    private static final int REPEATS = 3;
    private static final Path CACHE_DIR = Path.of(System.getProperty("user.home"), ".cache", "myfeeder-phase03");

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CalibrationInput(String profile, List<TopicInput> topics, List<ArticleInput> articles) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TopicInput(long id, String name, String description, int weight) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ArticleInput(String feedTitle, String title, String summary, String content, String label) {}

    @Test
    @EnabledIfEnvironmentVariable(named = "JEV_CALIBRATION", matches = "true")
    void calibrateQuestionWording() throws IOException {
        assertThat(StringUtils.hasText(System.getenv("MYFEEDER_TYPESAFE_API_KEY")))
                .as("JEV_CALIBRATION=true needs MYFEEDER_TYPESAFE_API_KEY exported")
                .isTrue();

        Path inputPath = envPath("JEV_CALIBRATION_INPUT", "calibration-input.json");
        Path outputPath = envPath("JEV_CALIBRATION_OUTPUT", "calibration-report.md");

        CalibrationInput input = JsonMapper.builder().build().readValue(inputPath.toFile(), CalibrationInput.class);
        List<ArticleInput> articleInputs = input.articles() == null ? List.of() : input.articles();
        assertThat(articleInputs).as("calibration input needs at least 10 articles").hasSizeGreaterThanOrEqualTo(10);

        List<InterestTopic> topics = new ArrayList<>();
        Map<String, Integer> weightsByKey = new LinkedHashMap<>();
        for (TopicInput t : input.topics() == null ? List.<TopicInput>of() : input.topics()) {
            InterestTopic topic = new InterestTopic();
            topic.setId(t.id());
            topic.setName(t.name());
            topic.setDescription(t.description());
            topic.setWeight(t.weight());
            topics.add(topic);
            weightsByKey.put(InterestQuestions.topicKey(t.id()), t.weight());
        }
        List<String> topicKeys = topics.stream()
                .sorted(Comparator.comparing(InterestTopic::getId))
                .map(t -> InterestQuestions.topicKey(t.getId()))
                .toList();

        Map<String, Question> questions = InterestQuestions.forRubric(input.profile(), topics);
        assertThat(questions).as("calibration input needs a profile or at least one topic").isNotEmpty();

        // Loaded from disk: src/test/resources/application.yaml shadows the main one on the classpath.
        // addLast so ${MYFEEDER_TYPESAFE_API_KEY:} resolves from the real environment.
        String path = "src/main/resources/application.yaml";
        PropertySource<?> mainYaml = new YamlPropertySourceLoader().load(path, new FileSystemResource(path)).getFirst();

        new ApplicationContextRunner()
                .withInitializer(ctx -> ctx.getEnvironment().getPropertySources().addLast(mainYaml))
                .withConfiguration(AutoConfigurations.of(TypeSafeAutoConfiguration.class,
                        RestClientAutoConfiguration.class, HttpClientAutoConfiguration.class,
                        ImperativeHttpClientAutoConfiguration.class))
                .withConfiguration(UserConfigurations.of(TypeSafeConfig.class, JevApiClientImpl.class))
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    JevApiClient client = ctx.getBean(JevApiClient.class);

                    List<Row> rows = new ArrayList<>();
                    String model = null;
                    for (ArticleInput a : articleInputs) {
                        JevJudgment judgment = client.judge(state(a), questions);
                        model = judgment.model();
                        rows.add(toRow(a, judgment, weightsByKey));
                    }

                    List<Row> repeats = new ArrayList<>();
                    for (ArticleInput a : articleInputs.subList(0, REPEATS)) {
                        repeats.add(toRow(a, client.judge(state(a), questions), weightsByKey));
                    }

                    String report = report(model, rows, repeats, topicKeys, weightsByKey);
                    Files.createDirectories(outputPath.toAbsolutePath().getParent());
                    Files.writeString(outputPath, report);
                    System.out.println(report);
                    System.out.println("Calibration report written to " + outputPath);
                });
    }

    private static Path envPath(String variable, String defaultFile) {
        String value = System.getenv(variable);
        return StringUtils.hasText(value) ? Path.of(value) : CACHE_DIR.resolve(defaultFile);
    }

    private static Map<String, Object> state(ArticleInput a) {
        Article article = new Article();
        article.setTitle(a.title());
        article.setSummary(a.summary());
        article.setContent(a.content());
        return ArticleStateBuilder.build(a.feedTitle(), article);
    }

    private static Row toRow(ArticleInput a, JevJudgment judgment, Map<String, Integer> weightsByKey) {
        JevScore profile = judgment.scores().get(InterestQuestions.PROFILE_KEY);
        double value = profile == null ? Double.NaN : profile.value();
        int maxLevel = profile == null ? -1 : profile.maxLevel();
        double normalized = profile == null || maxLevel <= 0 ? Double.NaN : value / maxLevel;
        double confidence = profile == null ? Double.NaN : profile.confidence();
        Map<String, Double> nouls = new LinkedHashMap<>(judgment.nouls());
        double points = CalibrationStats.points(value, maxLevel, nouls, weightsByKey);
        return new Row(a.title(), a.label(), normalized, confidence, nouls, points);
    }

    private static String report(String model, List<Row> rows, List<Row> repeats, List<String> topicKeys,
                                 Map<String, Integer> weightsByKey) {
        String fingerprint = Integer.toHexString(
                Objects.hash(InterestQuestions.profile("x"), InterestQuestions.topic("x")));
        long labelled = rows.stream().filter(r -> r.label() != null).count();

        StringBuilder out = new StringBuilder();
        out.append("# Interest calibration report\n\n");
        out.append("- Date: ").append(LocalDate.now()).append('\n');
        out.append("- Model: ").append(model).append('\n');
        out.append("- Articles: ").append(rows.size()).append(" (labelled: ").append(labelled).append(")\n");
        out.append("- Wording fingerprint: ").append(fingerprint).append('\n');
        out.append("- Topics (key: weight): ");
        out.append(String.join(", ", topicKeys.stream().map(k -> k + ": " + weightsByKey.get(k)).toList()));
        out.append("\n\n");

        out.append("## Articles\n\n| # | Title | Label | Profile (norm) | Confidence |");
        topicKeys.forEach(k -> out.append(' ').append(k).append(" noul / hinge |"));
        out.append(" Points |\n|---|---|---|---|---|");
        topicKeys.forEach(k -> out.append("---|"));
        out.append("---|\n");
        List<Row> ranked = new ArrayList<>(rows);
        ranked.sort(Comparator.comparingDouble(Row::points).reversed());
        int rank = 1;
        for (Row r : ranked) {
            out.append("| ").append(rank++).append(" | ").append(cell(r.title()))
                    .append(" | ").append(r.label() == null ? "" : r.label())
                    .append(" | ").append(num(r.profileNormalized()))
                    .append(" | ").append(num(r.profileConfidence())).append(" |");
            for (String k : topicKeys) {
                Double noul = r.nouls().get(k);
                out.append(' ').append(noul == null ? "-" : num(noul) + " / " + num(CalibrationStats.hinge(noul)))
                        .append(" |");
            }
            out.append(' ').append(fmt(r.points(), "%.1f")).append(" |\n");
        }

        List<Double> normalized = rows.stream().map(Row::profileNormalized).filter(v -> !v.isNaN()).toList();
        List<Double> confidences = rows.stream().map(Row::profileConfidence).filter(v -> !v.isNaN()).toList();
        List<Double> allNouls = rows.stream().flatMap(r -> r.nouls().values().stream()).toList();
        double stddev = CalibrationStats.stddev(normalized);
        double medianConfidence = CalibrationStats.median(confidences);
        double midBand = CalibrationStats.midBandShare(allNouls);
        double agreement = CalibrationStats.labelAgreement(rows);

        List<Row> firsts = rows.subList(0, repeats.size());
        double deltaPoints = CalibrationStats.maxDelta(
                firsts.stream().map(Row::points).toList(), repeats.stream().map(Row::points).toList());
        double deltaProfile = CalibrationStats.maxDelta(
                firsts.stream().map(Row::profileNormalized).toList(),
                repeats.stream().map(Row::profileNormalized).toList());
        double deltaNoul = CalibrationStats.maxDelta(nouls(firsts, topicKeys), nouls(repeats, topicKeys));

        out.append("\n## Summary statistics\n\n");
        out.append("- Stddev of normalized profile score: ").append(num(stddev)).append('\n');
        out.append("- Median profile confidence: ").append(num(medianConfidence)).append('\n');
        out.append("- Share of nouls in [").append(CalibrationStats.MID_BAND_LOW).append(", ")
                .append(CalibrationStats.MID_BAND_HIGH).append("]: ").append(num(midBand))
                .append(" (").append(allNouls.size()).append(" nouls)\n");
        out.append("- High-over-low label agreement (ties count half): ").append(num(agreement)).append('\n');
        out.append("- Max repeat delta over ").append(repeats.size()).append(" articles: points ")
                .append(fmt(deltaPoints, "%.1f")).append(", normalized profile ").append(num(deltaProfile))
                .append(", noul ").append(num(deltaNoul)).append('\n');

        out.append("\n## Thresholds (research A6 heuristics)\n\n");
        out.append(verdict("Stddev of normalized profile >= " + CalibrationStats.MIN_PROFILE_STDDEV,
                stddev, stddev >= CalibrationStats.MIN_PROFILE_STDDEV));
        out.append(verdict("Median profile confidence >= " + CalibrationStats.MIN_MEDIAN_CONFIDENCE,
                medianConfidence, medianConfidence >= CalibrationStats.MIN_MEDIAN_CONFIDENCE));
        out.append(verdict("Mid-band noul share < " + CalibrationStats.MAX_MID_BAND_SHARE,
                midBand, midBand < CalibrationStats.MAX_MID_BAND_SHARE));
        out.append(verdict("Label agreement >= " + CalibrationStats.MIN_LABEL_AGREEMENT,
                agreement, agreement >= CalibrationStats.MIN_LABEL_AGREEMENT));
        return out.toString();
    }

    private static List<Double> nouls(List<Row> rows, List<String> topicKeys) {
        List<Double> values = new ArrayList<>();
        for (Row r : rows) {
            for (String k : topicKeys) {
                values.add(r.nouls().getOrDefault(k, Double.NaN));
            }
        }
        return values;
    }

    private static String verdict(String name, double value, boolean pass) {
        String status = Double.isNaN(value) ? "N/A" : pass ? "PASS" : "FAIL";
        return "- " + status + ": " + name + " (actual " + num(value) + ")\n";
    }

    private static String cell(String text) {
        return text == null ? "" : text.replace("|", "\\|").replaceAll("\\s+", " ").trim();
    }

    private static String num(double value) {
        return fmt(value, "%.3f");
    }

    private static String fmt(double value, String format) {
        return Double.isNaN(value) ? "n/a" : String.format(Locale.ROOT, format, value);
    }
}
