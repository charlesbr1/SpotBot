package org.sbot.utils;

import org.apache.logging.log4j.LogManager;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Properties;

public interface PropertiesReader {

    String get(String name);

    static PropertiesReader loadProperties(String path) {
        LogManager.getLogger(PropertiesReader.class).debug("Loading properties file {}", path);
        try (InputStream input = new FileInputStream(path)) {
            Properties properties = new Properties();
            properties.load(input);
            return name -> readProperty(name, properties);
        } catch (IOException e) {
            LogManager.getLogger(PropertiesReader.class).error("Failed to load properties file " + path, e);
            throw new IllegalArgumentException(e);
        }
    }

    private static String readProperty(String name, Properties properties) {
        LogManager.getLogger(PropertiesReader.class).debug("Reading property {}", name);
        return Optional.ofNullable(properties.getProperty(name))
                .orElseThrow(() -> new IllegalArgumentException("Missing property " + name));
    }

    static String readFile(String filePath) {
        try {
            return Files.readString(Paths.get(filePath));
        } catch (IOException e) {
            LogManager.getLogger(PropertiesReader.class).debug("Failed to read file {}", filePath);
            throw new RuntimeException(e);
        }
    }
}
