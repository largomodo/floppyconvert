# FloppyConvert

A CLI tool that converts SNES ROM files into FAT12 floppy disk images for vintage backup units (Pro Fighter, Super Wild Card, Super UFO, Game Doctor SF3).

## Prerequisites

- JDK 21+
- Apache Maven

## Installation

### Pre-built Binaries
Native executables for Windows, Linux, and macOS are automatically generated via GitHub Actions. Please refer to the **[Releases](https://github.com/largomodo/floppyconvert/releases/latest)** section of this repository to download the artifact appropriate for your system.

### Building from Source
To compile the project and generate the JAR artifact:

```bash
mvn clean package
```

This will produce `target/floppyconvert-1.0-SNAPSHOT.jar`.

## Usage

For detailed command-line arguments and format options, please refer to the [Manpage Documentation](floppyconvert.adoc).

### Native Image
The native executable (from [releases](https://github.com/largomodo/floppyconvert/releases/latest) or built with GraalVM via the `native` Maven profile), runs directly without the JVM prefix:

```
# Linux / macOS
./floppyconvert ./roms --verbose

# Windows
floppyconvert.exe .\roms --verbose
```

See the [manpage](floppyconvert.adoc) for full documentation.

### .jar Basic Execution
Run the compiled JAR using the Java 21 runtime:

```bash
# Convert a single file (defaults output to current directory)
java -jar target/floppyconvert-1.0-SNAPSHOT.jar game.sfc

# Recursive batch conversion with explicit output directory
java -jar target/floppyconvert-1.0-SNAPSHOT.jar ./input_roms -o ./output_floppies --format SWC
```

## How It Works

FloppyConvert splits SNES ROMs into format-specific chunks with the appropriate copier headers, then packs those chunks into FAT12 floppy disk images (`.img`). The floppy size (720KB, 1.44MB, or 1.6MB) is selected automatically based on the chunk sizes. When a ROM spans multiple disks, images are numbered sequentially (`game_1.img`, `game_2.img`, ...); single-disk ROMs produce a single `game.img`.

## Supported Formats

| Format | Backup Unit | Split Naming | Example |
|--------|-------------|--------------|---------|
| FIG | Pro Fighter | `.1`, `.2`, `.3` | `game.1`, `game.2` |
| SWC | Super Wild Card | `.1`, `.2`, `.3` | `game.1`, `game.2` |
| UFO | Super UFO | `.1gm`, `.2gm`, `.3gm` | `game.1gm`, `game.2gm` |
| GD3 | Game Doctor SF3/SF6/SF7 | SF-Code (`.078`) | `SF16CHRA.078`, `SF16CHRB.078` |

### Game Doctor (GD3)

GD3 output is byte-identical to [uCON64](https://ucon64.sourceforge.io/) 2.2.3 (`ucon64 --gd3` followed by `ucon64 -s`) for every ROM uCON64 converts: 512-byte header on the first part, padded and interleaved body, parts cut as uCON64 cuts them. Three classes uCON64 cannot convert are handled in addition: 96 Mbit pre-decompressed builds (Star Ocean, Street Fighter Alpha 2), files that already carry a Game Doctor header, and interleaved input.

| ROM | Sizes | Padding and interleave | Parts |
|-----|-------|------------------------|-------|
| HiROM | 2-3 Mbit | rounded up to 1 Mbit, interleaved | two halves |
| HiROM | 4, 8 Mbit | none, interleaved | two halves |
| HiROM | 10 Mbit | padded to 12 Mbit, the holes mirror the half before them | two 6 Mbit halves |
| HiROM | 12, 16 Mbit | none, interleaved | two halves |
| HiROM | 20 Mbit | padded to 24 Mbit (mirrored), interleaved in a 16 Mbit and an 8 Mbit region | three 8 Mbit parts |
| HiROM | 24 Mbit | none, two-region interleave | three 8 Mbit parts |
| HiROM | 28 Mbit | zero-padded to 32 Mbit | four 8 Mbit parts |
| HiROM | 32 Mbit | none, interleaved | four 8 Mbit parts |
| Extended HiROM (header 4 MB in) | 40, 48, 64 Mbit | none, expansion placed before the 32 Mbit base | 8 Mbit parts; 40 Mbit follows uCON64 but is untested on hardware |
| LoROM | up to 32 Mbit | none, never padded or interleaved | 8 Mbit parts |
| Extended LoROM | 40-64 Mbit | none | 8 Mbit parts |
| LoROM | 96 Mbit | none; needs `--gd3-dram 128` | twelve 8 Mbit parts |

The 10, 12, 20 and 28 Mbit HiROM layouts follow uCON64 and replace the layouts earlier versions wrote for those sizes, some of which may have booted on a Game Doctor SF7. They are unverified on hardware: re-test those sizes on an SF7 before relying on them.

The 96 Mbit Star Ocean header sets its SRAM bytes (header byte 0x29 = 0x08 and map entry 19 = 0x00) from a single Game Doctor file. Saving and loading on an SF7 is untested: a Star Ocean conversion may boot but fail to save. Street Fighter Alpha 2 has no SRAM.

Other sizes are refused with uCON64's wording (for example `ROM size is 32.00001 Mbit -- conversion not yet implemented/verified`). A ROM needs at most 16 parts of 4 Mbit; the 96 Mbit class is the exception.

`--gd3-dram {32,64,128}` sets the DRAM of the target Game Doctor in Mbit (default 64). A ROM whose padded size exceeds it fails with category `HARDWARE`; the 96 Mbit class needs 128, the capacity of the SF7. DRAM only decides whether a ROM is accepted; the layout of the output does not depend on it. Other values are a usage error (exit code 2).

Part names follow uCON64: `sf`, the size in Mbit of the padded image, and the first three characters of the input file name (a space or `.` after the size digits becomes `_`), cut to 6 characters below 10 Mbit and 7 from 10 Mbit. A single part has no letter (`SF4SUP.078`). Several parts get a letter, after an `X` for HiROM below 10 Mbit and after `_` for LoROM below 10 Mbit (`SF8ABCXA.078`, `SF12ACTA.078`). uCON64 gives both parts of a multi-part LoROM below 10 Mbit the same name, so the second would overwrite the first; the `_` keeps them apart, the only place where a name differs from uCON64's.

Input that is already a Game Doctor file (`GAME DOCTOR SF 3` header) is split as it is, with its own header on part A. Interleaved input is detected as uCON64 detects it; interleaved extended HiROM (48 Mbit) is recognised only for the ROMs on uCON64's CRC list (Tales of Phantasia, Dai Kaiju Monogatari 2, Trials of Mana).

A ROM whose SNES checksum and complement do not sum to 0xFFFF is refused with category `CHECKSUM` in every format; FIG, SWC and UFO refuse the same ROMs. Copier headers on the input are dropped; uCON64's NSRT header block is not carried over.

### Batch failures

In batch mode a ROM that fails is logged to `failures.log` as `FAILED: <path> - <CATEGORY> - <message>`. The category is one of `CHECKSUM` (invalid SNES checksum/complement pair), `SIZE` (no copier converts this size), `HARDWARE` (a larger `--gd3-dram` would convert it), `HEADER` (no internal header) or `OTHER`.

## Testing

Tests use synthetic ROMs by default. The test suite uses real SNES ROM files when available for hardware compatibility testing. The expected SHA1 checksums are:

| ROM File | SHA1 Checksum |
|----------|---------------|
| ActRaiser 2 (USA).sfc | `17c086f3418f7f51e5472270d756ec5112914b83` |
| Art of Fighting (USA).sfc | `db0ed085bd28bf58ec050e6eb950471163f8367e` |
| Breath of Fire (USA).sfc | `b8a9e3023b92e0f4139428f6d7a9e0f9db70f60e` |
| Chrono Trigger (USA).sfc | `de5822f4f2f7a55acb8926d4c0eaa63d5d989312` |
| Street Fighter II Turbo (USA) (Rev 1).sfc | `9f6e8f2585e60bd6690c068c692ac97653bae6a6` |
| Super Bomberman 2 (USA).sfc | `14e4d0b3d00fd04f996eea86daa485a35e501853` |
| Super Mario World (USA).sfc | `6b47bb75d16514b6a476aa0c73a683a2a4c18765` |

GD3 parity tests (`Gd3ParityTest`) also use these local-only inputs, resolved from `src/test/resources/snes` and skipped when absent:

| File | SHA1 Checksum |
|------|---------------|
| sf48Tal (Game Doctor file, 48 Mbit) | `4c8f2ce490a44b38a2c7ed31f74430c142f4ebf2` |
| SF96soe (Game Doctor file, 96 Mbit) | `aea309ed2e70b2eed8f6b841d0e212f825124358` |
| SF96SFA2 (Game Doctor file, 96 Mbit) | `561528d4908547158100ef26d1eb5f2759d147f3` |
| Tales of Phantasia (J) T+Eng v1.2 DeJap [LCase].sfc | `5d1f0ab7bc58ebf5423e23bc1ee9bff5a070c742` |
| Bahamut Lagoon (J) T+Eng v1.2 Near.sfc | `47c66b9e6aa85883cf642c1ca68f538f17c20760` |
| Star Ocean (J) T+Eng, SDD1 Patched v1 DeJap, SDD1 v1 Neviksti.sfc | `7dbaa0265bb5d33422808bad1041cdc8f9585eb4` |
| Trials of Mana (World) (Rev 1) (Collection of Mana).sfc | `128d966ecf656957b9b9b39128817d1c6a9cbd21` |
| Dragon Quest III - Soshite Densetsu e... (J) T+Eng v1.0c Rod Merida.sfc | `692db88fa2738d5c434848976f0eef7d916a02a7` |
| Tokimeki Memorial - Densetsu no Ki no Shita de (J) T+Eng v3 Tom.sfc | `0f1c3068bf6cbf690ba048184b57ed88dcc6e98a` |
| Wizardry VI - Kindan no Mahitsu (J) T-Eng v0.99a TiCo.sfc | `eb865c530fb7e045ac6f26463b370548491548d0` |
| Daikaijuu Monogatari II (J) T+Eng v0.90 Dynamic-Designs.sfc | `bc7517b56630c66d4c9bbc3e5125534041956f4b` |
| Fire Emblem - Thracia 776 (J) T+Eng v1.07 Lil' Manster.sfc | SHA1 not recorded (file not available when this table was written) |

### GD3 parity testing

`Gd3ParityTest` compares, per part, the name and SHA1 of floppyconvert's GD3 output with the SHA1 table in `Gd3ParityExpectations`, which was generated from uCON64 2.2.3. Parts are compared one by one because comparing reassembled bytes would hide split and naming errors. CI runs the synthetic fixtures only, so the table is the oracle there; rows built from real ROMs are skipped when the ROM is absent. When a uCON64 2.2.3 binary is present at `src/test/resources/ucon64` (Linux x86-64, ignored by `src/test/resources/.gitignore` and never committed) the same test also compares the table with live uCON64 output; the live layer is skipped unless `ucon64 --version` reports `uCON64 2.2.3`. The binary used for the committed table has SHA1 `403b8868a51565ed23c6c304d859cbc9a50c7dc4` and reports `uCON64 2.2.3 WIP UNIX (Linux)`; to rebuild it run `cd ucon64-svn/src && ./configure && make` and copy `src/ucon64` to `src/test/resources/ucon64`.

- `-Dgd3.parity.regenerate=true` prints the table source from live uCON64 (`Gd3ParityTest#regenerate`) or from the code under test (`CrossFormatStabilityTest#regenerate`) instead of asserting.
- `-Dgd3.parity.sweepDir=<dir>` converts every ROM under `<dir>` with both tools and reports mismatches as DETECTION, LAYOUT, HEADER, NAME or REJECT.

`CrossFormatStabilityTest` pins FIG, SWC and UFO output for the same fixtures, so changes to shared code cannot alter those formats.
