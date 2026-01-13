package com.largomodo.floppyconvert.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Loads floppy disk templates from JAR classpath resources.
 *
 * Templates bundled at build time via maven-resources-plugin. Each createBlankDisk() call
 * opens fresh InputStream for thread-safety (shared streams would cause race conditions
 * in concurrent batch processing where multiple threads invoke simultaneously).
 */
public class ResourceDiskTemplateFactory implements DiskTemplateFactory {

    @Override
    public void createBlankDisk(FloppyType type, Path targetFile) throws IOException {
        String resourcePath = type.getResourcePath();

        // Resource path is absolute classpath reference (leading slash required)
        // Templates bundled at build time via maven-resources-plugin (see pom.xml)
        try (InputStream templateStream = getClass().getResourceAsStream(resourcePath)) {
            // Null check verifies resource exists before creating target file
            if (templateStream == null) {
                throw new IOException("Internal resource " + resourcePath +
                    " not found. Ensure application is built correctly.");
            }

            Files.copy(templateStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
