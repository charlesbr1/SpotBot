package org.sbot.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PropertiesReaderTest {

    @Test
    void getOr() {
        PropertiesReader propertiesReader = PropertiesReader.loadProperties("src/test/resources/test.properties");
        assertNotNull(propertiesReader);
        assertEquals("abc", propertiesReader.get("string.property"));
        assertEquals("other", propertiesReader.getOr("missing", "other"));
        assertNull(propertiesReader.getOr("missing", null));
    }

    @Test
    void getIntOr() {
        PropertiesReader propertiesReader = PropertiesReader.loadProperties("src/test/resources/test.properties");
        assertNotNull(propertiesReader);
        assertEquals(3, propertiesReader.getIntOr("int.property", 3));
        assertEquals(7, propertiesReader.getIntOr("badValue", 7));
    }

    @Test
    void loadProperties() {
        assertThrows(NullPointerException.class, () -> PropertiesReader.loadProperties(null));
        assertThrows(IllegalArgumentException.class, () -> PropertiesReader.loadProperties("src/test/resources/badpath"));
        PropertiesReader propertiesReader = PropertiesReader.loadProperties("src/test/resources/test.properties");
        assertNotNull(propertiesReader);
        assertThrows(NullPointerException.class, () -> propertiesReader.get(null));
        assertThrows(IllegalArgumentException.class, () -> propertiesReader.get("badValue"));
        assertEquals("abc", propertiesReader.get("string.property"));
    }

    @Test
    void readFile() {
        assertEquals("12345", PropertiesReader.readFile("src/test/resources/test.file"));
        assertThrows(IllegalArgumentException.class, () -> PropertiesReader.readFile("src/test/resources/badpath"));
    }
}