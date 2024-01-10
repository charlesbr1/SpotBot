package org.sbot.utils;

import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Properties;

import static java.util.Objects.requireNonNull;

public interface PropertiesReader {

    String get(@NotNull String name);

    default int getInt(@NotNull String name) {
        return Integer.parseInt(get(name));
    }

    @NotNull
    static PropertiesReader loadProperties(@NotNull String filePath) {
        LogManager.getLogger(PropertiesReader.class).debug("Loading properties file {}", filePath);
        try (InputStream input = new FileInputStream(requireNonNull(filePath, "missing properties file path"))) {
            Properties properties = new Properties();
            properties.load(input);
            return name -> readProperty(name, properties);
        } catch (IOException e) {
            LogManager.getLogger(PropertiesReader.class).error("Failed to load properties file " + filePath, e);
            throw new IllegalArgumentException(e);
        }
    }

    @NotNull
    private static String readProperty(@NotNull String name, @NotNull Properties properties) {
        LogManager.getLogger(PropertiesReader.class).debug("Reading property {}", name);
        return Optional.ofNullable(properties.getProperty(name))
                .orElseThrow(() -> new IllegalArgumentException("Missing property " + name));
    }

    @NotNull
    static String readFile(@NotNull String filePath) {
        try {
            return Files.readString(Paths.get(requireNonNull(filePath, "missing properties file path")));
        } catch (IOException e) {
            LogManager.getLogger(PropertiesReader.class).debug("Failed to read file {}", filePath);
            throw new RuntimeException(e);
        }
    }
}
