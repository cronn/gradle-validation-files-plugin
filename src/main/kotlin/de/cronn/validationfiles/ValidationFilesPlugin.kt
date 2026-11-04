package de.cronn.validationfiles

import java.util.concurrent.Callable
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel

public class ValidationFilesPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    val dataDirectory = project.layout.projectDirectory.dir(DATA_DIRECTORY)
    val validationDirectory = dataDirectory.dir("validation")
    val outputDirectory = dataDirectory.dir("output")
    val tmpDirectory = dataDirectory.dir("tmp")

    val extension =
      project.extensions.create("validationFiles", ValidationFilesExtension::class.java)
    val mode = extension.unusedFileDetection.mode.convention(UnusedFileDetectionMode.CHECK)

    // Gradle creates declared output directories before a task runs. Without this check, every
    // project the plugin is applied to through a convention plugin would get empty data/test
    // directories.
    val hasValidationFiles = validationDirectory.asFile.isDirectory
    val declareValidationFiles = { task: Task ->
      if (hasValidationFiles) {
        task.inputs
          .files(validationDirectory)
          .withPropertyName("validationFiles")
          .withPathSensitivity(PathSensitivity.RELATIVE)
        task.outputs.dir(outputDirectory).withPropertyName("validationFilesOutput")
        task.outputs.dir(tmpDirectory).withPropertyName("validationFilesTmp")
      }
    }
    project.tasks.withType(Test::class.java).configureEach(declareValidationFiles)
    extension.producers.all { producer -> producer.configure(declareValidationFiles) }

    val testsFiltered = commandLineFiltersTests(project)
    val configureUnusedFilesTask = { task: UnusedValidationFilesTask ->
      task.group = LifecycleBasePlugin.VERIFICATION_GROUP
      task.validationDirectory.set(validationDirectory)
      task.outputDirectory.set(outputDirectory)
      task.ignoredFiles.set(extension.unusedFileDetection.ignoredFiles)
      val testTasks = Callable { project.tasks.withType(Test::class.java) }
      val producers = Callable { extension.producers.toList() }
      task.mustRunAfter(testTasks, producers)
      task.dependsOn(
        mode.map {
          if (it == UnusedFileDetectionMode.CHECK) listOf(testTasks, producers) else listOf()
        },
      )

      // After a partial run, most validation files have no output file, so the check would fail
      // and the deletion would delete them.
      val skip = extension.unusedFileDetection.skip
      task.onlyIf("no test filter is given with --tests") { !testsFiltered }
      task.onlyIf("validationFiles.unusedFileDetection.skip is not set") { !skip.getOrElse(false) }
    }

    val deleteUnusedValidationFiles =
      project.tasks.register(
        "deleteUnusedValidationFiles",
        DeleteUnusedValidationFiles::class.java,
      ) { task ->
        configureUnusedFilesTask(task)
        task.description = "Deletes the validation files that no test wrote."
      }
    val findUnusedValidationFiles =
      project.tasks.register(
        "findUnusedValidationFiles",
        FindUnusedValidationFiles::class.java,
      ) { task ->
        configureUnusedFilesTask(task)
        task.description = "Fails if a validation file was not written by any test."
        task.deleteTaskPath.set(deleteUnusedValidationFiles.map { it.path })
      }

    val cleanValidationFilesOutput =
      project.tasks.register("cleanValidationFilesOutput", Delete::class.java) { task ->
        task.group = LifecycleBasePlugin.BUILD_GROUP
        task.description = "Deletes the output and tmp files of the validation files."
        task.delete(outputDirectory, tmpDirectory)
      }

    // Matched by name instead of applying lifecycle-base, which would collide with check or clean
    // tasks that a build registers itself.
    project.tasks
      .named { it == LifecycleBasePlugin.CHECK_TASK_NAME }
      .configureEach { check ->
        check.dependsOn(
          mode.map {
            if (it == UnusedFileDetectionMode.CHECK) listOf(findUnusedValidationFiles) else listOf()
          },
        )
      }
    project.tasks
      .named { it == LifecycleBasePlugin.CLEAN_TASK_NAME }
      .configureEach { clean -> clean.dependsOn(cleanValidationFilesOutput) }

    // Without a check task, a check of the whole build skips the project silently.
    project.afterEvaluate {
      if (
        mode.get() == UnusedFileDetectionMode.CHECK &&
          LifecycleBasePlugin.CHECK_TASK_NAME !in project.tasks.names
      ) {
        project.logger.warn(
          "No check task in ${project.displayName}, so findUnusedValidationFiles does not run with " +
            "check. Apply the base plugin, register a check task, or set " +
            "validationFiles.unusedFileDetection.mode to MANUAL.",
        )
      }
    }

    configureIdea(project, listOf(outputDirectory, tmpDirectory))
  }

  private fun commandLineFiltersTests(project: Project): Boolean {
    val filtered =
      project.gradle.startParameter.taskRequests
        .flatMap { it.args }
        .any { it == "--tests" || it.startsWith("--tests=") }
    if (filtered) {
      project.logger.debug("Skipping findUnusedValidationFiles because of --tests")
    }
    return filtered
  }

  private fun configureIdea(project: Project, excludedDirectories: List<Directory>) {
    val applyIdeaPlugin =
      project.providers
        .gradleProperty(APPLY_IDEA_PLUGIN_PROPERTY)
        .map(String::toBoolean)
        .getOrElse(true)
    if (applyIdeaPlugin) {
      project.pluginManager.apply(IdeaPlugin::class.java)
    } else {
      project.logger.debug("Not applying the idea plugin because $APPLY_IDEA_PLUGIN_PROPERTY=false")
    }
    project.pluginManager.withPlugin("idea") {
      project.extensions
        .getByType(IdeaModel::class.java)
        .module
        .excludeDirs
        .addAll(excludedDirectories.map { it.asFile })
    }
  }

  public companion object {
    public const val DATA_DIRECTORY: String = "data/test"

    public const val APPLY_IDEA_PLUGIN_PROPERTY: String =
      "de.cronn.validation-files.applyIdeaPlugin"
  }
}
