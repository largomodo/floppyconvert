# floppyconvert/ (native-image metadata)

## Files

| File                  | What                                                                                                                                                                                              | When to read                                                                          |
| --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------- |
| `reflect-config.json` | GraalVM reflection registrations: Logback `ThresholdFilter` / `LevelFilter`, `UnsynchronizedAppenderBase.addFilter()`, `Level` and `FilterReply` (needed for Logback XML property resolution)     | Debugging GraalVM native-image runtime errors, adding reflection metadata for new libs |
