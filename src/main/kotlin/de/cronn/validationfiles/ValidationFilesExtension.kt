package de.cronn.validationfiles

import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.DomainObjectSet
import org.gradle.api.Task
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.TaskProvider

public abstract class ValidationFilesExtension @Inject constructor(objects: ObjectFactory) {

  @Suppress("UNCHECKED_CAST")
  internal val producers: DomainObjectSet<TaskProvider<out Task>> =
    objects.domainObjectSet(TaskProvider::class.java) as DomainObjectSet<TaskProvider<out Task>>

  @get:Nested public abstract val unusedFileDetection: UnusedFileDetection

  public fun unusedFileDetection(action: Action<in UnusedFileDetection>) {
    action.execute(unusedFileDetection)
  }

  /**
   * Registers tasks besides the `Test` tasks that write validation files, for example a Vitest or
   * Playwright task. They get the same inputs and outputs as the `Test` tasks and run before
   * `findUnusedValidationFiles`.
   */
  public fun producedBy(vararg tasks: TaskProvider<out Task>) {
    producers.addAll(tasks)
  }

  public abstract class UnusedFileDetection {

    /**
     * Regular expressions matching validation files that are only written in some runs, relative to
     * `data/test/validation`. A pattern that matches no validation file fails the check, so that
     * outdated patterns do not pile up.
     */
    public abstract val ignoredFiles: ListProperty<String>

    /**
     * Skips the check, for example when only a part of the tests runs. Runs with `--tests` are
     * skipped regardless.
     */
    public abstract val skip: Property<Boolean>

    /**
     * [UnusedFileDetectionMode.CHECK] by default. [UnusedFileDetectionMode.MANUAL] suits tests that
     * are not part of `check` or run sharded, for example Playwright tests.
     */
    public abstract val mode: Property<UnusedFileDetectionMode>
  }
}
