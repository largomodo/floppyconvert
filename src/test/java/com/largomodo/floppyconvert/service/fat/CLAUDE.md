# fat/ (tests)

## Files

| File                            | What                                                                                                                          | When to read                                                                       |
| ------------------------------- | ----------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------- |
| `Fat12FormatFactoryTest.java`   | Geometry verification (720KB / 1.44MB / 1.6MB sizes), boot signature, media descriptor, FAT init, BPB fields, `sectorsPerFat` | Verifying dynamic image structure, debugging BPB generation, adding geometry cases |
| `Fat12ImageWriterTest.java`     | Cluster allocation, FAT mirroring, directory entries, disk full scenarios                                                      | Verifying FAT12 operations, edge-case test cases                                   |
