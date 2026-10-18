package de.cronn.validationfiles

import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Deletes files")
public abstract class DeleteUnusedValidationFiles : UnusedValidationFilesTask() {

  @TaskAction
  public fun delete() {
    val validationRoot = validationDirectory.get().asFile
    for (unusedFile in unusedFiles()) {
      val file = validationRoot.resolve(unusedFile)
      file.delete()
      logger.lifecycle("Deleted {}", file.relativeTo(rootDirectory).invariantSeparatorsPath)
      generateSequence(file.parentFile) { it.parentFile }
        .takeWhile { it != validationRoot }
        .takeWhile { it.list()?.isEmpty() == true }
        .forEach { it.delete() }
    }
  }
}
