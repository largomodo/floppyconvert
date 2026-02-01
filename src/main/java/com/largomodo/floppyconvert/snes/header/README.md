# SNES Copier Header Formats

## Overview

Each SNES backup unit format (FIG, SWC, UFO, GD3) requires a 512-byte header prepended to ROM data. Headers encode metadata about ROM type (LoROM/HiROM), SRAM configuration, DSP chipsets, and multi-file indicators. This package implements format-specific header generation while maintaining exact binary compatibility with ucon64.

## Header Generator Interface

```java
public interface HeaderGenerator {
    int HEADER_SIZE = 512;
    byte[] generateHeader(SnesRom rom, int partSize, int splitPartIndex, boolean isLastPart, byte chunkFlag);
}
```

All generators return exactly 512 bytes (or empty byte[] when headers are omitted for specific parts). Little-endian byte order for all multi-byte fields.

### chunkFlag Parameter

The `chunkFlag` parameter provides per-part multi-file flag encoding for formats requiring irregular chunk sequences. UFO HiROM uses lookup table-based flags (0x40/0x10/0x00) from UfoHiRomChunker indicating copier memory window progression through accumulated ROM data. Non-UFO formats ignore this parameter and use existing logic (isLastPart-based) for multi-file indicators.

**chunkFlag Parameter Purpose:**
- UFO HiROM requires position-based flags from lookup table (not chunk-size-based)
- Example: 12Mbit uses [0x40, 0x10, 0x10, 0x00] for chunks [4, 2, 4, 2] (third 4Mbit chunk gets 0x10, not 0x40)
- Single parameter maintains consistent signature across all formats
- Alternative (overloading) would create dual maintenance burden and ambiguity

### partSize Parameter

The `partSize` parameter provides the size of ROM data (in bytes) for THIS specific split part. This value is calculated per-iteration during splitting and may vary for the last part if it contains less than a full chunk.

**Format-specific usage:**

- **SWC/FIG**: Use partSize to calculate block count (partSize / 8192) stored in bytes 0-1, ignore chunkFlag (use isLastPart for multi-file flag)
- **UFO**: Dual-source requirement - partSize for bytes 0-1 (per-part blocks), total ROM size for byte 17 (total Mbit), use chunkFlag for byte 2
- **GD3**: Ignores partSize entirely - uses total ROM size for memory map table selection, ignores chunkFlag (first-part-only headers)

The partSize parameter allows generators to accurately encode per-part metadata while maintaining access to total ROM size through the SnesRom parameter. The chunkFlag parameter enables UFO irregular chunk sequences without breaking interface consistency for other formats.

## Format-Specific Behavior

### SWC Format (Super Wild Card)

**Emulation Mode:**
- HiROM: 0x30 (emulation mode)
- LoROM: 0x00 (native mode)

**SRAM Size Encoding (offset varies):**
- 0 bytes: 0x0C
- ≤2KB: 0x08
- ≤8KB: 0x04
- >8KB: 0x00

**Multi-File Flag:**
- Not last part: 0x40
- Last part: 0x00

**Fixed IDs:**
- Offset 8: 0xAA
- Offset 9: 0xBB
- Offset 10: 0x04

**Size Field:** ROM data length in 8KB blocks

**Header Generation:** All parts receive headers

### FIG Format (Pro Fighter)

**HiROM Flag:**
- HiROM: 0x80
- LoROM: 0x00

**Multi-File Flag:**
- Not last part: 0x40
- Last part: 0x00

**Emulation Bytes (emu1/emu2):**
- Complex logic combining SRAM and DSP flags
- DSP chipsets set specific bits in emulation bytes
- SRAM size influences emulation byte values

**Size Field:** ROM data length in 8KB blocks

**Header Generation:** ALL parts receive headers (unlike GD3)

**Quirk:** FIG headers must be present on every split part, not just the first. This differs from GD3 behavior and is required for Pro Fighter hardware.

### UFO Format (Super UFO)

**ID String:**
- "SUPERUFO" at offset 8 (8 bytes, ASCII)

**Bank Type:**
- HiROM: 0 (bank type field)
- LoROM: 1 (bank type field)

**SRAM Size Codes:**
- 0 bytes: 0 (no SRAM)
- 16KB: 1
- 64KB: 2
- 256KB: 3
- >32KB: 8 (special code for large SRAM)

**SRAM Mapping Controls (A15, A20-A23):**

For **HiROM** with SRAM:
- A15 control: specific value for HiROM mapping
- A20-A23 controls: configured for HiROM address decoding

For **LoROM** with SRAM:
- A15 control: specific value for LoROM mapping
- A20-A23 controls: configured for LoROM address decoding

These controls configure hardware address line mapping to SRAM chips. HiROM and LoROM use different mapping schemes due to different memory map architectures.

**Size Field:** Dual-source encoding:
- Bytes 0-1: Per-part size in 8KB blocks (calculated from partSize parameter)
- Byte 17: Total ROM size in Mbit (calculated from rom.rawData().length)

This dual-source requirement means UFO generators must use both partSize (for per-part block count) and total ROM size (for Mbit encoding) in the same header.

**Header Generation:** All parts receive headers

### GD3 Format (Game Doctor SF3)

**ID String:**
- "GAME DOCTOR SF 3" at specific offset

**First-Part-Only Headers:**
- splitPartIndex == 0: Generate 512-byte header
- splitPartIndex > 0: Return empty byte[] (NO header)

This is a critical quirk: GD3 hardware only reads the header from the first file. Additional parts (.078 files) contain raw ROM data without headers.

