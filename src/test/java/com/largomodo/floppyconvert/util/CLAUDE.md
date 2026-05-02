# util/ (tests)

## Files

| File                          | What                                                                                                          | When to read                                                              |
| ----------------------------- | ------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| `DosNameUtilTest.java`        | DOS 8.3 sanitization tests: truncation, special characters, extension preservation                            | Verifying filename truncation behavior, debugging name normalization      |
| `SnesRomMatcherTest.java`     | `isRom` tests for supported extensions and Game Doctor naming, directory rejection                             | Verifying ROM detection, adding new extension matchers                    |
