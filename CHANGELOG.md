# Changelog

## 0.2.0

- The plugin no longer applies `lifecycle-base`. It hooks into the `check` and `clean` tasks of the
  project instead, also when the build script registers them itself. A project without these
  tasks needs to apply `base` or register them. The plugin warns about a project without a `check`
  task, unless the mode is `MANUAL`.
- `clean` deletes the output files through the new task `cleanValidationFilesOutput`.

## 0.1.0

- Initial release: declares the validation files as inputs and outputs of `Test` tasks and of
  tasks registered with `producedBy`, deletes the output files on `clean`, adds
  `findUnusedValidationFiles` to `check` (or leaves it to be called on its own with
  `mode = MANUAL`), adds `deleteUnusedValidationFiles` and excludes the output files from the
  IntelliJ module.