**Memory Map Table Selection:**

Based on ROM size (after interleaving and padding):

**HiROM tables:**
- 8MB: MAP_HI_8MB
- 16MB: MAP_HI_16MB
- 24MB: MAP_HI_24MB
- 32MB: MAP_HI_32MB

**LoROM tables:**
- 4MB: MAP_LO_4MB
- 8MB: MAP_LO_8MB
- 16MB: MAP_LO_16MB
- 32MB: MAP_LO_32MB

Memory map tables configure Game Doctor's internal memory mapping hardware to correctly decode ROM accesses.

**HiROM SRAM Mapping:**
- Offset 0x29: SRAM configuration byte
- Offset 0x2A: SRAM size code

**LoROM DSP and SRAM Mapping:**
- Offset 0x14: DSP flag (if hasDsp == true)
- Offset 0x1C: DSP flag (duplicate location)
- Offset 0x24: SRAM configuration
- Offset 0x28: SRAM size code

**8Mbit Alignment Requirement:**

GD3 format requires ROM data to be padded to 8Mbit (1MB) boundaries before splitting. This padding occurs in SnesInterleaver before headers are generated. Header generators receive already-padded data and select memory map tables based on final padded size.

**File Naming:**
- All GD3 split files use .078 extension (fixed)
- Standard formats use .1, .2, .3, etc.

## SRAM Size Encoding Comparison

| Actual SRAM | SWC Code | FIG Behavior | UFO Code | GD3 Offset |
|-------------|----------|--------------|----------|------------|
| 0 bytes     | 0x0C     | Special emu  | 0        | N/A        |
| 2KB         | 0x08     | Special emu  | N/A      | Offset 0x29/0x2A (HiROM) or 0x24/0x28 (LoROM) |
| 8KB         | 0x04     | Special emu  | N/A      | Same as above |
| 16KB        | 0x00     | Special emu  | 1        | Same as above |
| 64KB        | 0x00     | Special emu  | 2        | Same as above |
| 256KB       | 0x00     | Special emu  | 3        | Same as above |
| >32KB       | 0x00     | Special emu  | 8        | Same as above |

Each format uses different encoding schemes based on hardware capabilities and address decoding logic.

## DSP Chipset Handling

DSP chipsets (Super FX, SA-1, DSP-1, etc.) are detected from ROM internal header map type byte:
- 0x03: DSP-1
- 0x05: DSP-2
- 0x13-0x15: DSP-3, DSP-4
- 0x1A: SA-1

**Format-specific DSP handling:**
- **SWC:** No specific DSP flags (relies on ROM internal header)
- **FIG:** DSP flag influences emulation bytes
- **UFO:** No specific DSP flags
- **GD3:** LoROM DSP sets flags at offsets 0x14 and 0x1C; HiROM DSP handling via memory map tables

## Multi-File Indicators

Multi-file flags inform copier hardware that additional parts follow:

- **SWC/FIG:** 0x40 flag when not last part
- **UFO:** Similar multi-file flag mechanism
- **GD3:** First-part-only headers mean multi-file status inferred from .078 extension

The `isLastPart` parameter controls this flag. For a 32 Mbit ROM split into 8 files (SWC 4Mbit chunks), parts 0-6 set multi-file flag; part 7 clears it.

## Size Field Calculation

Different formats use different size units and different sources (partSize vs total ROM size):

- **SWC/FIG:** 8KB blocks = `partSize / 8192` (uses partSize parameter for per-part block count)
- **UFO:**
  - Bytes 0-1: 8KB blocks = `partSize / 8192` (uses partSize for per-part blocks)
  - Byte 17: Mbit = `(rom.rawData().length * 8) / (1024 * 1024)` (uses total ROM size)
- **GD3:** Implicit in memory map table selection based on total ROM size (partSize parameter ignored)

## Design Tradeoffs

### First-Part-Only Headers (GD3)

**Chosen:** GD3 headers only on splitPartIndex == 0

**Cost:** Generators must handle empty byte[] return for parts > 0

**Benefit:** Exact binary compatibility with ucon64 and Game Doctor hardware

**Alternative cost:** Adding headers to all parts would break hardware compatibility

**Rationale:** Hardware expects this behavior; deviation would cause load failures

### Format-Specific SRAM Encoding

**Chosen:** Each format has unique SRAM size encoding table

**Cost:** Cannot share SRAM encoding logic across generators

**Benefit:** Matches hardware expectations for each copier format

**Alternative cost:** Generic encoding would produce non-functional headers

**Rationale:** Copier hardware uses different address decoding schemes; encoding must match hardware design

### Memory Map Tables (GD3)

**Chosen:** Hardcoded tables for standard ROM sizes (4MB, 8MB, 16MB, 24MB, 32MB)

**Cost:** Non-standard ROM sizes may not have exact table match

**Benefit:** Covers all commercially released SNES games

**Alternative cost:** Dynamic table generation would be complex and untested with real hardware

**Rationale:** Standard sizes cover entire SNES library; edge cases can fall back to next larger table

## Extending with New Formats

To add a new copier format:

1. Create new class implementing HeaderGenerator interface
2. Add format to CopierFormat enum
3. Update HeaderGeneratorFactory.get() switch statement
4. Implement format-specific encoding rules in generateHeader()
5. Add unit tests validating header structure
6. Add E2E tests with real ROM files

Refer to existing generators (SwcHeaderGenerator, FigHeaderGenerator, etc.) for implementation patterns.
