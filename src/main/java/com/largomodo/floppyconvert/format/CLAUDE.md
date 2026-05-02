# format/

Copier-format vocabulary shared across all layers; deliberately has no application-package dependencies.

## Files

| File                | What                                                                                                                                              | When to read                                                                                              |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------- |
| `README.md`         | Why `format` was extracted from `core` (circular-dependency break), package invariants (no internal imports), null-handling pattern               | Understanding package-restructuring decisions, reviewing the acyclic-dependency rule                      |
| `CopierFormat.java` | Enum for backup-unit formats (FIG / SWC / UFO / GD3) with ucon64 command flags, `fromFileExtension()` accepting nullable input                    | Adding format support, changing format-specific behavior                                                  |
