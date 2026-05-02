# service/split

Per-format ROM split strategies implementing the SplitStrategy interface.

## Structure

| Class | Strategy | CopierFormat | RomType |
|-------|----------|--------------|---------|
| StandardSplitStrategy | Fixed-size chunks (MBIT_4 default) | FIG, SWC, UFO LoROM | Any |
| StandardSplitStrategy (MBIT_8) | Fixed-size chunks, 8Mbit | GD3 | LoROM |
| UfoHiRomSplitStrategy | Irregular chunks from UfoHiRomChunker lookup | UFO | HiROM, ExHiROM |
| Gd3HiRomSplitStrategy | Interleave + fixed chunks, 4Mbit if <= 16Mbit | GD3 | HiROM, ExHiROM |

SplitStrategyFactory maps (CopierFormat, RomType) -> SplitStrategy, mirroring HeaderGeneratorFactory.

## Data Flow

```
NativeRomSplitter.split()
    prepareData(rom, format)   <- padding only; Gd3HiRom skips padding
    strategyFactory.get(format, rom.type())
    strategy.split(rom, preparedData, headerGen, workDir, baseName, filenameProvider)
        [strategy iterates chunks, calls filenameProvider lambda for each filename]
    return List<File>
```

## Responsibilities

SplitStrategy handles: data preparation specific to its combination (interleaving for GD3 HiROM,
chunk-size selection), chunk iteration, header invocation, file writing via StandardSplitStrategy.writeChunk.

NativeRomSplitter retains: filename construction (createFilename -> FilenameProvider lambda),
ROM reading, hardware validation, logging.

## Design Reference

Mirrors the HeaderGeneratorFactory pattern established in service/snes/header. Factory holds
singleton instances wired at construction to avoid per-call allocations. SnesInterleaver is
injected into SplitStrategyFactory for Gd3HiRomSplitStrategy.

## Invisible Knowledge

- GD3 HiROM <= 16 Mbit forces MBIT_4 chunks to trigger X-padding in SF-Code naming
  (ucon64 copier-naming compatibility). Threshold is 2 * 1024 * 1024 bytes (16 Mbit = 2 MB).
- UFO HiROM chunk flags (0x40/0x10/0x00) are position-based from UfoHiRomChunker, not
  derived from chunk sizes. Deriving from sizes produces wrong flags for the third chunk of
  a 12 Mbit ROM (should be 0x10, not 0x40).
- Gd3HiRomSplitStrategy does not call padToMbitBoundary; SnesInterleaver.mirrorTo8Mbit
  handles alignment during interleaving. Double-padding produces incorrect byte output.
- StandardSplitStrategy.writeChunk is package-accessible so Ufo/Gd3 strategies reuse
  the FileChannel I/O without duplication.
- UFO 2 Mbit is a single-chunk size; its lookup flag is 0x10 (not 0x00). The last-chunk
  flag is taken from the lookup table, not derived from chunk position.
