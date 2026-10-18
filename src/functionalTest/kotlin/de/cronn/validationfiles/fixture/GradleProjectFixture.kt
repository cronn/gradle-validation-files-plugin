package de.cronn.validationfiles.fixture

import java.io.File
import org.gradle.testkit.runner.GradleRunner

class GradleProjectFixture(val rootDirectory: File) {

  fun writeBuild(buildScript: String = "", plugins: String = "") {
    write("settings.gradle.kts", """rootProject.name = "project"""")
    write(
      "build.gradle.kts",
      """
      plugins {
        $plugins
        id("de.cronn.validation-files")
      }

      $buildScript
      """,
    )
  }

  /**
   * Writes a build with a `produce` task registered with `validationFiles.producedBy`, which writes
   * the output files [writtenFiles].
   */
  fun writeBuildWithProducer(writtenFiles: List<String>, buildScript: String = "") {
    writeBuild(
      """
      val produce = tasks.register("produce") {
        // A local of the task configuration, because a doLast referencing the script itself is not
        // supported by the configuration cache.
        val outputDirectory = layout.projectDirectory.dir("data/test/output")
        doLast {
          ${writtenFiles.joinToString("\n") { "outputDirectory.file(\"$it\").asFile.apply { parentFile.mkdirs() }.writeText(\"$it\")" }}
        }
      }

      validationFiles {
        producedBy(produce)
      }

      $buildScript
      """,
    )
  }

  /**
   * Writes a Java build whose single JUnit test writes the output file `data/test/output/A.txt`.
   */
  fun writeJavaBuild(buildScript: String = "") {
    writeBuild(
      plugins = "java",
      buildScript =
        """
        repositories { mavenCentral() }

        dependencies {
          testImplementation(platform("org.junit:junit-bom:6.1.3"))
          testImplementation("org.junit.jupiter:junit-jupiter")
          testRuntimeOnly("org.junit.platform:junit-platform-launcher")
        }

        tasks.test { useJUnitPlatform() }

        $buildScript
        """,
    )
    write(
      "src/test/java/ExampleTest.java",
      """
      import java.nio.file.Files;
      import java.nio.file.Path;
      import org.junit.jupiter.api.Test;

      class ExampleTest {
        @Test
        void writesOutput() throws Exception {
          Path output = Path.of("data/test/output/A.txt");
          Files.createDirectories(output.getParent());
          Files.writeString(output, "A");
        }

        @Test
        void otherTest() {}
      }
      """,
    )
  }

  fun writeValidationFile(name: String, content: String = name) {
    write("data/test/validation/$name", content)
  }

  fun write(path: String, content: String) {
    val file = file(path)
    file.parentFile.mkdirs()
    file.writeText(content.trimIndent().trim() + "\n")
  }

  fun file(path: String): File = File(rootDirectory, path)

  fun runner(vararg arguments: String): GradleRunner =
    GradleRunner.create()
      .withProjectDir(rootDirectory)
      .withPluginClasspath()
      .withArguments(
        arguments.toList() +
          listOf(
            "--configuration-cache",
            "-Dorg.gradle.configuration-cache.problems=fail",
            "--stacktrace",
          ),
      )
      .forwardOutput()
}
