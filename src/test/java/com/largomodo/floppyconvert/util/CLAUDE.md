# util/ (tests)

## Files

| File                          | What                                                                                                          | When to read                                                              |
| ----------------------------- | ------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| `DosNameTest.java`            | `DosName` value-type tests: construction, sanitization parity, equality, extension preservation               | Verifying DOS 8.3 name invariant, debugging sanitization behavior         |
| `SnesRomMatcherTest.java`     | `isRom` tests for supported extensions and Game Doctor naming, directory rejection                             | Verifying ROM detection, adding new extension matchers                    |
