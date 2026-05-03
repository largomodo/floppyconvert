# split/ (tests)

Per-format `SplitStrategy` tests and the factory wiring matrix.

## Files

| File                              | What                                                                                                              | When to read                                                                    |
| --------------------------------- | ----------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------- |
| `StandardSplitStrategyTest.java`  | Chunk count (2/4/8/16/32 Mbit parameterized), flag sequencing (`allChunksExceptLastHaveFlag0x40`), byte-level chunk content | Verifying chunk boundaries or flag sequences for FIG/SWC/UFO LoROM/GD3 LoROM |
| `Gd3HiRomSplitStrategyTest.java`  | HiROM threshold dispatch (`<=16 Mbit` forces 4 Mbit chunks, `>16 Mbit` uses 8 Mbit), byte-level interleave verification | Verifying GD3 HiROM chunk sizing or interleave correctness                   |
| `UfoHiRomSplitStrategyTest.java`  | UFO HiROM irregular chunk sequences and flag layout                                                               | Verifying UFO HiROM chunking                                                    |
| `SplitStrategyFactoryTest.java`   | Exhaustive 12-cell `strategyMatrix`: `(CopierFormat, RomType)` -> strategy class for all combinations            | Verifying factory dispatch wiring, auditing coverage when removing NativeRomSplitterTest methods |

## Architecture

The `SplitStrategy` boundary divides the splitter into two layers.

**Above the boundary** -- `NativeRomSplitter` owns:
- Filename construction (FIG/UFO/GD3 SF-Code, X-padding, sanitization)
- Padding (`padToMbitBoundary`, `padToUfoHiRomBoundary`)
- Validator wiring, IOException propagation, null-input and non-directory guards
- ExHiROM acceptance gate

**Below the boundary** -- `SplitStrategy` hierarchy under `service/split/` owns:
- Chunk count and size per format/type
- Flag sequencing per chunk
- Byte-level interleaving (GD3 HiROM only, via `Gd3HiRomSplitStrategy`)

`SplitStrategyFactory` dispatches `(CopierFormat, RomType)` to the correct strategy. The
12-cell `SplitStrategyFactoryTest.strategyMatrix` is the authoritative proof that factory
wiring is correct.

## Coverage ownership

Tests in `split/` own all split-mechanics assertions (chunk count, flag sequencing,
interleave, HiROM threshold dispatch). `NativeRomSplitterTest` owns all orchestration
assertions. Any new split-mechanics test belongs here; any new filename/padding/validator
test belongs in `NativeRomSplitterTest`.

When removing or refactoring tests in `split/`, verify that `NativeRomSplitterTest` does
not depend on the removed test as its only coverage source for a behavior.
Decision refs: DL-004, DL-005.
