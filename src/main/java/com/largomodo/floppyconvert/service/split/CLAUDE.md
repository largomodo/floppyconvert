# split/

Per-format ROM split strategies; mirrors the `HeaderGeneratorFactory` pattern from `snes/header/`.

## Files

| File                        | What                                                                                                                                                                                   | When to read                                                                              |
| --------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------- |
| `README.md`                 | Strategy-to-format mapping table, data flow diagram, design decisions, invisible knowledge (GD3 HiROM ≤16 Mbit force-4Mbit, UFO flag encoding, double-padding hazard)                  | Understanding split architecture decisions, debugging chunk-size or flag mismatches       |
| `SplitStrategy.java`        | Interface: `split(rom, data, headerGen, workDir, baseName, filenameProvider)` → `List<File>`                                                                                           | Adding a new format strategy, understanding the strategy contract                        |
| `SplitStrategyFactory.java` | Maps `(CopierFormat, RomType)` → singleton `SplitStrategy`; constructor-injected into `NativeRomSplitter` via `NativeConversionServiceFactory`                                        | Wiring the factory, adding a new (format, RomType) mapping                               |
| `StandardSplitStrategy.java`| Fixed-size chunk split (4 Mbit default, 8 Mbit for GD3 LoROM); used by FIG, SWC, UFO LoROM, GD3 LoROM; `writeChunk` is package-accessible for reuse by `Ufo`/`Gd3` implementations   | Debugging fixed-size split boundaries, understanding chunk I/O                           |
| `UfoHiRomSplitStrategy.java`| UFO HiROM/ExHiROM: irregular chunk sizes from `UfoHiRomChunker` lookup `{2,4,12,20,32}` Mbit; position-based flags `(0x40/0x10/0x00)`                                                 | Debugging UFO HiROM splits, understanding irregular chunk sequences or flag encoding     |
| `Gd3HiRomSplitStrategy.java`| GD3 HiROM/ExHiROM: interleaves via `SnesInterleaver`, forces 4 Mbit chunks when ROM ≤16 Mbit (X-padding for ucon64 SF-Code naming), skips `padToMbitBoundary`                         | Debugging GD3 HiROM splits, understanding interleave ordering or X-padding threshold     |
| `FilenameProvider.java`     | Functional interface: `provide(workDir, baseName, partIndex, totalParts, rom)` → `File`; lambda constructed by `NativeRomSplitter.createFilename()` per format                        | Understanding filename construction delegation, adding a new naming pattern              |
