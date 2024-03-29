package org.sbot.exchanges;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.sbot.entities.alerts.RemainderAlert;
import org.sbot.entities.chart.Candlestick;
import org.sbot.entities.chart.TimeFrame;
import org.sbot.exchanges.binance.BinanceClient;
import org.sbot.utils.PropertiesReader;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Collections.emptyList;
import static java.util.Optional.empty;
import static org.sbot.utils.PropertiesReader.loadProperties;
import static org.sbot.utils.PropertiesReader.readFile;

public final class Exchanges {

    private static final Logger LOGGER = LogManager.getLogger(Exchanges.class);

    private static final PropertiesReader properties = loadProperties("exchange.properties");

    private static final String BINANCE_API_KEY = properties.get("binance.api.key");
    private static final String BINANCE_API_SECRET_FILE = "binance.token";


    public static final List<String> SUPPORTED_EXCHANGES = List.of(BinanceClient.NAME);
    public static final List<String> VIRTUAL_EXCHANGES = List.of(RemainderAlert.REMAINDER_VIRTUAL_EXCHANGE);

    private final Map<String, Exchange> exchanges = new ConcurrentHashMap<>();

    public Exchanges() {
        VIRTUAL_EXCHANGES.forEach(exchangeName -> exchanges.put(exchangeName, new Exchange() {
            @Override @NotNull public String name() { return exchangeName; }
            @Override @NotNull public List<Candlestick> getCandlesticks(@NotNull String pair, @NotNull TimeFrame timeFrame, long limit) { return emptyList(); }
        }));
    }

    public Optional<Exchange> get(@NotNull String exchange) {
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
            case BinanceClient.NAME -> new BinanceClient(BINANCE_API_KEY, readFile(BINANCE_API_SECRET_FILE));
            default -> throw new IllegalArgumentException("Unsupported exchange : " + exchange);
        };
    }
}
