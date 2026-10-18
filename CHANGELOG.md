# Changelog

## Unreleased

- Initial release: declares the validation files as inputs and outputs of `Test` tasks and of
  tasks registered with `producedBy`, deletes the output files on `clean`, adds
  `findUnusedValidationFiles` to `check` (or leaves it to be called on its own with
  `mode = MANUAL`), adds `deleteUnusedValidationFiles` and excludes the output files from the
  IntelliJ module.
