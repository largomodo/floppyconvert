# domain/

Immutable records describing ROM-part metadata and disk layouts, plus the bin-packing algorithm.

## Files

| File                     | What                                                                                          | When to read                                                                |
| ------------------------ | --------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| `RomPartMetadata.java`   | Domain record: `(path, size, dosName)` with null validation; `RomPartMetadata.of(path, size, dosName)` static factory validates `dosName` via `DosName.of()` at the production boundary | Understanding `DiskPacker` input format, debugging validation errors, tracing DOS-name invariant enforcement |
| `DiskLayout.java`        | Domain record: list of parts plus `FloppyType` for one disk                                   | Understanding `DiskPacker` output format                                    |
| `DiskPacker.java`        | Bin-packing algorithm interface                                                                | Adding alternative packing strategies                                       |
| `GreedyDiskPacker.java`  | First-fit decreasing bin-packing implementation                                                | Debugging disk capacity issues, modifying packing algorithm                 |
