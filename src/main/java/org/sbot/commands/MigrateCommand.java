package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.commands.reader.CommandContext;
import org.sbot.services.dao.AlertsDao;

import static net.dv8tion.jda.api.interactions.commands.OptionType.*;
import static org.sbot.commands.SecurityAccess.alertBelongToUser;
import static org.sbot.utils.ArgumentValidator.*;

public final class MigrateCommand extends CommandAdapter {

    public static final String NAME = "migrate";
    static final String DESCRIPTION = "migrate an alert to your private channel or on another server that you and this bot have access to";
    private static final int RESPONSE_TTL_SECONDS = 30;

    private static final OptionData SERVER_ID_OPTION = option(NUMBER, "server_id", "0 for private channel or id of a discord server, you must be member of this server and this bot too", true);

    static final SlashCommandData options =
            Commands.slash(NAME, DESCRIPTION).addSubcommands(
                    new SubcommandData("id", DESCRIPTION).addOptions(
                            option(INTEGER, "alert_id", "id of one alert to migrate", true)
                                    .setMinValue(0),
                            SERVER_ID_OPTION),
                    new SubcommandData("all_or_ticker_or_pair", DESCRIPTION).addOptions(
                            option(STRING, "ticker_pair_all", "a ticker or a pair to filter alerts to migrate, or 'all'", true)
                                    .setMinLength(ALERT_MIN_TICKER_LENGTH).setMaxLength(ALERT_MAX_PAIR_LENGTH),
                            SERVER_ID_OPTION,
                            option(USER, "user", "for admin only, owner of the alerts to migrate", false)));

    public MigrateCommand(@NotNull AlertsDao alertsDao) {
        super(alertsDao, NAME, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        long alertId = requirePositive(context.args.getMandatoryLong("alert_id"));
        Long serverId = context.args.getLong("server_id").orElse(null);
        String privateChannel = context.args.getString("channel").orElse(null);

        LOGGER.debug("migrate command - alert_id : {}, channel : {}, server_id : {}", alertId, privateChannel, serverId);
        validateArguments(serverId, privateChannel);
        alertsDao.transactional(() -> context.reply(responseTtlSeconds, migrate(context.noMoreArgs(), alertId, serverId, privateChannel)));
    }

    private void validateArguments(@Nullable Long serverId, @Nullable String privateChannel) {
        if(null == serverId && null == privateChannel) {
            throw new IllegalArgumentException("Missing argument, 'private' or a discord server id is expected");
        }
        if(null != serverId && null != privateChannel) {
            throw new IllegalArgumentException("Too many arguments provided, either 'private' or discord server id is expected (exclusive arguments)");
        }
    }
    //TODO
// un admin peut rendre un alert d'un user private plutot que la delete@
    private EmbedBuilder migrate(@NotNull CommandContext context, long alertId, @Nullable Long serverId, @Nullable String privateChannel) {
        var alert = alertsDao.getUserIdAndServerIdAndType(alertId).orElse(null);
        if("CHOICE_PRIVATE".equals(privateChannel)) {
            if(alertBelongToUser(context.user, alert.userId()) && alert.serverId() == context.member.getGuild().getIdLong()) {
                // update alert set serverId 0
            }

        } else {
            if(alertBelongToUser(context.user, alert.userId())) {
                if(null != context.channel.getJDA().getGuildById(serverId))
                    context.channel.getJDA().getGuildById(serverId).getMemberById(context.user.getIdLong());
            }
        }
        return null;
    }
}
