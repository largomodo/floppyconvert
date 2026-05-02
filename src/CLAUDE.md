# src/

Maven standard layout: production code under `main/`, tests and fixtures under `test/`.

## Subdirectories

| Directory | What                                                                          | When to read                                                                  |
| --------- | ----------------------------------------------------------------------------- | ----------------------------------------------------------------------------- |
| `main/`   | Production source code and runtime resources                                  | Modifying production code, debugging conversion behavior, configuring logging |
| `test/`   | Unit, property-based, integration, and E2E tests with synthetic-ROM fixtures  | Adding tests, debugging test failures, understanding test ROM provisioning    |
