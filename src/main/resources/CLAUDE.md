# resources/

Production runtime resources: logging config, Picocli resource bundle, GraalVM native-image metadata.

## Files

| File           | What                                                                                                                                              | When to read                                                                              |
| -------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------- |
| `logback.xml`  | Logback config: three appenders (STDOUT / FILE / FAILURES), MDC pattern `%X{rom}`, INFO default level, `ThresholdFilter` on STDOUT, `LevelFilter` on FAILURES | Modifying log output format, changing log levels, debugging logging issues                |

## Subdirectories

| Directory          | What                                                              | When to read                                                                |
| ------------------ | ----------------------------------------------------------------- | --------------------------------------------------------------------------- |
| `floppyconvert/`   | Picocli resource bundle (Maven-filtered)                          | Changing CLI metadata in `--help`, debugging missing property injection     |
| `META-INF/`        | GraalVM native-image metadata, namespaced by groupId/artifactId   | Debugging native-image runtime errors, registering reflection for new libs  |
