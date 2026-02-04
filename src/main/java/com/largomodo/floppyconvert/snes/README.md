# SNES ROM Conversion Architecture

## Overview

The SNES ROM conversion pipeline transforms SNES ROM files into copier-format split files ready for floppy disk distribution. The pipeline consists of four phases orchestrated through a staged architecture with immutable domain objects and stateless services.

## Staged Pipeline

```
Input ROM File
     |
     v
[SnesRomReader] ---> SnesRom (domain record)
     |                  |
     |                  +---> RomType (LoROM/HiROM/ExHiROM)
     |                  +---> SRAM size, DSP flag, title
     |
     v
[HardwareValidator] ---> Validate ROM/format compatibility
     |                        |
     |                        v
     |                   Format-specific checks:
     |                     - UFO: HiROM ≤32Mbit, LoROM ≤36Mbit
     |                     - GD3: ExHiROM ≤64Mbit, HiROM/LoROM ≤32Mbit
     |                     - Throws UnsupportedHardwareException
     |
     v
[Conditional: GD3 + HiROM/ExHiROM only]
     |
     v
[SnesInterleaver] ---> byte[] interleaved data
     |                   (ExHiROM: split-interleave at 32Mbit boundary)
     |
     v
[NativeRomSplitter] ---> Split calculation (4Mbit/8Mbit boundaries)
     |                        |
     |                        v
     |                   [HeaderGeneratorFactory] ---> Format-specific generator
     |                        |
     |                        v
     |                   For each chunk:
     |                     - Generate header (512 bytes)
     |                     - Write header + chunk to file
     |                     - Name file (.1, .2, .078)
     |
     v
Output: List<File> (split ROM parts)
     |
     v
[RomProcessor] ---> Pack into floppy disks (existing logic)
```

## Validation Architecture

```
               ┌─────────────────────────────────────────────────────────┐
               │                    NativeRomSplitter                    │
               │  (entry point for format-specific ROM splitting)        │
               └─────────────────────────────────────────────────────────┘
                                         │
                    ┌────────────────────┼────────────────────┐
                    ▼                    ▼                    ▼
          ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
          │ HardwareValidator│  │  SnesInterleaver │  │ HeaderGenerator │
          │   (NEW)          │  │  (MODIFIED)      │  │ (existing)      │
          │                  │  │                  │  │                 │
          │ validate(rom,    │  │ interleave(data, │  │ generateHeader( │
          │   format)        │  │   romType)       │  │   rom, ...)     │
          └─────────────────┘  └─────────────────┘  └─────────────────┘
                    │                    │                    │
         ┌──────────┴──────────┐         │         ┌─────────┴─────────┐
         ▼                     ▼         │         ▼                   ▼
┌─────────────────┐ ┌─────────────────┐  │  ┌─────────────┐ ┌─────────────────┐
│UfoHardwareValid.│ │Gd3HardwareValid.│  │  │  Gd3Header  │ │SpecialCaseReg.  │
│                 │ │                 │  │  │  Generator  │ │  (NEW static)   │
│ Rejects HiROM   │ │ Rejects HiROM   │  │  │             │ │                 │
│ > 32Mbit        │ │ > 32Mbit        │  │  │ Uses        │ │ isTalesOf...()  │
│                 │ │ (unless ExHiROM)│  │  │ Registry    │ │ isDaiKaiju...() │
└─────────────────┘ └─────────────────┘  │  └─────────────┘ └─────────────────┘
                                         │
                                         ▼
                              ┌─────────────────────┐
                              │ ExHiROM handling:   │
                              │ - Split at 32Mbit   │
                              │ - Interleave base   │
                              │ - Interleave expan. │
                              │ - Concatenate       │
                              └─────────────────────┘
```

**HardwareValidator** enforces copier-specific constraints before splitting. Different backup unit formats have distinct memory mapping limitations. The interface enables format-specific validation strategies following the HeaderGenerator pattern.

**SpecialCaseRegistry** provides game-specific detection for ROMs requiring non-standard header values (e.g., Tales of Phantasia, Dai Kaiju Monogatari 2). Static utility pattern matches immutable ROM records.

## Data Flow

### Phase 1: ROM Parsing

```
ROM File (on disk)
  |
  v
Read bytes ---> Strip copier header (if fileSize % 1024 == 512)
  |
  v
Probe internal header locations:
  - 0x7FB0 (LoROM)
  - 0xFFB0 (HiROM)
  - 0x40FFB0 (ExHiROM)
  |
  v
Score each location (weighted algorithm)
  |
  v
Select best match ---> Extract metadata (title, SRAM, DSP, checksum)
  |
  v
SnesRom record (immutable)
```

