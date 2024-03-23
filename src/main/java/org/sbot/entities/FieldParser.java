package org.sbot.entities;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.entities.alerts.Alert;
import org.sbot.entities.alerts.ClientType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZonedDateTime;

import static java.util.Objects.requireNonNull;
import static org.sbot.utils.Dates.UTC;

public interface FieldParser {

    enum Type implements FieldParser {
        ALERT_TYPE {
            @NotNull
            @Override
            public Object parse(@NotNull String value) {
                return Alert.Type.valueOf(value);
            }
        },
        ALERT_CLIENT_TYPE {
            @NotNull
            @Override
            public Object parse(@NotNull String value) {
                return requireNonNull(ClientType.SHORTNAMES.get(value));
            }
        },
        STRING {
            @NotNull
            @Override
            public Object parse(@NotNull String value) {
                return requireNonNull(value);
            }
        },
        SHORT {
            @NotNull
            @Override
            public Object parse(@NotNull String value) {
                return Short.parseShort(value);
            }
        },
        LONG {
            @NotNull
            @Override
            public Object parse(@NotNull String value) {
                return Long.parseLong(value);
            }
        },
        DECIMAL {
            @NotNull
            @Override
            public Object parse(@NotNull String value) {
                return new BigDecimal(value);
            }
        },
        ZONED_DATE_TIME {
            @NotNull
            @Override
            public Object parse(@NotNull String value) {
                return Instant.ofEpochMilli(Long.parseLong(value)).atZone(UTC);
            }
        }
    }

    @NotNull
    Object parse(@NotNull String value);

    @NotNull
    static String format(@Nullable Object value) {
        return switch (value) {
            case null -> "";
            case ClientType ct -> ct.shortName;
            case BigDecimal bd -> bd.toPlainString();
            case ZonedDateTime zdt -> String.valueOf(zdt.toInstant().toEpochMilli());
            default -> value.toString();
        };
    }
}
