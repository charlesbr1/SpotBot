package org.sbot;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class SpotBotTest {

    @Test
    void main() {
    }

    @Test
    void spotBot() {
    }

    @Test
    void getParameters() {
    }

    <T> T getParameter(@NotNull List<String> args, @NotNull String name, @Nullable T defaultValue, @NotNull Function<String, T> mapper) {
        int index = args.indexOf('-' + name) + 1;
        if(index > 0 && index < args.size()) {
            String value = args.get(index);
            if(value.startsWith("-")) {
                throw new IllegalArgumentException(value + " is not a valid value for " + name);
            }
            defaultValue = mapper.apply(value);
        }
//        LOGGER.info("parameter : {} = {}", name, defaultValue);
        return defaultValue;
    }
    @Test
    void getParameter() {

    }

    @Test
    void help() {
        assertTrue(SpotBot.help().contains(SpotBot.DATABASE_URL_PROPERTY));
        assertTrue(SpotBot.help().contains(SpotBot.DISCORD_BOT_TOKEN_FILE_PROPERTY));
        assertTrue(SpotBot.help().contains(SpotBot.ALERTS_CHECK_PERIOD_MIN_PROPERTY));
        assertTrue(SpotBot.help().contains(SpotBot.ALERTS_HOURLY_SYNC_DELTA_MIN_PROPERTY));
    }

    @Test
    void loadDataServices() {
    }

    @Test
    void loadService() {
    }

    @Test
    void schedulingPlan() {
    }

    @Test
    void minutesUntilNextCheck() {
    }
}