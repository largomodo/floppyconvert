# ROM Splitting Service Architecture

## Overview

The ROM splitting service orchestrates the conversion of SNES ROM files into format-specific split parts with copier headers. NativeRomSplitter coordinates SnesRomReader, SnesInterleaver, and HeaderGeneratorFactory to produce split files matching each backup unit's requirements.

## Split Pipeline Data Flow

```
ROM File (bytes)
    ↓
SnesRomReader → SnesRom record (metadata + rawData)
    ↓
NativeRomSplitter.split():
    ↓
    Conditional interleaving (GD3 + HiROM only) → global algorithm
    ↓
    Calculate chunk size:
      - GD3: 8Mbit default, force 4Mbit if HiROM && size <= 16Mbit
      - SWC/FIG: 4Mbit
      - UFO LoROM: 4Mbit
      - UFO HiROM: UfoHiRomChunker.computeChunks() → irregular sequence
    ↓
    Loop iteration i (for each chunk):
        offset = i * chunkSize
        length = min(chunkSize, remaining)  ← per-part size
        chunkFlag = (UFO HiROM) ? lookup[i].flag : (isLast ? 0x00 : 0x40)
        ↓
        HeaderGenerator.generateHeader(rom, length, i, isLast, chunkFlag)
        → byte[512] header with length-based blocks and format-specific flag
        ↓
        createFilename(workDir, baseName, format, i, totalParts, rom)
        → File with format-specific name
        ↓
        writeChunk(file, header, data, offset, length)
        → Split file with correct header + name
```

**Key insights:**
- `length` (partSize) is calculated per-iteration and varies for the last part, which may be smaller than chunkSize. This per-part size is passed to header generators for accurate block count encoding.
- GD3 force-split at 16Mbit boundary (inclusive): HiROM ROMs ≤16Mbit force 4Mbit chunks triggering X-padding in filename logic
- UFO HiROM uses UfoHiRomChunker for irregular chunk sequences; LoROM uses standard 4Mbit chunks
- chunkFlag parameter passes UFO lookup table flags (0x40/0x10/0x00) to header generators

## Format-Specific Naming

### FIG/SWC Format

**Pattern:** `baseName.N` where N = part number (1-indexed)

**Examples:**
- Single 4Mbit ROM: `game.1`
- 12Mbit ROM (3 parts): `game.1`, `game.2`, `game.3`

### UFO Format

**Pattern:** `baseName.Ngm` where N = part number (1-indexed)

**Examples:**
- Single 4Mbit ROM: `game.1gm`
- 12Mbit HiROM (irregular chunks): `game.1gm`, `game.2gm`, `game.3gm`, `game.4gm` (4, 2, 4, 2 Mbit chunks)
- 12Mbit LoROM (standard chunks): `game.1gm`, `game.2gm`, `game.3gm` (4, 4, 4 Mbit chunks)

**Rationale:** The `.gm` extension identifies UFO format files for Game Master hardware. Extension combines part number + "gm" suffix (e.g., "1gm", "2gm").

**UFO HiROM Irregular Chunks:**

UFO copier has fixed memory bank layout per total ROM size. Standard 4Mbit chunks would mismatch bank boundaries causing loader failure. UfoHiRomChunker provides lookup table-based chunk sequences for copier-specific bank allocation:

- **2 Mbit:** [2] with flags [0x10]
- **4 Mbit:** [2, 2] with flags [0x10, 0x00]
- **12 Mbit:** [4, 2, 4, 2] with flags [0x40, 0x10, 0x10, 0x00]
- **20 Mbit:** [4, 4, 2, 4, 4, 2] with flags [0x40, 0x40, 0x10, 0x10, 0x10, 0x00]
- **32 Mbit:** [4, 4, 4, 4, 4, 4, 4, 4] with flags [0x40, 0x40, 0x40, 0x10, 0x10, 0x10, 0x10, 0x00]

