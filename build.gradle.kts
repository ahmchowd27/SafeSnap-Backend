plugins {
	kotlin("jvm") version "1.9.25"
	kotlin("plugin.spring") version "1.9.25"
	id("org.springframework.boot") version "3.5.3"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("plugin.jpa") version "1.9.25"
	jacoco
}

group = "com.safesnap"
version = "0.0.1-SNAPSHOT"
description = "SafeSnap - Safety Incident Reporting System for Construction and Warehouse Crews"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-web")
	
	// Kotlin support
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	
	// Monitoring with Micrometer
	implementation("io.micrometer:micrometer-registry-prometheus")
	implementation("io.micrometer:micrometer-tracing-bridge-brave")
	
	// API Documentation with OpenAPI/Swagger - Updated for Spring Boot 3.5.3
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.0")
	
	// Rate Limiting
	implementation("com.github.vladimir-bukhtoyarov:bucket4j-core:7.6.0")
	implementation("com.github.vladimir-bukhtoyarov:bucket4j-caffeine:7.6.0")
	implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
	
	// JWT
	implementation("io.jsonwebtoken:jjwt-api:0.11.5")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.5")

	// AWS SDK
	implementation("software.amazon.awssdk:s3:2.21.29")
	implementation("software.amazon.awssdk:auth:2.21.29")

	// Google Cloud Vision API with conflict resolution
	implementation("com.google.cloud:google-cloud-vision:3.20.0") {
		exclude(group = "com.google.guava", module = "listenablefuture")
	}
	implementation("com.google.guava:guava:32.1.3-jre")

	// OpenAI GPT API client
	implementation("com.theokanning.openai-gpt3-java:service:0.18.2")

	// Database
	runtimeOnly("org.postgresql:postgresql")
	testImplementation("com.h2database:h2")
	
	// Development
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	
	// Testing
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("org.springframework.security:spring-security-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testRuntimeOnly("org.junit.vintage:junit-vintage-engine")

	// Cucumber BDD Testing
	testImplementation("io.cucumber:cucumber-java:7.14.0")
	testImplementation("io.cucumber:cucumber-junit:7.14.0")
	testImplementation("io.cucumber:cucumber-spring:7.14.0")

	// Mockito Kotlin (for unit tests)
	testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
	testImplementation("org.mockito:mockito-core:5.6.0")
	// JSON logging (structured logs)
	implementation("net.logstash.logback:logstash-logback-encoder:7.4")
	// Flyway (core + PostgreSQL plugin) pinned to a version that supports Postgres 15.13
	implementation("org.flywaydb:flyway-core:10.17.0")
	implementation("org.flywaydb:flyway-database-postgresql:10.17.0")
}


kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

// Specify main class for Spring Boot
springBoot {
	mainClass.set("com.safesnap.backend.SafeSnapBackendApplicationKt")
}

tasks.withType<Test> {
	useJUnitPlatform()
	// Also support traditional JUnit for Cucumber
	include("**/*Test.class", "**/*Tests.class")
}

tasks.test {
	useJUnitPlatform()
	systemProperty("spring.profiles.active", "test")
	
	// Enable both JUnit Platform and traditional JUnit
	testLogging {
		events("passed", "skipped", "failed")
	}
}

// JaCoCo configuration
jacoco {
	toolVersion = "0.8.11"
}

tasks.jar {
	enabled = false
	archiveClassifier = ""
}

tasks.bootJar {
	enabled = true
	archiveClassifier = ""
	archiveFileName = "safesnap-backend.jar"
	manifest {
		attributes(
			"Implementation-Title" to "SafeSnap Backend API",
			"Implementation-Version" to project.version,
			"Implementation-Vendor" to "SafeSnap Development Team",
			"License" to "MIT",
			"License-File" to "LICENSE"
		)
	}
}

tasks.jacocoTestReport {
	dependsOn(tasks.test)
	reports {
		xml.required.set(true)
		html.required.set(true)
		csv.required.set(false)
	}
	finalizedBy(tasks.jacocoTestCoverageVerification)
}

tasks.jacocoTestCoverageVerification {
	violationRules {
		rule {
			limit {
				minimum = "0.80".toBigDecimal() // 80% minimum coverage
			}
		}
	}
}

configurations {
	jacocoAgent {
		resolutionStrategy.force("org.jacoco:org.jacoco.agent:0.8.12")
	}
	jacocoAnt {
		resolutionStrategy.force("org.jacoco:org.jacoco.ant:0.8.12")
	}
}
