package org.sbot.entities.alerts;

import net.dv8tion.jda.api.EmbedBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.entities.chart.Candlestick;
import org.sbot.services.MatchingService.MatchingAlert;
import org.sbot.services.MatchingService.MatchingAlert.MatchingStatus;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Locale;

import static java.util.Objects.requireNonNull;
import static org.sbot.services.MatchingService.MatchingAlert.MatchingStatus.MATCHED;
import static org.sbot.services.MatchingService.MatchingAlert.MatchingStatus.NOT_MATCHING;
import static org.sbot.utils.ArgumentValidator.requirePositive;
import static org.sbot.utils.Dates.formatUTC;

public final class RemainderAlert extends Alert {

    public static final String REMAINDER_VIRTUAL_EXCHANGE = "@r";
    static final short REMAINDER_DEFAULT_REPEAT = 1;

    public RemainderAlert(long id, long userId, long serverId, @NotNull Locale locale,
                          @NotNull ZonedDateTime creationDate, @Nullable ZonedDateTime listeningDate,
                          @NotNull String pair, @NotNull String message,
                          @NotNull ZonedDateTime fromDate) {
        super(id, Type.remainder, userId, serverId, locale, creationDate, listeningDate, REMAINDER_VIRTUAL_EXCHANGE, pair, message, null, null,
                requireNonNull(fromDate, "missing RemainderAlert fromDate"),
                null, null, MARGIN_DISABLED, REMAINDER_DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS);
    }

    @Override
    @NotNull
    public RemainderAlert build(long id, long userId, long serverId, @NotNull Locale locale,
                                @NotNull ZonedDateTime creationDate, @Nullable ZonedDateTime listeningDate,
                                @NotNull String exchange, @NotNull String pair, @NotNull String message,
                                @Nullable BigDecimal fromPrice, @Nullable BigDecimal toPrice,
                                @NotNull ZonedDateTime fromDate, @Nullable ZonedDateTime toDate,
                                @Nullable ZonedDateTime lastTrigger, @NotNull BigDecimal margin, short repeat, short snooze) {
        if(!REMAINDER_VIRTUAL_EXCHANGE.equals(exchange) || MARGIN_DISABLED.compareTo(margin) != 0 ||
                null != toDate|| null != fromPrice|| null != toPrice || null != lastTrigger || REMAINDER_DEFAULT_REPEAT != repeat || DEFAULT_SNOOZE_HOURS != snooze) {
            throw new IllegalArgumentException("Can't update such value in a Remainder Alert");
        }
        return new RemainderAlert(id, userId, serverId, locale, creationDate, listeningDate, pair, message, fromDate);
    }

    public MatchingAlert match(@NotNull ZonedDateTime now, int checkPeriodMin) {
        if(fromDate.isBefore(now.plusMinutes((requirePositive(checkPeriodMin) / 2) + 1L))) { // alert accuracy is +- ALERTS_CHECK_PERIOD_MIN / 2
            return new MatchingAlert(this, MATCHED, null);
        }
        return new MatchingAlert(this, NOT_MATCHING, null);
    }

    @Override
    @NotNull
    protected EmbedBuilder asMessage(@NotNull MatchingStatus matchingStatus, @Nullable Candlestick previousCandlestick, @NotNull ZonedDateTime now) {
        String description = (matchingStatus.notMatching() ? type.titleName + " set by <@" + userId + "> on " + pair :
                "<@" + userId + ">\n\n**" + message + "**") +
                "\n\n* created :\t" + formatUTC(creationDate) +
                "\n* id :\t" + id +
                "\n* date :\t" + formatUTC(fromDate) +
                (matchingStatus.notMatching() ? "\n* message :\t" + message : "");
        return new EmbedBuilder().setDescription(description);
    }
}