Flag encoding from ucon64 size_to_flags table (not derived from individual chunk sizes): 0x40 indicates multi-start (4Mbit bank), 0x10 indicates multi-continue (2Mbit bank), 0x00 indicates last-part. Pattern shows copier memory window progression through accumulated ROM data.

Lookup table fallback: ROM sizes not in table (e.g., 48 Mbit) use 32Mbit table entry (matches ucon64 reference behavior).

### GD3 Format (SF-Code)

**Pattern:** SF + Mbit + 3-char name + suffix + .078

GD3 uses SF-Code naming for hardware compatibility. All files use fixed `.078` extension regardless of part count.

**SF-Code Construction:**

1. **Prefix:** "SF" (fixed)
2. **Size:** Mbit value (total ROM size / 1024 / 1024 * 8)
3. **Name:** First 3 alphanumeric characters from sanitized ROM title
4. **Suffix:** Format-dependent (see below)
5. **Extension:** ".078" (fixed)

**Suffix Logic:**

- **Single file:** Underscore-padded to 8 characters total
  - Example: 4Mbit "Super Mario" → `SF4SUP__.078`
  - Format: `SF` + `4` + `SUP` + `__` (2 underscores for padding)

- **Multi-file:** Sequence letters A, B, C, ... (no padding)
  - Example: 16Mbit "Star Fox" 2 parts → `SF16STAA.078`, `SF16STAB.078`
  - Format: `SF` + `16` + `STA` + `A`/`B`

- **HiROM ≤16Mbit multi-file:** X-padding before sequence letter
  - Example: 16Mbit "Chrono Trigger" 2 parts → `SF16CHRXA.078`, `SF16CHRXB.078`
  - Format: `SF` + `16` + `CHR` + `X` + `A`/`B`
  - Rationale: Hardware expects X-padding for HiROM ROMs ≤16Mbit in multi-file splits

**SF-Code Constraints:**

1. **Size calculation:** Total ROM size in Mbit (not per-part size)
2. **Sanitization:** Remove all non-alphanumeric characters from ROM title
3. **SF-Code construction:** Concatenate SF + Mbit + 3-char name + suffix
4. **X-padding condition:** `rom.isHiRom() && sizeMbit <= 16` (inclusive 16)
5. **Suffix logic:** Single-file uses underscore padding; multi-file uses sequence letters
6. **Extension:** Always `.078` (not `.1`, `.2`, etc.)

**X-Padding Edge Case:**

The X-padding condition is `sizeMbit <= 16` (inclusive). A 16Mbit HiROM in multi-file mode gets X-padding:
- Correct: `SF16CHRXA.078`, `SF16CHRXB.078`
- Wrong: `SF16CHRA.078`, `SF16CHRB.078` (missing X)

Off-by-one errors here break hardware compatibility. The condition must be `<=` not `<`.

**Examples:**

| ROM Title | Size | Type | Format | Files |
|-----------|------|------|--------|-------|
| Super Mario World | 4Mbit | LoROM | Single | `SF4SUP__.078` |
| Chrono Trigger | 16Mbit | HiROM | Multi (2 parts) | `SF16CHRXA.078`, `SF16CHRXB.078` |
| Star Fox | 8Mbit | LoROM | Single | `SF8STA__.078` |
| Tales of Phantasia | 24Mbit | HiROM | Multi (3 parts) | `SF24TALA.078`, `SF24TALB.078`, `SF24TALC.078` |

## Architecture Decisions

### Why Separate Filename Generation from Header Generation?

Headers are format-specific bytes prepended to ROM data. Naming is format-specific string manipulation. These are orthogonal concerns:

- **Headers depend on:** ROM metadata, part size, part index
- **Naming depends on:** ROM metadata, total parts, part index

Combining them would couple binary encoding with string formatting. Current separation allows testing each independently.

### Why Inline GD3 Naming Instead of Extracting?

GD3 SF-Code logic is ~60 lines with 6 constraints (size calculation, sanitization, SF-Code construction, X-padding, suffix logic, extension). Extracting to separate class would require:

