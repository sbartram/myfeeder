# myfeeder runtime image.
#
# Build the jar on the host first: `./gradlew clean bootJar`. That task compiles
# the React frontend (npmBuild -> processResources) and embeds it in the jar, and
# stamps the axion-release version into build-info. This Dockerfile only packages
# the resulting jar into a slim JRE runtime.
#
# Why a hand-written Dockerfile instead of `bootBuildImage`: Docker 29's containerd
# image store corrupts Paketo buildpack image exports (containerd rejects the pull
# with "wrong diff id calculated on extraction"). Plain `docker build` images are
# unaffected. See CLAUDE.md Gotchas.
FROM eclipse-temurin:25-jre

WORKDIR /app
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser
USER appuser

COPY build/libs/*.jar app.jar

EXPOSE 8080
# JDK_JAVA_OPTIONS (set by the Helm chart) is read automatically by the JVM.
ENTRYPOINT ["java", "-jar", "app.jar"]
