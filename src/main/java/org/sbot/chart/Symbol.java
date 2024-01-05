package org.sbot.chart;

import org.jetbrains.annotations.NotNull;

public interface Symbol {

    @NotNull
    static String getSymbol(@NotNull String ticker) {
        return ticker.contains("USD") ? "$" :
                (ticker.contains("EUR") ? "€" :
                (ticker.contains("YEN") ? "¥" :
                (ticker.contains("GBP") ? "£" :
                (ticker.contains("BTC") ? "₿" : ticker))));
    }
}