- New `Gd3NamingStrategy` class (~80 lines with tests)
- Interface `NamingStrategy` for polymorphism (~15 lines)
- Factory or strategy selection logic (~20 lines)

**Total:** ~115 lines vs ~60 lines inline

**Benefit of extraction:** Easier to test naming independently, clearer separation of concerns

**Cost of extraction:** 3 new files, increased indirection, premature abstraction (no other formats have complex naming)

**Decision:** Keep inline until second format needs strategy pattern (YAGNI principle). If GD3 variants (PAL naming, region-specific) or 2+ other formats need complex naming, extract to strategy pattern.

### Why Per-Part Size Parameter?

Header generators require per-part ROM data size for accurate block count encoding. The last split part may be smaller than chunkSize (e.g., 10Mbit ROM splits to 2x4Mbit + 1x2Mbit).

**Format-specific usage:**
- SWC/FIG: Use partSize for block count in bytes 0-1, ignore chunkFlag (use isLastPart for multi-file flag)
- UFO: Use partSize for bytes 0-1, total ROM size for byte 17 (dual-source), use chunkFlag for byte 2
- GD3: Ignore partSize (use total ROM size for memory map selection), ignore chunkFlag (first-part-only headers)

Encoding full 4Mbit for 2Mbit last part produces invalid headers. partSize parameter ensures accurate per-part metadata without breaking GD3's total-size-based logic.

### Why chunkFlag Parameter?

UFO HiROM requires position-based multi-file flags from lookup table (not derived from individual chunk sizes):

- **Problem:** 12Mbit ROM uses chunks [4, 2, 4, 2] but flags [0x40, 0x10, 0x10, 0x00] (third 4Mbit chunk gets 0x10, not 0x40)
- **Broken approach:** Deriving flags from chunk sizes would produce incorrect copier behavior
- **Solution:** Pass chunkFlag from UfoHiRomChunker lookup table to HeaderGenerator

**Alternative rejected:** State-based flag passing (constructor/setter) makes HeaderGenerator stateful, complicating lifecycle and thread-safety. Parameter passing is stateless and clearer data flow.

Non-UFO formats ignore chunkFlag parameter and use existing isLastPart-based logic. Single signature maintains interface consistency without overloading ambiguity.

## Invariants

1. **Header size is always 512 bytes:** All generators return byte[512] or empty byte[]. No other sizes allowed.

2. **GD3 headers only on first part:** `if (splitPartIndex > 0) return new byte[0];` must remain in Gd3HeaderGenerator. Hardware only reads first file's header.

3. **Last part size may be smaller:** `length = Math.min(chunkSize, data.length - offset)` means last iteration can have length < chunkSize. Generators must handle any partSize value.

4. **SWC/FIG/UFO block calculation:** `blocks = partSize / 8192` (8KB blocks). Integer division only (no floating point).

5. **UFO dual-source requirement:** Bytes 0-1 use partSize, byte 17 uses total ROM size. Both sources required in same generator.

6. **GD3 X-padding condition:** `rom.isHiRom() && sizeMbit <= 16` (inclusive 16). Off-by-one here breaks hardware compatibility.

7. **GD3 underscore padding:** Single-file case pads to 8 characters with underscores. Multi-file case does NOT pad (just appends sequence letter).

8. **SF-Code sanitization:** `replaceAll("[^a-zA-Z0-9]", "")` removes ALL non-alphanumeric. Spaces, hyphens, underscores all removed.

## Extending with New Formats

To add a new ROM splitting format:

1. Add format to CopierFormat enum
2. Create new HeaderGenerator implementation
3. Update HeaderGeneratorFactory.get() switch statement
4. Add naming logic to NativeRomSplitter.createFilename() switch statement
5. Update RomPartComparator if new naming pattern requires custom sorting
6. Add unit tests for header generation
7. Add integration tests for NativeRomSplitter with new format
8. Add E2E tests with real ROM files

If the new format has complex naming (like GD3 SF-Code), consider extracting naming logic to strategy pattern at that point.
