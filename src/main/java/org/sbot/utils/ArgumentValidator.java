package org.sbot.utils;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.entities.alerts.Alert.Type;
import org.sbot.entities.alerts.ClientType;
import org.sbot.exchanges.Exchange;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import static net.dv8tion.jda.api.entities.Message.MentionType.*;
import static org.sbot.entities.alerts.Alert.NO_DATE;
import static org.sbot.exchanges.Exchange.EXCHANGES;
import static org.sbot.exchanges.Exchange.VIRTUAL;
import static org.sbot.services.discord.Discord.guildName;
import static org.sbot.utils.MutableDecimal.ImmutableDecimal.ZERO;
import static org.sbot.utils.MutableDecimalParser.MAX_COMPACT_DIGITS;

public interface ArgumentValidator {

    Pattern BLANK_SPACES = Pattern.compile("\\s+");


    int MESSAGE_MAX_LENGTH = 210;
    int SETTINGS_MAX_LENGTH = 96;
    int TICKER_MIN_LENGTH = 1;
    int TICKER_MAX_LENGTH = 8;
    int PAIR_MIN_LENGTH = (2 * TICKER_MIN_LENGTH) + 1;
    int PAIR_MAX_LENGTH = (2 * TICKER_MAX_LENGTH) + 1;
    int PRICE_MAX_LENGTH = MAX_COMPACT_DIGITS;
    int REPEAT_MIN = 0;
    int REPEAT_MAX = 100;
    int SNOOZE_MIN = 1;
    int SNOOZE_MAX = 1000;


    Pattern PAIR_PATTERN = Pattern.compile("^[A-Z0-9]{" + TICKER_MIN_LENGTH + ',' + TICKER_MAX_LENGTH +
            "}/[A-Z0-9]{" + TICKER_MIN_LENGTH + ',' + TICKER_MAX_LENGTH + "}$"); // TICKER/TICKER format

    Pattern START_WITH_DISCORD_USER_ID_PATTERN = Pattern.compile("^<@!?(\\d+)>"); // matches id from discord user mention

    static int requirePositive(int value) {
        return (int) requirePositive((long) value);
    }

    static int requireStrictlyPositive(int value) {
        return (int) requireStrictlyPositive((long) value);
    }

    static long requireEpoch(long value) {
        if(value <= NO_DATE) {
            throw zeroOrNegativeValue(value);
        }
        return value;
    }

    static long requireStrictlyPositive(long value) {
        if(value <= 0) {
            throw zeroOrNegativeValue(value);
        }
        return value;
    }

    static long requirePositive(long value) {
        if(value < 0) {
            throw negativeValue(value);
        }
        return value;
    }

    @NotNull
    static MutableDecimal requirePositive(@NotNull MutableDecimal value) {
        if(value.compareTo(ZERO) < 0) {
            throw negativeValue(value);
        }
        return value;
    }

    @NotNull
    private static IllegalArgumentException negativeValue(@NotNull Object value) {
        return new IllegalArgumentException("Negative value : " + value);
    }

    @NotNull
    private static IllegalArgumentException zeroOrNegativeValue(@NotNull Object value) {
        return new IllegalArgumentException("Zero or negative value : " + value);
    }


    static short requireRepeat(long repeat) {
        if (repeat < REPEAT_MIN || repeat > REPEAT_MAX) {
            throw new IllegalArgumentException("Repeat must be between " + REPEAT_MIN + " and " + REPEAT_MAX + ", provided value : " + repeat);
        }
        return (short) repeat;
    }

    static short requireSnooze(long snooze) {
        if (snooze < SNOOZE_MIN || snooze > SNOOZE_MAX) {
            throw new IllegalArgumentException("Snooze must be between " + SNOOZE_MIN + " and " + SNOOZE_MAX + " , provided value : " + snooze);
        }
        return (short) snooze;
    }

    static MutableDecimal requirePrice(@NotNull MutableDecimal price) {
        return requirePositive(requireNonNull(price));
    }

    static String requireNotBlank(@NotNull String value, @NotNull String fieldName) {
        if (value.isBlank()) {
            throw new IllegalArgumentException("Missing value for " + fieldName);
        }
        return value;
    }

