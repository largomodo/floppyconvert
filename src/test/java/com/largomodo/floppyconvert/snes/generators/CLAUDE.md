# generators/ (test ROM utilities)

Synthetic SNES ROM generation used by both unit tests and the E2E fallback path.

## Files

| File                              | What                                                                                                                                                                                                              | When to read                                                                              |
| --------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------- |
| `SyntheticRomFactory.java`        | Synthetic ROM generator: 5-param overloads with custom titles (`generateLoRom` / `generateHiRom` / `generateExHiRom`), 4-param overloads with auto-generated unique titles (`SYNLO{size}T{sram}{dsp}` pattern); ExHiROM (`0x40FFB0` header, map mode `0x25`, 48-64 Mbit); `generateTestSuite()` for pre-generated ROMs; satisfies `checksum + complement = 0xFFFF` | Adding synthetic ROM variants, debugging ROM generation, generating test-suite ROMs       |
| `RomDataGenerator.java`           | jqwik data generator: synthetic SNES ROM byte arrays with proper LoROM/HiROM headers                                                                                                                              | Adding property-based test data sources, extending ROM data generation                    |
| `SyntheticRomFactoryTest.java`    | Custom title placement at correct header offsets, auto-title pattern validation, ExHiROM header at `0x40FFB0`, 64 Mbit max, invalid-size error handling                                                            | Verifying synthetic ROM generation, adding generation test cases                          |
