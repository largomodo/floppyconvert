# FloppyConvert

A CLI tool that converts SNES ROM files into FAT12 floppy disk images for vintage backup units (Pro Fighter, Super Wild Card, Super UFO, Game Doctor SF3).

## Prerequisites

- JDK 21+
- Apache Maven

## Building

```bash
mvn clean package
```

Pre-built native executables are available in the **Releases** section.

## Usage

See the [manpage](floppyconvert.adoc) for full documentation.

```bash
# Single file conversion
java -jar target/floppyconvert-1.0-SNAPSHOT.jar game.sfc

# Batch conversion with format selection
java -jar target/floppyconvert-1.0-SNAPSHOT.jar ./roms -o ./output --format SWC

# Native executable
./floppyconvert game.sfc --format GD3 --verbose
```

## Supported Formats

### FIG / SWC

Split into 4Mbit chunks with numeric extensions.

```
game.1    game.2    game.3
```

### UFO

Split into 4Mbit chunks (LoROM) or irregular chunks (HiROM) with `.gm` extensions.

```
game.1gm    game.2gm    game.3gm
```

### GD3

Split into 8Mbit chunks using SF-Code filenames (`SF` + size + 3-char title + sequence + `.078`). HiROM data is interleaved before splitting. Only the first part carries a header.

```
SF4SUP__.078                              (4Mbit LoROM, single file)
SF8AXEXA.078  SF8AXEXB.078                (8Mbit HiROM, force-split to 4Mbit)
SF16CHRA.078  SF16CHRB.078  ...           (16Mbit HiROM, force-split to 4Mbit)
SF32TALA.078  SF32TALB.078  ...           (32Mbit HiROM, 8Mbit chunks)
```

## Testing

Tests use synthetic ROMs by default. For higher-fidelity testing, place real ROM files in `src/test/resources/snes/` — the test suite detects and uses them automatically.