**SnesRomReader** handles ROM file parsing with a weighted scoring algorithm that validates:
- Printable ASCII title characters (weight +1 per character)
- Valid checksum+complement sum (weight +4)
- Reasonable ROM size field
- Valid map type byte

### Phase 2: Interleaving (GD3 HiROM only)

```
byte[] rawData
  |
  v
Pad to 8Mbit boundary (if needed) ---> Fill with 0x00 (mirroring)
  |
  v
Global interleaving across entire padded buffer:
  For each 64KB block i in entire ROM:
    srcBlockOffset = i * 64KB
    destLowerOffset = i * 32KB
    destUpperOffset = (bufferSize/2) + i * 32KB
    |
    v
    Upper 32KB (srcBlockOffset+32K..srcBlockOffset+64K) ---> destLowerOffset
    Lower 32KB (srcBlockOffset..srcBlockOffset+32K) ---> destUpperOffset
  |
  v
byte[] interleaved data (globally swapped, not per-chunk)
```

**SnesInterleaver** performs global block swapping across the entire ROM address space. GD3 copier hardware expects symmetric block distribution: output first half (bytes 0 to n-1, where n = size/2) receives upper 32KB of each 64KB block, output second half (bytes n to 2n-1) receives lower 32KB. This global algorithm maintains 64KB block alignment required by copier memory map. Treating each 8Mbit chunk independently would corrupt block boundaries for multi-chunk ROMs. Interleaving is non-destructive (returns new byte array).

### Phase 3: Header Generation

```
SnesRom + splitPartIndex + isLastPart
  |
  v
HeaderGeneratorFactory ---> Format-specific generator (SWC/FIG/UFO/GD3)
  |
  v
Generator logic:
  - Calculate size fields (8KB blocks or Mbit)
  - Set HiROM/LoROM flags
  - Set SRAM size codes
  - Set multi-file flags
  - Set DSP flags (format-specific)
  - Set memory map tables (GD3 only)
  |
  v
byte[512] (header bytes, little-endian)
```

**HeaderGeneratorFactory** selects the appropriate generator based on CopierFormat enum. Each generator implements format-specific encoding rules for SRAM, DSP, memory mapping, and multi-file indicators.

### Phase 4: Splitting (NativeRomSplitter)

```
File inputRom + Path workDir + CopierFormat format
  |
  v
SnesRomReader.read(inputRom) ---> SnesRom
  |
  v
[Conditional: if GD3 + HiROM]
  SnesInterleaver.interleave(rom.rawData()) ---> byte[] data
  |
  v
Calculate split boundaries:
  - Standard (SWC/FIG): 4Mbit chunks
  - GD3: 8Mbit chunks (default), force 4Mbit if HiROM && size <= 16Mbit
  - UFO LoROM: 4Mbit chunks
  - UFO HiROM: UfoHiRomChunker.computeChunks() → irregular sequence
  |
  v
For each chunk (index i):
  chunkFlag = (UFO HiROM) ? lookup[i].flag : (isLast ? 0x00 : 0x40)
  HeaderGenerator.generateHeader(rom, partSize, i, isLastPart, chunkFlag) ---> byte[512] header
  |
  v
  Write to workDir:
    - Filename: baseName + extension (format-specific)
    - Content: header + chunk bytes
  |
  v
List<File> (split parts)
```

**NativeRomSplitter** coordinates all phases using dependency injection. Uses FileChannel for efficient I/O with large ROM files. GD3 force-split uses 4Mbit chunks for HiROM ROMs ≤16Mbit to trigger X-padding naming. UFO HiROM uses UfoHiRomChunker for irregular chunk sequences matching copier bank allocation.

## Design Rationale

### Immutable Domain Records (SnesRom)

- Record enforces immutability at language level
- No defensive copying needed
- Thread-safe by construction
- Null validation in compact form
- Natural value semantics for equality

### Separation of Reader and Interleaver

- SnesRomReader is complex (header probing, scoring algorithm)
- SnesInterleaver is format-specific (GD3 only)
- Separation allows testing in isolation
- Reader is reusable across all formats
- Interleaver only called when needed (conditional on GD3 + HiROM)

### Factory Pattern for HeaderGenerator

- Each copier format has unique header structure
- Factory encapsulates selection logic
- Implementations share 512-byte contract
- Easy to test each format independently
- Extensible for future formats without changing client code

### NativeRomSplitter as Orchestrator

- Coordinates all phases (read, interleave, split, header, write)
- Implements RomSplitter interface for dependency injection
- Manages file I/O in single location
- Stateless service (thread-safe)
- Can be tested with mocked dependencies

## Invariants

### SNES Internal Header Checksum

- Checksum + Complement MUST equal 0xFFFF
- Stored as little-endian 16-bit words
- Does not affect copier header generation but validates ROM integrity
- Used in scoring algorithm (weight +4) to prefer valid headers

