# fat/

Native Java FAT12 disk-image generation and writing.

## Files

| File                       | What                                                                                                                                                                                                                                                                                              | When to read                                                                                          |
| -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------- |
| `Fat12FormatFactory.java`  | `DiskTemplateFactory` impl that constructs MS-DOS Boot Sector with BPB and initializes dual FAT tables; `sectorsPerFat` precomputed (not runtime); `writeBootSector` decomposed into 4 helpers (jumpCodeAndOem, BPB, extended boot record, signature); eliminates `.img` binary resources         | Debugging dynamic image generation, understanding BPB structure, troubleshooting FAT initialization, adding non-standard geometries |
| `Fat12ImageWriter.java`    | Native FAT12 writer: memory-mapped I/O, little-endian `ByteBuffer`, BPB parsing, cluster chaining, FAT mirroring                                                                                                                                                                                  | Debugging FAT12 write operations, understanding native implementation                                 |
