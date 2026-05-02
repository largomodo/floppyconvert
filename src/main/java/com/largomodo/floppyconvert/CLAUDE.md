# floppyconvert/

Application root package containing the CLI entry point.

## Files

| File                    | What                                                                                                                                                                                                                       | When to read                                                                                                              |
| ----------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------- |
| `FloppyConvert.java`    | Picocli CLI entry point: `@Command`/`@Parameters`/`@Option`, resourceBundle for dynamic metadata injection, single positional input parameter, `--verbose` toggles STDOUT ThresholdFilter to DEBUG, smart output-dir defaults, depends only on `ConversionServiceFactory` (no concrete service classes); `call()` delegates to `configureLogging()`, `validateInput()`, `resolveOutputDirectory()`, `dispatchProcessing()` | Adding CLI arguments, changing argument defaults, debugging argument validation, modifying log verbosity behavior, understanding call() decomposition |

## Subdirectories

| Directory   | What                                                                                                       | When to read                                                                  |
| ----------- | ---------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------- |
| `core/`     | Orchestration: `RomProcessor`, `ConversionFacade` interface, domain models, packing, workspace management | Changing pipeline flow, debugging orchestration, working with domain records  |
| `format/`   | `CopierFormat` enum (FIG/SWC/UFO/GD3), shared format vocabulary with no other dependencies                 | Adding new format support, understanding the acyclic package layout           |
| `service/`  | Concrete services: `NativeRomSplitter`, `Fat12*`, `ConversionServiceFactory`, format-specific splitting    | Implementing splitter/writer behavior, understanding split-pipeline data flow |
| `snes/`     | SNES ROM parsing, validation, interleaving, header generation                                              | Debugging ROM detection, understanding ucon64-compatible split rules          |
| `util/`     | Stateless utilities: DOS name sanitization, ROM-extension matching                                         | Debugging filename handling, adding ROM extensions                            |
