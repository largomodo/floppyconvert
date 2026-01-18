# FloppyConvert

FloppyConvert is a CLI utility designed to automate the conversion of SNES ROM files (`.sfc`, `.fig`, `.smw`, `.swc`, `.ufo`) into FAT12 floppy disk images compatible with vintage backup units.

It orchestrates `ucon64` for precise ROM splitting and utilizes a custom, native Java FAT12 engine to generate compliant `.img` files. The tool supports recursive directory scanning, concurrent batch processing, and intelligent workspace management.

## Prerequisites

### Runtime Dependencies
**Crucial:** This application requires **ucon64** to function.
1. Download the binary for your platform from [ucon64.sourceforge.io](https://ucon64.sourceforge.io/).
2. Place the `ucon64` binary either:
    * Somewhere in your system `$PATH`.
    * In the same directory as the FloppyConvert executable.
    * Or specify its location manually via the `--ucon64-path` argument.

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