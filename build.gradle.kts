import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.plugin.compatibility.compatibility
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

buildscript {
  dependencyLocking { lockAllConfigurations() }
  dependencies {
    // Without this, latest.release also picks release candidates and milestones.
    components.all {
      if (id.version.contains(Regex("(?i).+([-.])(CANDIDATE|RC|BETA|ALPHA|CR\\d+|M\\d+).*"))) {
        status = "milestone"
      }
    }
  }
}

plugins {
  kotlin("jvm") version "latest.release"
  id("com.gradle.plugin-publish") version "latest.release"
  id("com.diffplug.spotless") version "latest.release"
}

description = "Gradle plugin that integrates validation files into Gradle builds"

version = providers.environmentVariable("ARTIFACT_VERSION").getOrElse("0.1.0-SNAPSHOT")

dependencyLocking { lockAllConfigurations() }

java {
  sourceCompatibility = JavaVersion.VERSION_21
  targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
  explicitApi()

  compilerOptions {
    jvmTarget = JvmTarget.JVM_21
    // Kotlin runtime of the oldest supported Gradle (9.0)
    apiVersion = KotlinVersion.KOTLIN_2_2
    languageVersion = KotlinVersion.KOTLIN_2_2
  }
}

dependencies {
  components.all {
    if (id.version.contains(Regex("(?i).+([-.])(CANDIDATE|RC|BETA|ALPHA|CR\\d+|M\\d+).*"))) {
      status = "milestone"
    }
  }

  compileOnly(kotlin("stdlib"))

  testImplementation(gradleApi())
  testImplementation(kotlin("stdlib"))
  testImplementation(platform("org.junit:junit-bom:latest.release"))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testImplementation("org.junit.jupiter:junit-jupiter-params")
  testImplementation("org.assertj:assertj-core:latest.release")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val functionalTest: SourceSet = sourceSets.create("functionalTest")

configurations[functionalTest.implementationConfigurationName].extendsFrom(
  configurations.testImplementation.get(),
)

configurations[functionalTest.runtimeOnlyConfigurationName].extendsFrom(
  configurations.testRuntimeOnly.get(),
)

gradlePlugin {
  website = "https://github.com/cronn/gradle-validation-files-plugin"
  vcsUrl = "https://github.com/cronn/gradle-validation-files-plugin.git"

  testSourceSets(functionalTest)

  plugins {
    register("validationFiles") {
      id = "de.cronn.validation-files"
      implementationClass = "de.cronn.validationfiles.ValidationFilesPlugin"
      displayName = "Validation files plugin"
      description =
        "Integrates validation files (data/test/validation and data/test/output) into the Gradle " +
          "build: up-to-date checks, build cache, cleanup and detection of unused validation files."
      compatibility {
        features {
          configurationCache = true
        }
      }
      tags =
        listOf(
          "testing",
          "snapshot-testing",
          "validation-files",
        )
    }
  }
}

val functionalTestTask =
  tasks.register<Test>("functionalTest") {
    description = "Runs the Gradle TestKit tests."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = functionalTest.output.classesDirs
    classpath = functionalTest.runtimeClasspath
    shouldRunAfter(tasks.test)
  }

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
  testLogging { exceptionFormat = TestExceptionFormat.FULL }
}

tasks.check { dependsOn(functionalTestTask) }

spotless {
  isEnforceCheck = true

  kotlin {
    target("src/*/kotlin/**/*.kt")
    ktfmt().googleStyle()
    trimTrailingWhitespace()
    endWithNewline()
  }

  kotlinGradle {
    target("*.gradle.kts")
    ktfmt().googleStyle()
    trimTrailingWhitespace()
    endWithNewline()
  }

  flexmark {
    target("*.md")
    flexmark()
    trimTrailingWhitespace()
    endWithNewline()
  }

  format("misc") {
    target("*.properties", ".gitignore", ".github/**/*.yml")
    trimTrailingWhitespace()
    endWithNewline()
  }
}

// CAUTION: run ./gradlew wrapper (twice) after changes to this task!
// See https://docs.gradle.org/current/userguide/gradle_wrapper.html#sec:upgrading_wrapper
tasks.wrapper {
  gradleVersion = "9.8.1"
  distributionType = Wrapper.DistributionType.ALL
}
