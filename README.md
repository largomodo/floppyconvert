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
| GD3 | Game Doctor SF3 | SF-Code (`.078`) | `SF16CHRXA.078`, `SF16CHRXB.078` |

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

