package com.largomodo.floppyconvert.service;

import com.largomodo.floppyconvert.format.CopierFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DefaultConversionFacade delegation behavior.
 * Verifies facade correctly delegates to underlying services without additional logic.
 */
class DefaultConversionFacadeTest {

    private RomSplitter mockSplitter;
    private FloppyImageWriter mockWriter;

    @BeforeEach
    void setUp() {
        mockSplitter = mock(RomSplitter.class);
        mockWriter = mock(FloppyImageWriter.class);
    }

    @Test
    void testSplitRomDelegatesToRomSplitter() throws IOException {
        // Arrange
        File inputRom = new File("test.sfc");
        Path workDir = Path.of("/tmp/work");
        CopierFormat format = CopierFormat.SWC;
        List<File> expectedParts = List.of(new File("test.1"), new File("test.2"));

        when(mockSplitter.split(inputRom, workDir, format)).thenReturn(expectedParts);

        DefaultConversionFacade facade = new DefaultConversionFacade(mockSplitter, mockWriter);

        // Act
        List<File> actualParts = facade.splitRom(inputRom, workDir, format);

        // Assert
        verify(mockSplitter).split(inputRom, workDir, format);
        assert actualParts == expectedParts;
    }

    @Test
    void testWriteDelegatesToFloppyImageWriter() throws IOException {
        // Arrange
        File targetImage = new File("disk.img");
        List<File> sourceParts = List.of(new File("test.1"), new File("test.2"));
        Map<File, String> dosNames = Map.of(new File("test.1"), "TEST.1", new File("test.2"), "TEST.2");

        DefaultConversionFacade facade = new DefaultConversionFacade(mockSplitter, mockWriter);

        // Act
        facade.write(targetImage, sourceParts, dosNames);

        // Assert
        verify(mockWriter).write(targetImage, sourceParts, dosNames);
    }

    @Test
    void testExceptionsPropagateFromSplitter() throws IOException {
        // Arrange
        File inputRom = new File("test.sfc");
        Path workDir = Path.of("/tmp/work");
        CopierFormat format = CopierFormat.SWC;
        IOException expectedException = new IOException("Split failed");

        when(mockSplitter.split(inputRom, workDir, format)).thenThrow(expectedException);

        DefaultConversionFacade facade = new DefaultConversionFacade(mockSplitter, mockWriter);

        // Act & Assert
        assertThrows(IOException.class, () -> facade.splitRom(inputRom, workDir, format));
    }

    @Test
    void testExceptionsPropagateFromWriter() throws IOException {
        // Arrange
        File targetImage = new File("disk.img");
        List<File> sourceParts = List.of(new File("test.1"));
        Map<File, String> dosNames = Map.of(new File("test.1"), "TEST.1");
        IOException expectedException = new IOException("Write failed");

        doThrow(expectedException).when(mockWriter).write(targetImage, sourceParts, dosNames);

        DefaultConversionFacade facade = new DefaultConversionFacade(mockSplitter, mockWriter);

        // Act & Assert
        assertThrows(IOException.class, () -> facade.write(targetImage, sourceParts, dosNames));
    }
}
