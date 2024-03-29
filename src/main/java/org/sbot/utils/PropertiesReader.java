package org.sbot.utils;

import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Properties;

import static java.util.Objects.requireNonNull;

public interface PropertiesReader {

    @NotNull
    String get(@NotNull String name);

    @Nullable
    default String getOr(@NotNull String name, @Nullable String defaultValue) {
        try {
            return get(name);
        } catch (RuntimeException e) {
            return defaultValue;
        }
    }

    default int getIntOr(@NotNull String name, int defaultValue) {
        try {
            return Integer.parseInt(get(name));
        } catch (RuntimeException e) {
            return defaultValue;
        }
    }

    @NotNull
    static PropertiesReader loadProperties(@NotNull String filePath) {
        LogManager.getLogger(PropertiesReader.class).debug("Loading properties file {}", filePath);
        try (InputStream input = new FileInputStream(requireNonNull(filePath, "missing properties file path"))) {
            Properties properties = new Properties();
            properties.load(input);
            return name -> readProperty(name, properties);
        } catch (IOException e) {
            LogManager.getLogger(PropertiesReader.class).error("Failed to load properties file {}", filePath);
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
            throw new IllegalArgumentException("Unable to read file : " + filePath, e);
        }
    }
}
