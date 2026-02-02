package com.largomodo.floppyconvert.snes.header;

import com.largomodo.floppyconvert.format.CopierFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HeaderGeneratorFactoryTest {

    @Test
    void testSwcFormatReturnsCorrectGeneratorType() {
        HeaderGeneratorFactory factory = new HeaderGeneratorFactory();
        HeaderGenerator generator = factory.get(CopierFormat.SWC);
        assertInstanceOf(SwcHeaderGenerator.class, generator);
    }

    @Test
    void testFigFormatReturnsCorrectGeneratorType() {
        HeaderGeneratorFactory factory = new HeaderGeneratorFactory();
        HeaderGenerator generator = factory.get(CopierFormat.FIG);
        assertInstanceOf(FigHeaderGenerator.class, generator);
    }

    @Test
    void testUfoFormatReturnsCorrectGeneratorType() {
        HeaderGeneratorFactory factory = new HeaderGeneratorFactory();
        HeaderGenerator generator = factory.get(CopierFormat.UFO);
        assertInstanceOf(UfoHeaderGenerator.class, generator);
    }

    @Test
    void testGd3FormatReturnsCorrectGeneratorType() {
        HeaderGeneratorFactory factory = new HeaderGeneratorFactory();
        HeaderGenerator generator = factory.get(CopierFormat.GD3);
        assertInstanceOf(Gd3HeaderGenerator.class, generator);
    }
}
