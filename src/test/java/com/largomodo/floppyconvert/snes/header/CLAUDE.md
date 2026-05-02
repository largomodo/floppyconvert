# header/ (tests)

## Files

| File                                  | What                                                                                                                                  | When to read                                                  |
| ------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------- |
| `SwcSramEncoderTest.java`             | Byte 2 encoding (`0x0C`/`0x08`/`0x04`/`0x00` for 256KB/64KB/16KB/0KB)                                                                  | Verifying SWC SRAM encoding strategy                          |
| `FigSramEncoderTest.java`             | Byte 3 encoding (same code mapping as SWC)                                                                                              | Verifying FIG SRAM encoding strategy                          |
| `UfoSramEncoderTest.java`             | Byte 4 size codes, bytes 5-6 mapping controls                                                                                          | Verifying UFO SRAM encoding strategy                          |
| `Gd3SramEncoderTest.java`             | HiROM byte 7, LoROM byte 2, DSP flag handling                                                                                          | Verifying GD3 SRAM encoding strategy                          |
| `SwcHeaderGeneratorTest.java`         | Property-based: `partSize` validation, emulation mode, multi-file flags, block-count calc, `chunkFlag` ignored                          | Verifying SWC header correctness, edge cases                  |
| `FigHeaderGeneratorTest.java`         | Property-based: `partSize` validation, HiROM flags, multi-file flags, DSP encoding, block-count calc, `chunkFlag` ignored                | Verifying FIG header correctness, edge cases                  |
| `UfoHeaderGeneratorTest.java`         | Property-based: `partSize` validation, dual-source size encoding (`partSize` for bytes 0-1, total for byte 17), bank type, `chunkFlag` for byte 2 | Verifying UFO dual-source encoding and `chunkFlag` behavior |
| `Gd3HeaderGeneratorTest.java`         | Property-based: `partSize` ignored (uses total ROM size), first-part-only headers, memory-map table selection, `chunkFlag` ignored      | Verifying GD3 first-part-only behavior, edge cases            |
| `HeaderGeneratorFactoryTest.java`     | Format-based generator selection                                                                                                       | Verifying factory logic, adding format support                |