    static boolean requireBoolean(@NotNull String value, @NotNull String fieldName) {
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes") || value.equals("1")) {
            return true;
        }
        if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("no") || value.equals("0")) {
            return false;
        }
        throw new IllegalArgumentException("Missing value for " + fieldName);
    }

    @NotNull
    static Locale requireSupportedLocale(@NotNull String locale) {
        return Stream.of(DiscordLocale.values())
                .filter(l -> locale.equals(l.getLocale()))
                .filter(not(DiscordLocale.UNKNOWN::equals))
                .findFirst().map(DiscordLocale::toLocale)
                .orElseThrow(() -> new IllegalArgumentException("Provided locale is not supported : " + locale +" (expected one of : " +
                        String.join(", ", Stream.of(DiscordLocale.values())
                                .filter(not(DiscordLocale.UNKNOWN::equals))
                                .map(DiscordLocale::getLocale).toList()) + ')'));
    }

    @NotNull
    static Exchange requireRealExchange(@NotNull String exchange) {
        if(VIRTUAL.shortName.equalsIgnoreCase(exchange)) {
            throw unsupportedExchange(exchange);
        }
        return requireAnyExchange(exchange);
    }

    @NotNull
    static Exchange requireAnyExchange(@NotNull String exchange) {
        try {
            return Exchange.of(exchange);
        } catch (RuntimeException e) {
            throw unsupportedExchange(exchange);
        }
    }

    @NotNull
    private static IllegalArgumentException unsupportedExchange(@NotNull String exchange) {
        throw new IllegalArgumentException("Provided exchange is not supported : " + exchange + " (expected " + String.join(", ", EXCHANGES) + ')');
    }

    @NotNull
    static String requireExchangePairOrFormat(@NotNull Exchange exchange, @NotNull String pair) {
        requireNonNull(exchange);
        try {
            return requireExchangePair(exchange, pair);
        } catch (RuntimeException e) {
            return requirePairFormat(pair);
        }
    }

    @NotNull
    static String requireExchangePair(@NotNull Exchange exchange, @NotNull String pair) {
        var xchangePair = exchange.getPair(pair.toUpperCase());
        if(null == xchangePair) {
            throw unsupportedPair(pair, false);
        }
        return xchangePair;
    }

    @NotNull
    static String requirePairFormat(@NotNull String pair) {
        var upperCasePair = pair.toUpperCase();
        if(!PAIR_PATTERN.matcher(upperCasePair).matches()) {
            throw unsupportedPair(pair, true);
        }
        return upperCasePair;
    }

    @NotNull
    private static IllegalArgumentException unsupportedPair(@NotNull String pair, boolean sample) {
        return new IllegalArgumentException("Invalid pair : " + pair  + (sample ? ", should be like EUR/USD" : ""));
    }

    @NotNull
    static String requireTickerPairLength(@NotNull String tickerPair) {
        int slashIndex = tickerPair.indexOf('/');
        if(tickerPair.indexOf(' ') >= 0) {
            throw unsupportedTickerPair(tickerPair, PAIR_MIN_LENGTH, PAIR_MAX_LENGTH, slashIndex >= 0);
        }
        if(slashIndex >= 0) { // pair
            boolean badTicker = slashIndex > TICKER_MAX_LENGTH || slashIndex < TICKER_MIN_LENGTH || slashIndex == tickerPair.length() - 1;
            if (badTicker || tickerPair.length() > PAIR_MAX_LENGTH) {
                throw unsupportedTickerPair(tickerPair, PAIR_MIN_LENGTH, PAIR_MAX_LENGTH, true);
            }
        } else if (tickerPair.length() > TICKER_MAX_LENGTH || tickerPair.isEmpty()) {
            throw unsupportedTickerPair(tickerPair, TICKER_MIN_LENGTH, TICKER_MAX_LENGTH, false);
        }
        return tickerPair.toUpperCase();
    }


    @NotNull
    private static IllegalArgumentException unsupportedTickerPair(@NotNull String tickerPair, int minLength, int maxLength, boolean pair) {
        throw new IllegalArgumentException("Provided " + (pair ? "pair" : "ticker") + " is invalid : " + tickerPair + " (expected " + minLength + " to " + maxLength + " chars)");
    }

    static String requireAlertMessageMaxLength(@NotNull String message) {
        if (message.length() > MESSAGE_MAX_LENGTH) {
            throw new IllegalArgumentException("Provided message is too long (" + message.length() + " chars, max is " + MESSAGE_MAX_LENGTH + ") : " + message);
        }
        return message;
    }

    static String requireSettingsMaxLength(@NotNull String settings) {
        if (settings.length() > SETTINGS_MAX_LENGTH) {
            throw new IllegalArgumentException("Provided value is too long (" + settings.length() + " chars, max is " + SETTINGS_MAX_LENGTH + ") : " + settings);
        }
        return settings;
    }

    static long requireInFuture(long now, long epochTime) {
        if (epochTime < now) {
            throw new IllegalArgumentException("Provided date must be in the future");
        }
        return epochTime;
    }

    static long requireUser(@NotNull ClientType clientType, @NotNull String userMention) {
        return asUser(clientType, userMention).orElseThrow(() -> new IllegalArgumentException("Provided string is not an user mention : " + userMention));
    }

    // possibly blocking call
    static void requireGuildMember(@Nullable Guild guild, long userId) {
        if(null != guild && null == guild.retrieveMemberById(userId).complete()) {
            throw new IllegalArgumentException("User <@" + userId + "> is not a member of guild " + guildName(guild));
        }
    }

    static Optional<Long> asUser(@NotNull ClientType clientType, @NotNull String userMention) {
        return switch (clientType) {
            case DISCORD -> {
                Matcher matcher = USER.getPattern().matcher(userMention);
                yield matcher.matches() ? Optional.of(Long.parseLong(matcher.group(1))) : Optional.empty();
            }
        };
    }

    static Optional<Long> asChannel(@NotNull ClientType clientType, @NotNull String channelMention) {
        return switch (clientType) {
            case DISCORD -> {
                Matcher matcher = CHANNEL.getPattern().matcher(channelMention);
                yield matcher.matches() ? Optional.of(Long.parseLong(matcher.group(1))) : Optional.empty();
            }
        };
    }

    static Optional<Long> asRole(@NotNull ClientType clientType, @NotNull String roleMention) {
        return switch (clientType) {
            case DISCORD -> {
                Matcher matcher = ROLE.getPattern().matcher(roleMention);
                yield matcher.matches() ? Optional.of(Long.parseLong(matcher.group(1))) : Optional.empty();
            }
        };
    }

    static Optional<Type> asType(@NotNull String type) {
        try {
            return Optional.of(Type.valueOf(type));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    static <T> T requireOneItem(@NotNull List<T> list) {
        if(list.size() != 1) {
            throw new IllegalArgumentException("Unexpected list size (wanted 1) : " + list.size() + ", content : " + list);
        }
        return list.getFirst();
    }
}
