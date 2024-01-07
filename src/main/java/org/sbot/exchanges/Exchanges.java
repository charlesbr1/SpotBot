package org.sbot.exchanges;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.sbot.exchanges.binance.BinanceClient;
import org.sbot.utils.PropertiesReader;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Optional.empty;
import static org.sbot.utils.PropertiesReader.loadProperties;
import static org.sbot.utils.PropertiesReader.readFile;

public enum Exchanges {
    ;

    private static final Logger LOGGER = LogManager.getLogger(Exchanges.class);

    private static final PropertiesReader properties = loadProperties("exchange.properties");

    private static final String BINANCE_API_KEY = properties.get("binance.api.key");
    private static final String BINANCE_API_SECRET_FILE = "binance.token";

    private static final Map<String, Exchange> exchanges = new ConcurrentHashMap<>();

    public static final List<String> SUPPORTED_EXCHANGES = List.of("binance");

    public static Optional<Exchange> get(@NotNull String exchange) {
        try {
            return Optional.of(exchanges.computeIfAbsent(exchange, Exchanges::loadExchange));
        } catch (RuntimeException e) {
            LOGGER.error("Unable to load exchange " + exchange, e);
            return empty();
        }
    }

    private static Exchange loadExchange(@NotNull String exchange) {
        LOGGER.debug("Loading exchange {}...", exchange);
        return switch (exchange) {
            case "binance" -> new BinanceClient(BINANCE_API_KEY, readFile(BINANCE_API_SECRET_FILE));
            default -> throw new IllegalArgumentException("Unsupported exchange : " + exchange);
        };
    }
}
