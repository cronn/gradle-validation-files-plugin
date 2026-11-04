package de.cronn.validationfiles

import de.cronn.validationfiles.fixture.GradleProjectFixture
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ValidationFilesPluginFunctionalTest {

  @TempDir lateinit var projectDirectory: File

  private lateinit var fixture: GradleProjectFixture

  @BeforeEach
  fun createFixture() {
    fixture = GradleProjectFixture(projectDirectory)
  }

  @Test
  fun `reruns the tests when a validation file changes`() {
    fixture.writeJavaBuild()
    fixture.writeValidationFile("A.txt")

    assertThat(fixture.runner("test").build().task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(fixture.runner("test").build().task(":test")?.outcome)
      .isEqualTo(TaskOutcome.UP_TO_DATE)

    fixture.writeValidationFile("A.txt", "changed")

    assertThat(fixture.runner("test").build().task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
  }

  @Test
  fun `reruns the tests when an output file was deleted`() {
    fixture.writeJavaBuild()
    fixture.writeValidationFile("A.txt")
    fixture.runner("test").build()

    fixture.file("data/test/output/A.txt").delete()

    assertThat(fixture.runner("test").build().task(":test")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
  }

  @Test
  fun `declares no outputs for a project without validation files`() {
    fixture.writeJavaBuild()

    fixture.runner("test").build()
    fixture.file("data/test/output/A.txt").delete()

    assertThat(fixture.runner("test").build().task(":test")?.outcome)
      .isEqualTo(TaskOutcome.UP_TO_DATE)
    assertThat(fixture.file("data/test/tmp")).doesNotExist()
  }

  @Test
  fun `check fails for a validation file no test wrote`() {
    fixture.writeJavaBuild()
    fixture.writeValidationFile("A.txt")
    fixture.writeValidationFile("sub/B.txt")

    val result = fixture.runner("check").buildAndFail()

    assertThat(result.task(":findUnusedValidationFiles")?.outcome).isEqualTo(TaskOutcome.FAILED)
    assertThat(result.output)
      .contains("Found 1 unused validation file:")
      .contains("• data/test/validation/sub/B.txt")
      .contains("To delete it, run:\n\n    ./gradlew :deleteUnusedValidationFiles\n")
      .doesNotContain("validation/A.txt")
  }

  @Test
  fun `reports unused validation files of a subproject relative to the root project`() {
    fixture.write(
      "settings.gradle.kts",
      """
      rootProject.name = "project"
      include("app")
      """,
    )
    fixture.write(
      "app/build.gradle.kts",
      """plugins {
        base
        id("de.cronn.validation-files")
      }""",
    )
    fixture.write("app/data/test/validation/A.txt", "A")

    val result = fixture.runner(":app:check").buildAndFail()

    assertThat(result.output)
      .contains("• app/data/test/validation/A.txt")
      .contains("./gradlew :app:deleteUnusedValidationFiles")
  }

  @Test
  fun `check passes when every validation file was written`() {
    fixture.writeBuildWithProducer(listOf("A.txt", "sub/B.txt"))
    fixture.writeValidationFile("A.txt")
    fixture.writeValidationFile("sub/B.txt")

    val result = fixture.runner("check").build()

    assertThat(result.task(":produce")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.task(":findUnusedValidationFiles")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
  }

  @Test
  fun `ignores validation files matching an ignored pattern`() {
    fixture.writeBuildWithProducer(
      listOf("A.txt"),
      """
      validationFiles {
        unusedFileDetection {
          ignoredFiles.add("optional/.+")
        }
      }
      """,
    )
    fixture.writeValidationFile("A.txt")
    fixture.writeValidationFile("optional/B.txt")

    val result = fixture.runner("findUnusedValidationFiles").build()

    assertThat(result.task(":findUnusedValidationFiles")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
  }

  @Test
  fun `fails for an ignored pattern that matches no validation file`() {
    fixture.writeBuildWithProducer(
      listOf("A.txt"),
      """
      validationFiles {
        unusedFileDetection {
          ignoredFiles.add("outdated/.+")
        }
      }
      """,
    )
    fixture.writeValidationFile("A.txt")

    val result = fixture.runner("findUnusedValidationFiles").buildAndFail()

    assertThat(result.output)
      .contains("No validation file matches the ignored file pattern 'outdated/.+'")
  }

  @Test
  fun `skips the check when tests are filtered with --tests`() {
    fixture.writeJavaBuild()
    fixture.writeValidationFile("A.txt")
    fixture.writeValidationFile("B.txt")

    val result = fixture.runner("test", "--tests", "ExampleTest.otherTest", "check").build()

    assertThat(result.task(":findUnusedValidationFiles")?.outcome).isEqualTo(TaskOutcome.SKIPPED)
  }

  @Test
  fun `skips the check when skip is set`() {
    fixture.writeBuildWithProducer(
      listOf("A.txt"),
      """
      validationFiles {
        unusedFileDetection {
          skip = providers.gradleProperty("selector").map { it.isNotBlank() }
        }
      }
      """,
    )
    fixture.writeValidationFile("A.txt")
    fixture.writeValidationFile("B.txt")

    fixture.runner("findUnusedValidationFiles").buildAndFail()
    val result = fixture.runner("findUnusedValidationFiles", "-Pselector=login").build()

    assertThat(result.task(":findUnusedValidationFiles")?.outcome).isEqualTo(TaskOutcome.SKIPPED)
  }

  @Test
  fun `neither check nor a test run runs the check in mode MANUAL`() {
    fixture.writeBuildWithProducer(listOf("A.txt"), MANUAL)
    fixture.writeValidationFile("A.txt")
    fixture.writeValidationFile("B.txt")

    val checkResult = fixture.runner("check").build()
    assertThat(checkResult.task(":findUnusedValidationFiles")).isNull()
    assertThat(checkResult.task(":produce")).isNull()

    val produceResult = fixture.runner("produce").build()
    assertThat(produceResult.task(":findUnusedValidationFiles")).isNull()
  }

  @Test
  fun `checks the existing output files without running the tests in mode MANUAL`() {
    fixture.writeBuildWithProducer(listOf("A.txt"), MANUAL)
    fixture.writeValidationFile("A.txt")

    assertThat(fixture.runner("findUnusedValidationFiles").buildAndFail().output)
      .contains("• data/test/validation/A.txt")

    fixture.write("data/test/output/A.txt", "A")
    val result = fixture.runner("findUnusedValidationFiles").build()

    assertThat(result.task(":produce")).isNull()
    assertThat(result.task(":findUnusedValidationFiles")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
  }

  @Test
  fun `runs the check after the tests started in the same build in mode MANUAL`() {
    fixture.writeBuildWithProducer(listOf("A.txt"), MANUAL)
    fixture.writeValidationFile("A.txt")

    val result = fixture.runner("findUnusedValidationFiles", "produce").build()

    assertThat(result.tasks.map { it.path })
      .containsSubsequence(":produce", ":findUnusedValidationFiles")
    assertThat(result.task(":findUnusedValidationFiles")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
  }

  @Test
  fun `deletes the unused validation files and the directories left empty`() {
    fixture.writeBuildWithProducer(
      listOf("A.txt"),
      """
      validationFiles {
        unusedFileDetection {
          ignoredFiles.add("optional/.+")
        }
      }
      """,
    )
    fixture.writeValidationFile("A.txt")
    fixture.writeValidationFile("sub/deeper/B.txt")
    fixture.writeValidationFile("optional/C.txt")

    val result = fixture.runner("deleteUnusedValidationFiles").build()

    assertThat(result.task(":produce")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.output).contains("Deleted data/test/validation/sub/deeper/B.txt")
    assertThat(fixture.file("data/test/validation/A.txt")).exists()
    assertThat(fixture.file("data/test/validation/optional/C.txt")).exists()
    assertThat(fixture.file("data/test/validation/sub")).doesNotExist()
    assertThat(
        fixture
          .runner("findUnusedValidationFiles")
          .build()
          .task(":findUnusedValidationFiles")
          ?.outcome,
      )
      .isEqualTo(TaskOutcome.SUCCESS)
  }

  @Test
  fun `deletes nothing when tests are filtered with --tests`() {
    fixture.writeJavaBuild()
    fixture.writeValidationFile("A.txt")
    fixture.writeValidationFile("B.txt")

    val result =
      fixture
        .runner("test", "--tests", "ExampleTest.otherTest", "deleteUnusedValidationFiles")
        .build()

    assertThat(result.task(":deleteUnusedValidationFiles")?.outcome).isEqualTo(TaskOutcome.SKIPPED)
    assertThat(fixture.file("data/test/validation/A.txt")).exists()
    assertThat(fixture.file("data/test/validation/B.txt")).exists()
  }

  @Test
  fun `clean deletes the output and tmp files but keeps the validation files`() {
    fixture.writeBuild()
    fixture.writeValidationFile("A.txt")
    fixture.write("data/test/output/A.txt", "A")
    fixture.write("data/test/tmp/A.txt", "A")

    fixture.runner("clean").build()

    assertThat(fixture.file("data/test/output")).doesNotExist()
    assertThat(fixture.file("data/test/tmp")).doesNotExist()
    assertThat(fixture.file("data/test/validation/A.txt")).exists()
  }

  @Test
  fun `hooks into check and clean when the build registers them itself`() {
    fixture.writeBuildWithProducer(
      listOf("A.txt"),
      """
      tasks.register("check")
      tasks.register("clean")
      """,
      plugins = "",
    )
    fixture.writeValidationFile("A.txt")
    fixture.writeValidationFile("B.txt")

    assertThat(fixture.runner("check").buildAndFail().output)
      .contains("• data/test/validation/B.txt")
      .doesNotContain("No check task")

    fixture.write("data/test/tmp/A.txt", "A")
    fixture.runner("clean").build()

    assertThat(fixture.file("data/test/output")).doesNotExist()
    assertThat(fixture.file("data/test/tmp")).doesNotExist()
  }

  @Test
  fun `warns about a missing check task and runs the check only when called`() {
    fixture.writeBuildWithProducer(listOf("A.txt"), plugins = "")
    fixture.writeValidationFile("A.txt")

    val result = fixture.runner("findUnusedValidationFiles").build()

    assertThat(result.output)
      .contains(
        "No check task in root project 'project', so findUnusedValidationFiles does not run with check.",
      )
    assertThat(result.task(":produce")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.task(":findUnusedValidationFiles")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
  }

  @Test
  fun `does not warn about a missing check task in mode MANUAL`() {
    fixture.writeBuildWithProducer(listOf("A.txt"), MANUAL, plugins = "")
    fixture.writeValidationFile("A.txt")

    assertThat(fixture.runner("help").build().output).doesNotContain("No check task")
  }

  @Test
  fun `excludes the output and tmp files from the IntelliJ module`() {
    fixture.writeBuild(printIdeaExcludes())

    val result = fixture.runner("printIdeaExcludes").build()

    assertThat(result.output)
      .contains("idea plugin applied: true")
      .contains("excluded: .gradle, build, data/test/output, data/test/tmp")
  }

  @Test
  fun `does not apply the idea plugin when it is disabled`() {
    fixture.writeBuild(printIdeaExcludes())
    fixture.write("gradle.properties", "de.cronn.validation-files.applyIdeaPlugin=false")

    val result = fixture.runner("printIdeaExcludes").build()

    assertThat(result.output).contains("idea plugin applied: false")
  }

  @Test
  fun `excludes the output and tmp files when the idea plugin is applied by the build`() {
    fixture.writeBuild(printIdeaExcludes(), plugins = "idea")
    fixture.write("gradle.properties", "de.cronn.validation-files.applyIdeaPlugin=false")

    val result = fixture.runner("printIdeaExcludes").build()

    assertThat(result.output).contains("excluded: .gradle, build, data/test/output, data/test/tmp")
  }

  private fun printIdeaExcludes(): String =
    """
    tasks.register("printIdeaExcludes") {
      val ideaApplied = project.pluginManager.hasPlugin("idea")
      val excluded =
        project.extensions.findByType<org.gradle.plugins.ide.idea.model.IdeaModel>()
          ?.module?.excludeDirs.orEmpty()
          .map { it.relativeTo(projectDir).invariantSeparatorsPath }.sorted().joinToString()
      doLast {
        println("idea plugin applied: " + ideaApplied)
        println("excluded: " + excluded)
      }
    }
    """

  private companion object {
    val MANUAL =
      """
      validationFiles {
        unusedFileDetection {
          mode = de.cronn.validationfiles.UnusedFileDetectionMode.MANUAL
        }
      }
      """
  }
}
