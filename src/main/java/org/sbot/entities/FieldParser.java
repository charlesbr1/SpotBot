package org.sbot.entities;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.entities.alerts.Alert;
import org.sbot.entities.alerts.ClientType;
import org.sbot.utils.MutableDecimalParser;

import static java.util.Objects.requireNonNull;

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
        BYTE {
            @NotNull
            @Override
            public Object parse(@NotNull String value) {
                return Byte.parseByte(value);
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
                return MutableDecimalParser.parse(value);
            }
        },
    }

    @NotNull
    Object parse(@NotNull String value);

    @NotNull
    static String format(@Nullable Object value) {
        return switch (value) {
            case null -> "";
            case ClientType ct -> ct.shortName;
            default -> value.toString();
        };
    }
}
