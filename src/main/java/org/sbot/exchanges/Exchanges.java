package org.sbot.exchanges;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sbot.exchanges.binance.BinanceClient;
import org.sbot.utils.PropertiesReader;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.sbot.utils.PropertiesReader.loadProperties;

public enum Exchanges {
    ;

    private static final Logger LOGGER = LogManager.getLogger(Exchanges.class);

    private static final PropertiesReader properties = loadProperties("exchange.properties");

    private static final String BINANCE_API_KEY = properties.get("binance.api.key");
    private static final String BINANCE_API_SECRET = properties.get("binance.api.secret");


    private static final Map<String, Exchange> exchanges = new ConcurrentHashMap<>();

    public static Exchange get(String exchange) {
        return exchanges.computeIfAbsent(exchange, Exchanges::loadExchange);
    }

    private static Exchange loadExchange(String exchange) {
        LOGGER.debug("Loading exchange {}...", exchange);
        return switch (exchange) {
            case "binance" -> new BinanceClient(BINANCE_API_KEY, BINANCE_API_SECRET);
            default -> throw new IllegalArgumentException("Unsupported exchange : " + exchange);
        };
    }
}
