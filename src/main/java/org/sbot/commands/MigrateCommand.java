package org.sbot.commands;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.commands.reader.CommandContext;
import org.sbot.services.dao.AlertsDao;

import java.util.List;

import static net.dv8tion.jda.api.interactions.commands.OptionType.*;
import static org.sbot.commands.SecurityAccess.alertBelongToUser;
import static org.sbot.utils.ArgumentValidator.requirePositive;

public final class MigrateCommand extends CommandAdapter {

    public static final String NAME = "migrate";
    static final String DESCRIPTION = "migrate an alert to your private channel or on another server that you and this bot have access to";
    private static final int RESPONSE_TTL_SECONDS = 30;

    private static final String CHOICE_PRIVATE = "private";

    static final List<OptionData> options = List.of(
            new OptionData(INTEGER, "alert_id", "id of the alert", true)
                    .setMinValue(0),
            new OptionData(USER, "user", "owner of alerts to migrate", true),
            new OptionData(STRING, "channel", "'private' to migrate the alert on your private channel (exclusive argument)", false)
                    .addChoice(CHOICE_PRIVATE, CHOICE_PRIVATE),
            new OptionData(NUMBER, "server_id", "id of a discord server to migrate your alert, you must be member of this server and this bot too", false),
            new OptionData(STRING, "ticker_pair", "id of a discord server to migrate your alert, you must be member of this server and this bot too", false));


    public MigrateCommand(@NotNull AlertsDao alertsDao) {
        super(alertsDao, NAME, DESCRIPTION, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        long alertId = requirePositive(context.args.getMandatoryLong("alert_id"));
        Long serverId = context.args.getLong("server_id").orElse(null);
        String privateChannel = context.args.getString("channel").orElse(null);

        LOGGER.debug("migrate command - alert_id : {}, channel : {}, server_id : {}", alertId, privateChannel, serverId);
        validateArguments(serverId, privateChannel);
        alertsDao.transactional(() -> context.reply(responseTtlSeconds, migrate(context, alertId, serverId, privateChannel)));
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
        if(CHOICE_PRIVATE.equals(privateChannel)) {
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
