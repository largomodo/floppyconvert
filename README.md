# FloppyConvert

FloppyConvert is a CLI utility designed to automate the conversion of SNES ROM files (`.sfc`, `.fig`, `.smw`, `.swc`, `.ufo`) into FAT12 floppy disk images compatible with vintage backup units.

It uses a native Java ROM splitting engine to generate format-specific split files (FIG, SWC, UFO, GD3) with accurate copier headers, then packages them into FAT12 floppy images using a custom FAT12 writer. The tool supports recursive directory scanning, concurrent batch processing, and intelligent workspace management.

## Prerequisites

### Build Dependencies
*   Java Development Kit (JDK) 21 or higher.
*   Apache Maven.

## Installation

### Pre-built Binaries
Native executables for Windows, Linux, and macOS are automatically generated via GitHub Actions. Please refer to the **Releases** section of this repository to download the artifact appropriate for your system.

### Building from Source
To compile the project and generate the JAR artifact:

```bash
mvn clean package
```

This will produce `target/floppyconvert-1.0-SNAPSHOT.jar`.

## Usage

For detailed command-line arguments and format options, please refer to the [Manpage Documentation](floppyconvert.adoc).

### Basic Execution
Run the compiled JAR using the Java 21 runtime:

```bash
# Convert a single file (defaults output to current directory)
java -jar target/floppyconvert-1.0-SNAPSHOT.jar game.sfc

# Recursive batch conversion with explicit output directory
java -jar target/floppyconvert-1.0-SNAPSHOT.jar ./input_roms -o ./output_floppies --format SWC
```

### Native Image
If you have built the native executable (via the `native` Maven profile), you can run it directly without the JVM prefix:

```
# Linux / macOS
./floppyconvert ./roms --verbose

# Windows
floppyconvert.exe .\roms --verbose
```

## Format-Specific Examples

FloppyConvert supports four backup unit formats, each with distinct file naming conventions.

### FIG Format (Pro Fighter)

```bash
# Convert single ROM to FIG format
java -jar floppyconvert.jar game.sfc --format FIG

# Output files (for 12Mbit ROM):
# game.1  (4Mbit chunk with FIG header)
# game.2  (4Mbit chunk with FIG header)
# game.3  (4Mbit chunk with FIG header)
```

**Naming:** `.1`, `.2`, `.3`, ... (numeric extensions)

**Headers:** All parts receive FIG headers (unlike GD3)

### SWC Format (Super Wild Card)

```bash
# Convert single ROM to SWC format
java -jar floppyconvert.jar game.sfc --format SWC

# Output files (for 8Mbit ROM):
# game.1  (4Mbit chunk with SWC header)
# game.2  (4Mbit chunk with SWC header)
```

**Naming:** `.1`, `.2`, `.3`, ... (numeric extensions)

**Headers:** All parts receive SWC headers

### UFO Format (Super UFO)

```bash
# Convert single ROM to UFO format
java -jar floppyconvert.jar game.sfc --format UFO

# Output files (for 12Mbit ROM):
# game.1gm  (4Mbit chunk with UFO header)
# game.2gm  (4Mbit chunk with UFO header)
# game.3gm  (4Mbit chunk with UFO header)
```

**Naming:** `.1gm`, `.2gm`, `.3gm`, ... (numeric prefix + "gm" extension)

**Headers:** All parts receive UFO headers with dual-source size encoding (per-part blocks in bytes 0-1, total Mbit in byte 17)

### GD3 Format (Game Doctor SF3)

```bash
# Convert single ROM to GD3 format
java -jar floppyconvert.jar game.sfc --format GD3

# Output files (for 16Mbit HiROM "Chrono Trigger"):
# SF16CHRXA.078  (8Mbit chunk with GD3 header)
# SF16CHRXB.078  (8Mbit chunk, no header)
```

**Naming:** SF-Code format (SF + Mbit + 3-char name + suffix + .078)

**SF-Code Examples:**

| ROM Title | Size | Type | Output Files |
|-----------|------|------|--------------|
| Super Mario World | 4Mbit | LoROM | `SF4SUP__.078` (single file, underscore-padded) |
| Chrono Trigger | 16Mbit | HiROM | `SF16CHRXA.078`, `SF16CHRXB.078` (X-padding for HiROM ≤16Mbit) |
| Star Fox | 8Mbit | LoROM | `SF8STA__.078` (single file) |
| Tales of Phantasia | 24Mbit | HiROM | `SF24TALA.078`, `SF24TALB.078`, `SF24TALC.078` (no X-padding, >16Mbit) |

**Headers:** First part only (subsequent .078 files contain raw ROM data)

**Interleaving:** HiROM ROMs are automatically interleaved before splitting (required for Game Doctor hardware)

## Architecture Rationale

### Why Native ROM Splitting?

FloppyConvert uses a native Java ROM splitting engine instead of external tools like ucon64. This design decision provides:

- **Precise header control:** Direct byte-level control over copier headers ensures exact hardware compatibility
- **Format flexibility:** Easy to add new copier formats without external tool dependencies
- **Portable builds:** Single JAR contains all conversion logic (no external binaries required)
- **Test reliability:** Property-based tests validate headers across thousands of synthetic ROMs

### Why Format-Specific Naming?

Each backup unit format uses different naming conventions because the hardware expects specific patterns:

- **FIG/SWC:** Simple numeric extensions (.1, .2, ...) match original DOS-era tooling
- **UFO:** `.gm` extension identifies Game Master format files for hardware recognition
- **GD3:** SF-Code format encodes ROM metadata (size, title, type) in filename for Game Doctor's file browser

The SF-Code format is particularly complex because it encodes:
- Total ROM size in Mbit (not per-part size)
- Sanitized 3-character ROM title
- HiROM/LoROM type via X-padding rules
- Multi-file sequence letters

This filename encoding allows Game Doctor hardware to display ROM information without reading file contents.

### Why Dual-Source Size Encoding (UFO)?

UFO format headers use two different size values in the same header:
- **Bytes 0-1:** Per-part size in 8KB blocks (for this specific split part)
- **Byte 17:** Total ROM size in Mbit (for entire ROM)

This dual-source requirement exists because UFO hardware uses:
- Per-part blocks for loading individual files from floppy
- Total Mbit for internal memory mapping configuration

Encoding only total size (like GD3) would break per-part loading. Encoding only per-part size would break memory mapping. Both are required.

### Why X-Padding for HiROM ≤16Mbit?

Game Doctor SF3 hardware expects X-padding in SF-Code filenames for HiROM ROMs ≤16Mbit in multi-file splits:
- Correct: `SF16CHRXA.078`, `SF16CHRXB.078`
- Wrong: `SF16CHRA.078`, `SF16CHRB.078`

This quirk exists because early Game Doctor firmware used X-padding to distinguish HiROM from LoROM in its file browser. The condition must be `<=` not `<` because exactly 16Mbit HiROM ROMs require X-padding.

Off-by-one errors here cause hardware to misdetect ROM type and fail to load.

### Why First-Part-Only Headers (GD3)?

GD3 format only includes headers in the first .078 file. Subsequent parts contain raw ROM data without headers.

This design matches Game Doctor hardware behavior:
- First file is loaded by hardware bootloader (reads header for memory map configuration)
- Subsequent files are loaded by first file's code (raw data only)

Adding headers to all parts (like FIG does) would break compatibility because Game Doctor expects raw data in parts > 0.
```