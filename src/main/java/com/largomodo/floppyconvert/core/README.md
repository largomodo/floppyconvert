# Core Package

## Overview

The `core` package contains orchestration logic for ROM-to-floppy conversion. Key components: `RomProcessor` (pipeline coordination), `ConversionFacade` (dependency inversion boundary), domain models (`DiskLayout`, `RomPartMetadata`), and packing algorithms (`GreedyDiskPacker`).

## Architecture

```
┌──────────────┐
│ RomProcessor │ (orchestration)
└──────┬───────┘
       │ depends on abstraction
       ▼
┌──────────────┐
│ConversionFac.│ (interface)
└──────────────┘
       △
       │ implemented by
       │
┌──────────────┐
│DefaultConver.│ (service layer)
│sionFacade    │
└──────┬───────┘
       │ delegates to
       ▼
┌──────────────┐
│NativeRomSpl. │
│Fat12Writer   │
└──────────────┘
```

This structure enables:
- Core package depends on abstraction (no concrete service imports)
- Service layer provides implementation
- RomProcessor testable with mocked facade
- Service implementations swappable without core changes

## Design Decisions

### Facade Layer Pattern

**Chosen:** `ConversionFacade` interface in core, `DefaultConversionFacade` implementation in service

**Cost:** One level of indirection (interface call overhead)

**Benefit:**
- Dependency inversion: core depends on abstraction, not concrete implementations
- Independent testing: RomProcessor testable with mocked facade (no real ROM splitting required)
- Service swapping: future implementations (e.g., parallel splitter, cloud-based writer) require no core changes

**Alternative cost:** Direct service dependencies would couple RomProcessor to specific implementations, making isolated testing impossible and service evolution brittle.

**Rationale:** Testability and architectural cleanliness prioritized over minimal indirection. I/O-bound ROM processing makes interface call overhead negligible.

**Verification:** `grep "import.*service.*Native\|Fat12" src/main/java/de/nrq/floppyconvert/core/**/*.java` must return no results.

### God Method Decomposition

**Chosen:** Extract helper methods from `RomProcessor.processRom()` to enforce <50 line threshold

**Extracted helpers:**
- `extractAndSanitizeBaseName()`: Filename normalization
- `createDosNameMapping()`: Per-layout DOS 8.3 mapping
- `validateNoDosCollisions()`: Duplicate detection
- `createDiskName()`: Disk image naming logic
- `writeDisksFromLayout()`: Disk creation loop

**Cost:** More methods to navigate (5 additional private methods)

**Benefit:**
- Single responsibility: each helper has one clear purpose
- Independent testing: helpers testable in isolation
- Reduced cognitive load: main orchestration method readable in <50 lines
- Modification isolation: changing naming logic doesn't touch disk writing logic

**Alternative cost:** Monolithic 200+ line method forces developers to understand entire conversion pipeline to modify any single concern (DOS naming, disk writing, collision detection).

**Rationale:** Maintainability prioritized over minimal method count. 50-line threshold prevents god methods from reaccumulating complexity.

### Per-Layout DOS Name Validation

**Chosen:** Validate DOS name collisions within each disk layout, not across entire ROM

**Cost:** More complex validation logic (per-layout iteration instead of global check)

**Benefit:** Prevents false positives on multi-disk ROMs where same filename legitimately appears on different disks (e.g., PART1.1 on disk 1 and PART2.1 on disk 2)

**Alternative cost:** Global collision detection would reject valid multi-disk conversions, forcing manual workarounds.

**Rationale:** Correctness over simplicity. DOS name uniqueness is a per-disk invariant, not a cross-disk invariant.

## Invariants

**Facade Dependency:** Core package must not import concrete service implementations (`NativeRomSplitter`, `Fat12ImageWriter`, `Ucon64Driver`). All service access through `ConversionFacade` interface. Violation breaks dependency inversion.

**Method Complexity:** No method may exceed 50 lines (excluding blank lines and comments). Threshold prevents god methods from reaccumulating complexity. Automated verification: line count check on `processRom()` and extracted helpers.

**DOS Name Uniqueness:** Each disk layout must have unique DOS 8.3 names for all parts. Collision within a layout indicates normalization failure. Collision detection runs per-layout before disk writing.

**Workspace Cleanup:** All intermediate files tracked in `ConversionWorkspace`. Cleanup runs in `close()` even if exceptions occur. Final outputs promoted via atomic move (`promoteToFinal()`) to prevent partial artifacts on failure.
