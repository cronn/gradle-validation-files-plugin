package de.cronn.validationfiles

import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Produces no output")
public abstract class FindUnusedValidationFiles : UnusedValidationFilesTask() {

  @get:Internal internal abstract val deleteTaskPath: Property<String>

  @TaskAction
  public fun check() {
    val unusedFiles = unusedFiles()
    if (unusedFiles.isNotEmpty()) {
      val validationPath = validationDirectory.get().asFile.relativeTo(rootDirectory)
      throw GradleException(
        """
        |Found ${unusedFiles.size} unused validation file${plural(unusedFiles)}:
        |
        |${unusedFiles.joinToString("\n") { "• ${validationPath.resolve(it).invariantSeparatorsPath}" }}
        |
        |To delete ${if (unusedFiles.size > 1) "them" else "it"}, run:
        |
        |    ./gradlew ${deleteTaskPath.get()}
        |
        |For false positives there are two potential reasons:
        |
        |1) The task that writes the file is not registered with 'validationFiles.producedBy(...)'.
        |
        |2) The file is only written in some runs, for example when specific properties are set.
        |   In this case, add it to 'validationFiles.unusedFileDetection.ignoredFiles'.
        """
          .trimMargin(),
      )
    }
  }
}