### GD3 8Mbit Alignment

- Game Doctor hardware expects 8Mbit chunks
- ROMs not divisible by 8Mbit MUST be padded to next boundary
- Padding bytes: 0x00 (safe for ROM data)
- Interleaving operates on padded data
- Final file size may exceed original ROM size

### Header Size Consistency

- ALL copier headers are exactly 512 bytes
- No exceptions across FIG/SWC/UFO/GD3 formats
- Files without headers return empty byte[] (not null)
- Size validation occurs in HeaderGenerator.HEADER_SIZE constant

### LoROM vs HiROM Detection

- Dual-probe algorithm checks both 0x7FB0 and 0xFFB0
- Scoring prevents false positives
- Tie-breaking favors ASCII printable title
- ExHiROM requires separate probe at 0x40FFB0
- Invalid ROMs throw IOException (no silent failure)

### File Naming for Split Parts

- Standard formats (SWC/FIG/UFO): .1, .2, .3, ... .N
- GD3 format: .078 (fixed, from ucon64 snes_gd_make_names)
- Base filename preserves original (minus extension)
- No RomPartNormalizer involvement (different concern)

### Interleaving is Destructive

- Interleaver returns NEW byte array (does not modify input)
- Original rom.rawData() remains unchanged
- Interleaved data cannot be written back to SnesRom (immutable)
- NativeRomSplitter responsible for managing interleaved vs raw data

### Global Interleaving Algorithm

- GD3 copier expects global block swapping across entire ROM (not per-chunk)
- Output partitioning: first half (0 to n-1) and second half (n to 2n-1) where n = size/2
- Block mapping: for all 64KB blocks i, upper 32KB → output[i*32KB], lower 32KB → output[n + i*32KB]
- Per-chunk iteration would break 64KB block boundaries for multi-chunk ROMs causing corruption
- Matches ucon64 reference implementation: single pass over entire buffer with global offset

### Hardware Validation Invariants

**1. Validation precedes splitting**: HardwareValidator.validate() must be called before any format-specific logic in NativeRomSplitter. CopierFormat is only known at split time, making NativeRomSplitter the correct entry point for validation.

**2. ExHiROM split-interleave order**: Base 32Mbit interleaved THEN expansion interleaved THEN concatenated (not interleaved as single block). ExHiROM layout: first 32Mbit (base ROM) mapped to $C0-$FF banks, remaining data (expansion) mapped to $40-$7D banks. Single-pass interleaving would corrupt bank boundaries.

**3. UFO HiROM ceiling**: 32Mbit maximum for HiROM, enforced at two levels (validator + chunker exception). UFO hardware cannot map HiROM banks beyond $40. Reference: ucon64 snes.c:1445 explicit rejection.

**4. GD3 ExHiROM ceiling**: 64Mbit maximum (limited by existing memory map tables in Gd3HeaderGenerator). Standard HiROM and LoROM limited to 32Mbit.

**5. Exception logging**: UnsupportedHardwareException must be logged at throw site (caller's responsibility). RuntimeException enables fail-fast validation without catch blocks at every call site.

### GD3 HiROM Force-Split (≤16Mbit)

- GD3 HiROM ROMs ≤16Mbit (2MB) force 4Mbit chunks instead of default 8Mbit
- Ensures multi-part split (chunkCount > 1) to trigger X-padding in filename logic
- Without force-split, 8Mbit HiROM stays single file without X-padding (naming inconsistency)
- Matches ucon64 condition `size <= 16 * MBIT` for copier loader compatibility
- LoROM GD3 of any size unaffected (uses standard logic, no force-split)

### UFO HiROM Irregular Chunks

- UFO copier has fixed memory bank layout per total ROM size
- Standard 4Mbit chunks mismatch bank boundaries causing loader failure
- UfoHiRomChunker provides lookup table with 5 size variants (2, 4, 12, 20, 32 Mbit)
- Multi-file flags (0x40/0x10/0x00) indicate copier memory window progression (position-based)
- Flag encoding from ucon64 size_to_flags table (not derived from individual chunk sizes)
- Chunker throws IllegalArgumentException for unsupported sizes (defensive, after validator)
- LoROM UFO uses standard 4Mbit chunks (no lookup table)

## Binary Compatibility

The implementation maintains exact binary compatibility with ucon64:

- Split boundaries match ucon64 (4Mbit for standard, 8Mbit for GD3)
- File naming matches ucon64 conventions (.1, .2 vs .078)
- Header structures match copier hardware expectations
- GD3 interleaving matches ucon64 block swapping algorithm

This ensures drop-in replacement capability for existing workflows and guaranteed copier hardware compatibility.
