package org.sbot.discord;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.commands.reader.CommandContext;
import org.sbot.services.dao.AlertsDao;

import java.awt.*;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static org.sbot.alerts.Alert.PRIVATE_ALERT;
import static org.sbot.commands.CommandAdapter.embedBuilder;
import static org.sbot.discord.Discord.guildName;
import static org.sbot.utils.ArgumentValidator.START_WITH_DISCORD_USER_ID_PATTERN;

final class EventAdapter extends ListenerAdapter {

    private static final Logger LOGGER = LogManager.getLogger(EventAdapter.class);

    private final Discord discord;
    private final AlertsDao alertsDao;
    private final String spotBotUserMention;

    public EventAdapter(@NotNull Discord discord, @NotNull AlertsDao alertsDao, @NotNull String selfMention) {
        this.discord = requireNonNull(discord);
        this.alertsDao = requireNonNull(alertsDao);
        spotBotUserMention = requireNonNull(selfMention);
    }

    @Override
    public void onGuildLeave(@NotNull GuildLeaveEvent event) {
        LOGGER.debug("onGuildLeave, event {}", event);
        migrateUserAlertsToPrivateChannel(null, event.getGuild(),
                "Guild " + guildName(event.getGuild()) + " removed this bot");
    }

    @Override
    public void onGuildBan(@NotNull GuildBanEvent event) {
        LOGGER.debug("onGuildBan, event {}", event);
        migrateUserAlertsToPrivateChannel(event.getUser().getIdLong(), event.getGuild(),
                "You were banned from guild " + guildName(event.getGuild()));
    }

    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        LOGGER.debug("onGuildMemberRemove, event {}", event);
        migrateUserAlertsToPrivateChannel(event.getUser().getIdLong(), event.getGuild(),
                "You leaved guild " + guildName(event.getGuild()));
    }

    private void migrateUserAlertsToPrivateChannel(@Nullable Long userId, @NotNull Guild guild, @NotNull String reason) {
        if(null != userId) {
            long nbMigrated = alertsDao.transactional(() -> alertsDao.updateServerIdOfUserAndServerId(userId, guild.getIdLong(), PRIVATE_ALERT));
            notifyPrivateAlertMigration(userId, guild, reason, nbMigrated);
            LOGGER.debug("Migrated to private {} alerts of user {} on server {}, reason : {}", nbMigrated, userId, guild.getIdLong(), reason);
        } else { // guild removed this bot, migrate all the alerts of this guild to private and notify each user
            List<Long> userIds = alertsDao.transactional(() -> {
                var ids = alertsDao.getUserIdsByServerId(guild.getIdLong());
                long totalMigrated = alertsDao.updateServerIdPrivate(guild.getIdLong());
                LOGGER.debug("Migrated to private {} alerts on server {}, reason : {}", totalMigrated, guild.getIdLong(), reason);
                return ids;
            });
            userIds.forEach(uid -> notifyPrivateAlertMigration(uid, guild, reason, null));
        }

    }

    private void notifyPrivateAlertMigration(long userId, @NotNull Guild guild, @NotNull String reason, Long nbMigrated) {
        if(null == nbMigrated || nbMigrated > 0) {
            discord.userChannel(userId).ifPresent(channel ->
                    channel.sendMessages(List.of(embedBuilder("Notice of " + (null == nbMigrated || nbMigrated > 1 ? "alerts" : "alert") + " migration", Color.lightGray,
                            reason + ((nbMigrated == null ? ", all your alerts on this guild were " :
                                    (nbMigrated > 1 ? ", all your alerts (" + nbMigrated + ") on this guild were " :
                                            ", your alert on this guild was "))) + "migrated to your private channel")), emptyList()));
        }
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (acceptCommand(event.getUser(), event.getChannel())) {
            LOGGER.info("Discord slash command received from user {} : {}, with options {}", event.getUser().getEffectiveName(), event.getName(), event.getOptions());
            onCommand(new CommandContext(discord, alertsDao, event));
        } else {
            event.replyEmbeds(embedBuilder("Sorry !", Color.black,
                            "SpotBot disabled on this channel. Use it in private or on channel " +
                                    Optional.ofNullable(event.getGuild()).flatMap(Discord::getSpotBotChannel)
                                            .map(Channel::getAsMention).orElse("**#" + Discord.DISCORD_BOT_CHANNEL + "**")).build())
                    .setEphemeral(true)
                    .queue(message -> message.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
        }
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (acceptCommand(event.getAuthor(), event.getChannel())) {
            var command = event.getMessage().getContentRaw().strip();
            if (isPrivateMessage(event.getChannel().getType()) || command.startsWith(spotBotUserMention)) {
                LOGGER.info("Discord message received from user {} : {}", event.getAuthor().getEffectiveName(), event.getMessage().getContentRaw());
                onCommand(new CommandContext(discord, alertsDao, event, removeStartingMentions(command)));
            }
        }
    }

    @NotNull
    private static String removeStartingMentions(@NotNull String content) {
        String command;
        do {
            command = content;
            content = START_WITH_DISCORD_USER_ID_PATTERN.matcher(content)
                    .replaceFirst("").strip();
        } while (!content.equals(command));
        return command;
    }

    private void onCommand(@NotNull CommandContext command) {
        Thread.ofVirtual().name("Discord event handler").start(() -> process(command));
    }

    private void process(@NotNull CommandContext command) {
        try {
            CommandListener listener = discord.getGetCommandListener(command.name);
            if (null != listener) {
                listener.onCommand(command);
            } else if (!command.name.isEmpty()) {
                command.reply(30, embedBuilder(":confused: Oops !", Color.red,
                        command.user.getAsMention() + " I don't know this command"));
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Internal error while processing discord command: " + command.name, e);
            command.reply(30, embedBuilder(":confused: Oops !", Color.red,
                    command.user.getAsMention() + " Something went wrong !\n\n" + e.getMessage()));
        }
    }

    private boolean acceptCommand(@NotNull User user, @NotNull MessageChannel channel) {
        return !user.isBot() && (isPrivateMessage(channel.getType()) || isSpotBotChannel(channel));
    }

    private boolean isPrivateMessage(@Nullable ChannelType channelType) {
        return ChannelType.PRIVATE.equals(channelType);
    }

    private boolean isSpotBotChannel(@NotNull MessageChannel channel) {
        return Discord.DISCORD_BOT_CHANNEL.equals(channel.getName());
    }
}
