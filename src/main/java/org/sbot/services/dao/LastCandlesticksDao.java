package org.sbot.services.dao;

import org.jetbrains.annotations.NotNull;
import org.sbot.chart.Candlestick;

import java.util.Optional;

public interface LastCandlesticksDao extends TransactionalCtx {

    Optional<Candlestick> getLastCandlestick(@NotNull String pair);

    void setLastCandlestick(@NotNull String pair, @NotNull Candlestick candlestick);
}
