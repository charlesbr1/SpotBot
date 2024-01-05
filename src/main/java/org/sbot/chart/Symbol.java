package org.sbot.chart;

import org.jetbrains.annotations.NotNull;

public interface Symbol {

    @NotNull
    static String getSymbol(@NotNull String ticker) {
        return ticker.length() <= 4 && ticker.contains("USD") ? "$" :
                (ticker.equals("EUR") ? "€" :
                (ticker.equals("YEN") ? "¥" :
                (ticker.equals("GBP") ? "£" :
                (ticker.equals("BTC") ? "₿" : ticker))));
    }
}
