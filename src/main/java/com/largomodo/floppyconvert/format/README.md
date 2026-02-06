# Format Package

## Overview

The `format` package defines copier format specifications (FIG/SWC/UFO/GD3) as a shared vocabulary across all layers. This package was extracted from `core` to break circular dependencies between `core`, `service`, and `snes` packages.

## Why This Package Exists

**Pre-refactoring circular dependency:**
```
core → service → core (via CopierFormat)
snes → core → service → snes
```

The `CopierFormat` enum was originally in the `core` package. This created bidirectional imports:
- Service layer needed `CopierFormat` for ROM splitting logic
- SNES header generators needed `CopierFormat` for format-specific header generation
- Core orchestration needed `CopierFormat` for pipeline coordination

When all three packages imported from each other, Maven builds became order-dependent and refactoring became fragile.

**Post-refactoring acyclic dependency:**
```
FloppyConvert → core → service → snes → format
```

Moving `CopierFormat` to a dedicated `format` package provides:
- Single source of truth for format specifications
- No bidirectional imports (format package has no dependencies)
- Clear semantic ownership (format concerns separated from infrastructure)

## Design Decisions

### New Package vs Reusing Service

**Chosen:** Create new `format` package

**Cost:** One additional package in codebase

**Benefit:** Clean separation of cross-cutting format concerns from service layer infrastructure. Format specifications are not service implementations - they are shared domain vocabulary.

**Alternative cost:** Placing `CopierFormat` in `service` would make snes layer depend on service package, creating semantic confusion (header generators are not service consumers).

**Rationale:** Format specifications are domain knowledge, not infrastructure. Dedicated package provides clearest ownership and prevents future circular dependency reintroduction.

### Package Naming

**Chosen:** `format` (not `formats`, `copier`, or `specification`)

**Cost:** Potential ambiguity with Java's built-in formatting utilities

**Benefit:** Concise, matches domain language ("copier format"), singular follows Java convention (like `java.io`, not `java.ios`)

**Rationale:** Context (backup unit formats) makes meaning unambiguous within this codebase.

## Invariants

**No Dependencies:** The `format` package must not import from any other application package. It defines vocabulary, not behavior. Violation check: `grep "^import com.largomodo.floppyconvert" src/main/java/de/nrq/floppyconvert/format/*.java` must return no results (excluding test files).

**Single Enum:** This package contains exactly one enum (`CopierFormat`). If additional format-related types are needed, create new packages (e.g., `format.encoding`) rather than expanding this package's scope.

## Null Handling Patterns

`CopierFormat.fromFileExtension()` checks `extension == null || extension.isEmpty()` explicitly because it accepts external input that may be null (public API contract). Callers using File.getName() as input source (guaranteed non-null per JDK contract) need no explicit null checking. This pattern difference reflects different input sources: external API boundary vs internal JDK guarantee.

When adding future extension-based methods to `CopierFormat`, maintain explicit null checking to preserve public API safety.
