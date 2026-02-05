# Test Infrastructure

## ROM Provisioning Architecture

E2E tests use TestRomProvider + E2ETestRomRegistry pattern for ROM acquisition with synthetic fallback.

### Component Interaction

```
E2E Test Method
      |
      v
TestRomProvider.getRomOrSynthetic()
      |
      +---> Try real ROM from classpath
      |     (success: copy to outputDir)
      |
      +---> Fallback: E2ETestRomRegistry.getSpec()
            |
            +---> SyntheticRomFactory.generateXxxRom()
```

### Design Rationale

**Registry + Provider over inline fallback**: Fallback logic would duplicate across 5+ E2E methods; centralized utility provides single source of truth with testable isolation

**Registry + Provider over base class**: Java single-inheritance limits future design; modern testing prefers composition; JUnit 5 idioms don't require base classes

**Compile-time constants**: ROM mappings are fixed at compile-time; static access simplifies test code; follows SnesConstants pattern

**Path return type**: E2E tests need file paths for CLI invocation; returning Path avoids extra write step; consistent with existing test patterns

## Additional Design Decisions

### Full ExHiROM Range (48-64 Mbit)

**Decision**: Allow SyntheticRomFactory to generate ExHiROM across full 48-64 Mbit range.

**Rationale**: HiROM/LoROM already support arbitrary sizes. Restricting ExHiROM creates inconsistency. Hardware validators enforce actual limits. Factory should be permissive.

### Unique Title Pattern

**Problem**: GD3 format creates DOS 8.3 name collisions when multiple synthetic ROMs share generic titles like "SYNTHETIC ROM".

**Solution**: Auto-generated title pattern encodes ROM characteristics: `SYNLO{size}T{sram}{dsp}` / `SYNHI{size}T{sram}{dsp}` / `SYNEX{size}T{sram}{dsp}`.

**Examples**:
- `SYNLO4T0` = 4Mbit LoROM, 0KB SRAM, no DSP
- `SYNHI32T8D` = 32Mbit HiROM, 8KB SRAM, DSP present
- `SYNEX48T0` = 48Mbit ExHiROM, 0KB SRAM, no DSP

**Benefit**: Prevents collisions. Enables debugging without filename lookup.

### ExHiROM Header Configuration

**Decision**: Header offset 0x40FFB0, map mode 0x25.

**Rationale**: ExHiROM internal header at 0x40FFB0 per RomType enum. SnesRomReader probes this location. Map mode 0x25 encodes ExHiROM (hi-bit set + extended flag) per official SNES dev documentation. SnesRomReader uses this value for ExHiROM classification.

### Path Return Type

**Decision**: TestRomProvider returns Path not byte[].

**Rationale**: E2E tests need file paths for CLI invocation. Returning Path avoids extra write step. Consistent with existing test patterns.

### SHA1 in README (Main Project)

**Decision**: Calculate and document SHA1 checksums for 7 real ROMs now.

**Rationale**: Provides immediate verification capability. User explicitly requested calculation now. Deferred documentation reduces value.

### Invariants

- **Title uniqueness**: Each synthetic ROM has a unique title surviving DOS 8.3 truncation (first 8 uppercase chars)
- **Fallback determinism**: Given same inputs, TestRomProvider always returns same ROM (either real or identical synthetic)
- **Checksum validity**: All generated ROMs satisfy complement + checksum = 0xFFFF
- **Header location**: LoROM at 0x7FB0, HiROM at 0xFFB0, ExHiROM at 0x40FFB0

## ROM Registry Mappings

| Real ROM Path | Size | Type | SRAM | DSP | Synthetic Title |
|--------------|------|------|------|-----|-----------------|
| `/snes/Chrono Trigger (USA).sfc` | 32 Mbit | HiROM | 8 KB | No | SYNCHRN32H |
| `/snes/Super Mario World (USA).sfc` | 4 Mbit | LoROM | 0 KB | No | SYNMARIO4L |
| `/snes/Super Bomberman 2 (USA).sfc` | 8 Mbit | HiROM | 0 KB | No | SYNBOMB8H |
| `/snes/Art of Fighting (USA).sfc` | 16 Mbit | HiROM | 0 KB | No | SYNFIGHT16H |
| `/snes/Street Fighter II - The World Warrior (USA).sfc` | 20 Mbit | HiROM | 0 KB | No | SYNSTREET20H |
| `/snes/Breath of Fire (USA).sfc` | 12 Mbit | LoROM | 8 KB | No | SYNBREATH12L |
| `/snes/ActRaiser 2 (USA).sfc` | 12 Mbit | HiROM | 0 KB | No | SYNACT12H |
| `/snes/synthetic_exhirom_48mbit_0kb.sfc` | 48 Mbit | ExHiROM | 0 KB | No | SYNEX48T0 |

## Test Categories

| Test File | ROM Source |
|-----------|------------|
| FloppyConvertE2ETest | TestRomProvider (real or synthetic fallback) |
| FloppyConvertLoggingTest | SyntheticRomFactory direct (LOGTEST4L) |
| FloppyConvertConcurrencyTest | SyntheticRomFactory direct (CONCUR4L) |
| FloppyConvertRecursionTest | SyntheticRomFactory direct (RECURS4L) |
| SnesRomReaderTest | SyntheticRomFactory direct (SYNHI32T8, SYNLO4T0) |

E2E tests use TestRomProvider for automatic fallback. All other tests use SyntheticRomFactory directly with no assumptions about real ROM availability.
