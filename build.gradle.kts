plugins {
	java
	id("org.springframework.boot") version "4.0.3"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "org.bartram"
version = "0.0.1-SNAPSHOT"
description = "Feed Manager"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	mavenCentral()
}

extra["springAiVersion"] = "2.0.0-M2"
extra["springCloudVersion"] = "2025.1.0"

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-cache")
	implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-restclient")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.ai:spring-ai-starter-model-anthropic")

	implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j")
	implementation("com.rometools:rome:2.1.0")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.flywaydb:flyway-database-postgresql")
	compileOnly("org.projectlombok:lombok")
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")
	runtimeOnly("org.postgresql:postgresql")
	runtimeOnly("io.netty:netty-resolver-dns-classes-macos")
	runtimeOnly("io.netty:netty-resolver-dns-native-macos::osx-aarch_64")
	runtimeOnly("io.netty:netty-resolver-dns-native-macos::osx-x86_64")
	developmentOnly("org.springframework.ai:spring-ai-spring-boot-docker-compose")
	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-cache-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jdbc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-redis-test")
	testImplementation("org.springframework.boot:spring-boot-starter-restclient-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")

	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
		mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
	environment("DOCKER_HOST", System.getenv("DOCKER_HOST") ?: "unix:///Users/scottb/.rd/docker.sock")
	environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", System.getenv("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE") ?: "/var/run/docker.sock")
	environment("TESTCONTAINERS_RYUK_DISABLED", System.getenv("TESTCONTAINERS_RYUK_DISABLED") ?: "true")
}

val npmInstall by tasks.registering(Exec::class) {
	workingDir = file("src/main/frontend")
	commandLine("npm", "install")
	inputs.file("src/main/frontend/package.json")
	inputs.file("src/main/frontend/package-lock.json")
	outputs.dir("src/main/frontend/node_modules")
}

val npmBuild by tasks.registering(Exec::class) {
	dependsOn(npmInstall)
	workingDir = file("src/main/frontend")
	commandLine("npm", "run", "build")
	inputs.dir("src/main/frontend/src")
	inputs.file("src/main/frontend/index.html")
	inputs.file("src/main/frontend/vite.config.ts")
	inputs.file("src/main/frontend/tsconfig.json")
	inputs.file("src/main/frontend/package-lock.json")
	outputs.dir("src/main/resources/static")
}

tasks.named("processResources") {
	dependsOn(npmBuild)
}
