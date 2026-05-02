# resources/ (test)

## Files

| File                  | What                                                                                       | When to read                                                                |
| --------------------- | ------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------- |
| `logback-test.xml`    | Test logging config: WARN threshold to suppress test noise                                 | Debugging test log output, adjusting noise                                  |
| `.gitignore`          | Excludes `snes/`, `ucon64`, and dotfiles from git                                          | Adding patterns for test fixtures                                           |
| `ucon64`              | Gitignored ucon64 reference binary used for ad-hoc cross-checks (not committed)            | Comparing splitter output against ucon64                                    |

## Subdirectories

| Directory   | What                                                                                                       | When to read                                                  |
| ----------- | ---------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------- |
| `snes/`     | Gitignored real-ROM fixture set (`*.sfc`) and `synthetic/` ROMs; loaded by `TestRomProvider` when present  | Adding/refreshing real-ROM fixtures (not committed)           |
