# Floppy Conversion Tool

CLI that converts SNES ROM files into FAT12 floppy disk images for vintage backup units (FIG / SWC / UFO / GD3).

## Files

| File                  | What                                                                                                                                                                                  | When to read                                                                                       |
| --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------- |
| `pom.xml`             | Maven build, Java 21, dependency versions (Picocli, JUnit, jqwik, SLF4J, Logback), `native` profile with GraalVM native-maven-plugin and `picocli-codegen` annotation processor       | Modifying dependencies, build settings, JAR manifest, or GraalVM native-image build configuration  |
| `README.md`           | Project overview, install/build/usage instructions, supported formats, real-ROM SHA1 checksums                                                                                        | Running the tool, understanding format split-naming, verifying ROM fixtures                        |
| `floppyconvert.adoc`  | Picocli-generated AsciiDoc manpage: synopsis, options, exit codes                                                                                                                     | Publishing the manpage, documenting CLI options                                                    |
| `.gitignore`          | Build artifacts and IDE noise excluded from git                                                                                                                                       | Adding patterns to ignore                                                                          |

## Subdirectories

| Directory      | What                                                                                | When to read                                                                              |
| -------------- | ----------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------- |
| `src/`         | Maven standard layout (`main/` for production, `test/` for tests and fixtures)      | Modifying production code, adding tests, debugging behavior                               |
| `.github/`     | GitHub Actions workflows for CI and native-image release builds                     | Modifying CI, adjusting release artifact builds                                           |

## Build

```bash
mvn clean package
```

Produces `target/floppyconvert-1.0-SNAPSHOT.jar` (shaded). Use the `native` profile for GraalVM native image:

```bash
mvn -Pnative -DskipTests package
```

## Test

```bash
mvn test
```

Tests use synthetic ROMs by default; real ROM fixtures are picked up automatically when present (see `README.md` for SHA1 list).
