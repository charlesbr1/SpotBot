package org.sbot.entities.alerts;

import net.dv8tion.jda.api.EmbedBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.entities.FieldParser;
import org.sbot.entities.chart.DatedPrice;
import org.sbot.services.MatchingService.MatchingAlert;
import org.sbot.services.MatchingService.MatchingAlert.MatchingStatus;
import org.sbot.utils.Dates;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static org.sbot.services.MatchingService.MatchingAlert.MatchingStatus.MATCHED;
import static org.sbot.services.MatchingService.MatchingAlert.MatchingStatus.NOT_MATCHING;
import static org.sbot.utils.ArgumentValidator.requirePositive;
import static org.sbot.utils.Dates.formatDiscord;
import static org.sbot.utils.Dates.formatDiscordRelative;

public final class RemainderAlert extends Alert {

    public static final String REMAINDER_VIRTUAL_EXCHANGE = "@r";
    public static final short REMAINDER_DEFAULT_REPEAT = 0;

    public RemainderAlert(long id, @NotNull ClientType clientType, long userId, long serverId,
                          @NotNull ZonedDateTime creationDate, @Nullable ZonedDateTime listeningDate,
                          @NotNull String pair, @NotNull String message,
                          @NotNull ZonedDateTime fromDate, @Nullable ZonedDateTime lastTrigger, short repeat, short snooze) {
        super(id, Type.remainder, clientType, userId, serverId, creationDate, listeningDate, REMAINDER_VIRTUAL_EXCHANGE, pair, message, null, null,
                requireNonNull(fromDate, "missing RemainderAlert fromDate"),
                null, lastTrigger, MARGIN_DISABLED, repeat, snooze);
    }

    public RemainderAlert(@NotNull Map<FieldParser, Object> fields) {
        super(fields);
    }

    @Override
    @NotNull
    protected RemainderAlert build(long id, @NotNull ClientType clientType, long userId, long serverId,
                                @NotNull ZonedDateTime creationDate, @Nullable ZonedDateTime listeningDate,
                                @NotNull String exchange, @NotNull String pair, @NotNull String message,
                                @Nullable BigDecimal fromPrice, @Nullable BigDecimal toPrice,
                                @NotNull ZonedDateTime fromDate, @Nullable ZonedDateTime toDate,
                                @Nullable ZonedDateTime lastTrigger, @NotNull BigDecimal margin, short repeat, short snooze) {
        if(!REMAINDER_VIRTUAL_EXCHANGE.equals(exchange) || MARGIN_DISABLED.compareTo(margin) != 0 ||
                null != toDate|| null != fromPrice|| null != toPrice) {
            throw new IllegalArgumentException("Can't update such value in a Remainder Alert");
        }
        return new RemainderAlert(id, clientType, userId, serverId, creationDate, listeningDate, pair, message, fromDate, lastTrigger, repeat, snooze);
    }

    public MatchingAlert match(@NotNull ZonedDateTime now, int checkPeriodMin) {
        if(fromDate.isBefore(now.plusMinutes((requirePositive(checkPeriodMin) / 2) + 1L))) { // alert accuracy is +- ALERTS_CHECK_PERIOD_MIN / 2
            return new MatchingAlert(this, MATCHED, null);
        }
        return new MatchingAlert(this, NOT_MATCHING, null);
    }

    @Override
    @NotNull
    public EmbedBuilder asMessage(@NotNull MatchingStatus matchingStatus, @Nullable DatedPrice previousClose, @NotNull ZonedDateTime now) {
        requireNonNull(now);
        int nextRepeat = matchingStatus.notMatching() ? repeat : repeat - 1;
        String description = (matchingStatus.notMatching() ? type.titleName + " set by <@" + userId + "> on " + pair :
                "<@" + userId + ">\n\n**" + message + "**") +
                (matchingStatus.notMatching() && (null == listeningDate || !fromDate.isEqual(listeningDate)) ? withListeningTime(now) : "") +
                "\n\n* id :\t" + id +
                "\n* date :\t" + formatDiscord(fromDate) + '(' + formatDiscordRelative(fromDate) + ')' +
                "\n* created :\t" + formatDiscordRelative(creationDate) +
                "\n* repeat / snooze :\t" + (nextRepeat < 0 ? "disabled" : nextRepeat) + " / " + snooze + (snooze > 1 ? " hours" : " hour") +
                (matchingStatus.notMatching() ? Optional.ofNullable(lastTrigger)
                .map(Dates::formatDiscordRelative)
                .map("\n\nRaised "::concat)
                .orElse("") : "");
        return new EmbedBuilder().setDescription(description);
    }
}
