package de.cronn.validationfiles

/** How `findUnusedValidationFiles` is wired into the build. */
public enum class UnusedFileDetectionMode {
  /** Part of `check`, runs all registered tests first. */
  CHECK,

  /**
   * Runs only when called, without running tests: it checks the output of the last run, for example
   * in a CI job after sharded test jobs.
   */
  MANUAL,
}
