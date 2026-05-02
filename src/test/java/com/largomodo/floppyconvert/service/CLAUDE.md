# service/ (tests)

## Files

| File                                  | What                                                                                                                                                                                                | When to read                                                                                  |
| ------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------- |
| `DefaultConversionFacadeTest.java`    | Delegation to `RomSplitter` and `FloppyImageWriter`, parameter passing                                                                                                                              | Verifying facade delegation, testing constructor DI                                            |
| `NativeRomSplitterTest.java`          | Mocked-dependency integration: split-boundary calc, naming for all formats (FIG/SWC `.1`/`.2`, UFO `.1gm`/`.2gm`, GD3 `.078`), conditional GD3 interleaving, GD3 force-split, UFO irregular chunks   | Verifying split logic, format-specific naming, interleaving behavior, GD3 force-split, UFO chunker integration |
| `UfoHiRomChunkerTest.java`            | Property-based: supported sizes (2/4/12/20/32 Mbit), chunk sums, flag sequences (first 0x40/0x10, last always 0x00), 32 Mbit fallback                                                              | Verifying chunk sequences, lookup-table correctness, fallback behavior                        |
| `ConversionServiceFactoryTest.java`   | Verifies `NativeConversionServiceFactory` returns non-null instances and a fresh instance each call                                                                                                  | Verifying factory wiring                                                                       |

## Subdirectories

| Directory | What                                                | When to read                                |
| --------- | --------------------------------------------------- | ------------------------------------------- |
| `fat/`    | Tests for FAT12 image generation/writing            | Verifying BPB, FAT init, cluster allocation |
