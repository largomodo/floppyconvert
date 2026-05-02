# util/

Stateless utilities for filename and ROM-extension handling.

## Files

| File                  | What                                                                                                                                                                              | When to read                                                                                |
| --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------- |
| `DosName.java`        | DOS 8.3 filename value type; validates the 8.3 invariant at construction via `DosNameUtil.sanitize`; any holder is guaranteed a valid FAT12 directory-entry name                  | Passing a validated DOS name through production code, tracing the 8.3 invariant boundary    |
| `DosNameUtil.java`    | DOS 8.3 filename sanitization: truncation, special-character handling, extension preservation                                                                                     | Changing filename truncation, special-character handling, or extension preservation         |
| `SnesRomMatcher.java` | `isRom(Path)` recognizes `.sfc`, `.fig`, `.swc`, `.ufo`, `.1`, plus Game Doctor naming via regex; `Files.isRegularFile` check prevents directory false positives                  | Adding new ROM format support, debugging format detection, understanding batch filter logic |
