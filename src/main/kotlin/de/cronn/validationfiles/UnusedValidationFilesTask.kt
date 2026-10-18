package de.cronn.validationfiles

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Produces no output")
public abstract class UnusedValidationFilesTask : DefaultTask() {

  @get:InputFiles
  @get:SkipWhenEmpty
  @get:IgnoreEmptyDirectories
  @get:PathSensitive(PathSensitivity.RELATIVE)
  public abstract val validationDirectory: DirectoryProperty

  @get:InputFiles
  @get:IgnoreEmptyDirectories
  @get:PathSensitive(PathSensitivity.RELATIVE)
  public abstract val outputDirectory: DirectoryProperty

  @get:Input public abstract val ignoredFiles: ListProperty<String>

  // project is not available at execution time with the configuration cache
  @get:Internal internal val rootDirectory: File = project.rootDir

  protected fun unusedFiles(): List<String> {
    val validationFiles = relativePaths(validationDirectory)
    val outputFiles = relativePaths(outputDirectory).toSet()
    val ignoredPatterns = ignoredFiles.get().map(::Regex)

    val unmatchedPatterns = ignoredPatterns.filter { pattern ->
      validationFiles.none(pattern::matches)
    }
    if (unmatchedPatterns.isNotEmpty()) {
      throw GradleException(
        "No validation file matches the ignored file pattern${plural(unmatchedPatterns)} " +
          unmatchedPatterns.joinToString { "'$it'" },
      )
    }

    return validationFiles.filter { file ->
      file !in outputFiles && ignoredPatterns.none { pattern -> pattern.matches(file) }
    }
  }

  protected fun plural(items: Collection<*>): String = if (items.size > 1) "s" else ""

  private fun relativePaths(directory: DirectoryProperty): List<String> {
    val root: File = directory.get().asFile
    return directory.asFileTree.files.map { it.relativeTo(root).invariantSeparatorsPath }.sorted()
  }
}
