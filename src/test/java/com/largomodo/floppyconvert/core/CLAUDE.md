# core/ (tests)

## Files

| File                          | What                                                                                                                       | When to read                                                              |
| ----------------------------- | -------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| `FloppyTypeTest.java`         | Property-based tests: `bestFit()` boundary selection for 720KB, 1.44MB, 1.6MB capacities                                   | Verifying disk-capacity selection, debugging `bestFit` edge cases         |
| `RomPartComparatorTest.java`  | Sort tests: numeric extensions, UFO `.gm` extensions, alphanumeric filenames                                               | Verifying sort behavior for new format or edge cases                      |
| `RomPartNormalizerTest.java`  | Filename sanitization, metadata generation                                                                                  | Verifying sanitization, debugging DOS name generation                     |
| `RomProcessorTest.java`       | Mocked-dependency tests: delegation, exception handling, workspace cleanup                                                  | Debugging orchestration without external tools                            |

## Subdirectories

| Directory    | What                                                                  | When to read                                                |
| ------------ | --------------------------------------------------------------------- | ----------------------------------------------------------- |
| `domain/`    | Tests for `RomPartMetadata`, `DiskLayout`, `GreedyDiskPacker`         | Verifying domain records and bin-packing behavior           |
| `workspace/` | Tests for `ConversionWorkspace`                                       | Debugging cleanup, validating `AutoCloseable` contract      |
