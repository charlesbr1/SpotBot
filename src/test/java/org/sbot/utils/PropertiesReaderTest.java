package org.sbot.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PropertiesReaderTest {

    @Test
    void loadProperties() {
        PropertiesReader propertiesReader;
        assertThrows(IllegalArgumentException.class, () -> PropertiesReader.loadProperties("src/test/resources/badpath"));
        assertNotNull(propertiesReader = PropertiesReader.loadProperties("src/test/resources/test.properties"));
        assertThrows(IllegalArgumentException.class, () -> propertiesReader.get("badValue"));
        assertEquals("abc", propertiesReader.get("string.property"));
        assertEquals(3, propertiesReader.getIntOr("int.property", 3));
        assertEquals(7, propertiesReader.getIntOr("badValue", 7));
    }

    @Test
    void readFile() {
        assertEquals("12345", PropertiesReader.readFile("src/test/resources/test.file"));
        assertThrows(IllegalArgumentException.class, () -> PropertiesReader.readFile("src/test/resources/badpath"));
    }
}