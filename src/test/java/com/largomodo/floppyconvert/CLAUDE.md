# floppyconvert/ (tests)

CLI / E2E / integration tests plus shared test ROM provisioning utilities.

## Files

| File                                  | What                                                                                                                                                                          | When to read                                                                                                |
| ------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| `README.md`                           | ROM provisioning architecture (`TestRomProvider` + `E2ETestRomRegistry`), design rationale, invariants, registry mappings, per-test ROM source                                | Understanding test ROM provisioning, reviewing tradeoffs                                                    |
| `FloppyConvertTest.java`              | Picocli argument-parsing tests: positional parameter, smart output defaults, enum parsing, validation                                                                          | Verifying argument handling, testing CLI behavior                                                           |
| `CopierFormatTest.java`               | Parameterized tests for `CopierFormat`: file-extension conversion, case-insensitivity, invalid-format handling                                                                  | Verifying `CopierFormat` behavior, adding format test cases                                                 |
| `FloppyConvertLoggingTest.java`       | Synthetic-ROM tests for `--verbose`, MDC propagation (`LOGTEST4L.sfc`), failures appender — no real-ROM assumptions                                                            | Verifying logging behavior                                                                                  |
| `FloppyConvertConcurrencyTest.java`   | Synthetic-ROM concurrent batch tests: workspace isolation, observer thread-safety, graceful shutdown (uses `CONCUR4L`)                                                          | Debugging concurrency or thread-pool behavior                                                                |
| `FloppyConvertE2ETest.java`           | Full pipeline E2E across all formats with `TestRomProvider` fallback: GD3 force-split (8/16/20 Mbit HiROM), UFO irregular chunks (12 Mbit HiROM/LoROM), ExHiROM 48 Mbit         | Debugging conversion failures, validating pipeline changes, verifying GD3/UFO behaviors                     |
| `FloppyConvertRecursionTest.java`     | Recursive directory traversal tests with synthetic ROMs (`RECURS4L`): deep nesting, sibling dirs, root-level files, symlink behavior                                            | Debugging structure preservation, validating path mirroring                                                  |
| `E2ETestRomRegistry.java`             | Registry of 8 real-ROM resource paths and their `RomSpec` (`sizeMbit`, `type`, `sramSizeKb`, `hasDsp`, `title`); `getSpec(resourcePath)` lookup                                 | Adding ROM mappings, debugging registry lookup, understanding synthetic fallback specs                       |
| `TestRomProvider.java`                | `getRomOrSynthetic(resourcePath, outputDir)` tries real classpath ROM then falls back to `SyntheticRomFactory`; `getRealRom()` returns `Optional<Path>`; switch over `RomType`  | Debugging E2E ROM acquisition, modifying provisioning behavior                                              |
| `TestRomProviderTest.java`            | Provider unit tests: real ROM exists vs missing, synthetic title from registry, determinism, unknown-path error                                                                | Verifying fallback logic, adding provider test cases                                                        |

## Subdirectories

| Directory   | What                                                                                  | When to read                                                       |
| ----------- | ------------------------------------------------------------------------------------- | ------------------------------------------------------------------ |
| `core/`     | Tests for orchestration layer (`FloppyType`, `RomPartComparator`, `RomProcessor`, ...) | Verifying orchestration logic, packing, workspace cleanup          |
| `service/`  | Tests for service layer (`DefaultConversionFacade`, `NativeRomSplitter`, factory, FAT12 writer/factory, per-format SplitStrategy tests) | Verifying split logic, FAT12 image generation, factory wiring  |
| `snes/`     | Tests for SNES parsing/validation/interleaving and per-format header generation        | Verifying ROM detection, hardware validation, interleaving        |
| `util/`     | Tests for `DosName` value type and `SnesRomMatcher`                                    | Verifying DOS 8.3 name invariant and ROM-extension detection       |
