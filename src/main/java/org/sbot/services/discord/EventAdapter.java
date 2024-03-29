package org.sbot.services.discord;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.utils.MarkdownUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.commands.SetupCommand;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.Message;
import org.sbot.entities.settings.Settings;
import org.sbot.entities.notifications.MigratedNotification.Reason;
import org.sbot.services.context.Context;

import java.awt.Color;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static org.sbot.commands.CommandAdapter.embedBuilder;
import static org.sbot.commands.MigrateCommand.migrateServerAlertsToPrivateChannel;
import static org.sbot.commands.MigrateCommand.migrateUserAlertsToPrivateChannel;
import static org.sbot.entities.settings.ServerSettings.DEFAULT_BOT_CHANNEL;
import static org.sbot.entities.settings.UserSettings.NO_ID;
import static org.sbot.entities.alerts.Alert.DISABLED_COLOR;
import static org.sbot.entities.alerts.Alert.isPrivate;
import static org.sbot.entities.alerts.ClientType.DISCORD;
import static org.sbot.entities.notifications.RecipientType.DISCORD_USER;
import static org.sbot.services.discord.CommandListener.optionsDescription;
import static org.sbot.utils.ArgumentValidator.START_WITH_DISCORD_USER_ID_PATTERN;

final class EventAdapter extends ListenerAdapter {

    private static final Logger LOGGER = LogManager.getLogger(EventAdapter.class);

    static final int ERROR_REPLY_DELETE_DELAY_SECONDS = 90;
    private static final int UNKNOWN_COMMAND_REPLY_DELAY_SECONDS = 5;

    private final Context context;

    public EventAdapter(@NotNull Context context) {
        this.context = requireNonNull(context);
    }

    @Override
    public void onGuildLeave(@NotNull GuildLeaveEvent event) {
        LOGGER.debug("onGuildLeave, event {}", event);
        // guild removed this bot, migrate each alert of this guild to private and notify each user
        var ids = context.transactional(txCtx -> migrateServerAlertsToPrivateChannel(txCtx, DISCORD, event.getGuild().getIdLong(), event.getGuild()));
        if(!ids.isEmpty()) {
            context.notificationService().sendNotifications();
        }
    }

    @Override
    public void onGuildBan(@NotNull GuildBanEvent event) {
        LOGGER.debug("onGuildBan, event {}", event);
        if(!event.getUser().isBot()) {
            var nbMigrated = context.transactional(txCtx -> migrateUserAlertsToPrivateChannel(txCtx, DISCORD, event.getUser().getIdLong(), null, event.getGuild(), Reason.BANNED));
            if (nbMigrated > 0) {
                context.notificationService().sendNotifications();
            }
        }
    }

    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        LOGGER.debug("onGuildMemberRemove, event {}", event);
        if(!event.getUser().isBot()) {
            var nbMigrated = context.transactional(txCtx -> migrateUserAlertsToPrivateChannel(txCtx, DISCORD, event.getUser().getIdLong(), null, event.getGuild(), Reason.LEAVED));
            if (nbMigrated > 0) {
                context.notificationService().sendNotifications();
            }
        }
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        LOGGER.debug("onGuildMemberJoin, event {}", event);
        if(!event.getUser().isBot()) {
            // unblock possibly bocked notifications since this user leaved the server
            var nbUpdated = context.transactional(txCtx -> txCtx.notificationsDao().unblockStatusOfRecipient(DISCORD_USER, event.getUser().getId()));
            if (nbUpdated > 0) {
                context.notificationService().sendNotifications();
            }
        }
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if(event.getUser().isBot()) {
            return;
        }
        Settings settings = null;
        try {
            settings = settings(event.getUser(), event.getUserLocale().toLocale(), event.getMember());
            if (!acceptCommand(event.getChannel(), settings.serverSettings().spotBotChannel())) {
                throw new UnsupportedOperationException();
            }
            LOGGER.info("Discord slash command received from user {} : {}, with options {}", event.getUser().getEffectiveName(), event.getName(), event.getOptions());
            event.deferReply(true).queue();
            onCommand(CommandContext.of(context, settings, event));
        } catch (UnsupportedOperationException e) {
            var spotBotChannel = Optional.ofNullable(settings).map(s -> s.serverSettings().spotBotChannel()).orElse(DEFAULT_BOT_CHANNEL);
            event.replyEmbeds(embedBuilder("Sorry !", DISABLED_COLOR,
                            "SpotBot disabled on this channel. Use it in private or on channel " +
                                    Discord.spotBotChannel(event.getGuild(), spotBotChannel).map(Channel::getAsMention)
                                            .orElse(MarkdownUtil.bold("#" + spotBotChannel))).build())
                    .setEphemeral(true)
                    .queue(message -> message.deleteOriginal().queueAfter(UNKNOWN_COMMAND_REPLY_DELAY_SECONDS, TimeUnit.SECONDS));
        } catch (RuntimeException e) {
            LOGGER.warn(() -> "Internal error while processing discord slash command : " + event, e);
            event.replyEmbeds(errorEmbed(event.getUser().getAsMention(), e.getMessage()))
                    .queue(m -> m.deleteOriginal().queueAfter(ERROR_REPLY_DELETE_DELAY_SECONDS, TimeUnit.SECONDS));
        }
    }

    @NotNull
    private Settings settings(@NotNull User user, @NotNull Locale locale, @Nullable Member member) {
        return context.settingsService().setupSettings(DISCORD, user.getIdLong(),
                Optional.ofNullable(member).map(Member::getGuild).map(Guild::getIdLong).orElse(NO_ID),
                locale, context.clock());
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if(event.getAuthor().isBot()) {
            return;
        }
        String command = null;
        try {
            var content = event.getMessage().getContentRaw().strip();
            boolean accept = isPrivateMessage(event.getChannel().getType());
            if (accept || content.startsWith(context.discord().spotBotUserMention())) {
                command = removeStartingMentions(content);
                if(!command.isBlank()) {
                    var settings = context.settingsService().accessSettings(DISCORD, event.getAuthor().getIdLong(),
                                    Optional.ofNullable(event.getMember()).map(Member::getGuild).map(Guild::getIdLong).orElse(NO_ID),
                                    context.clock());
                    accept |= command.startsWith(SetupCommand.NAME); // accept setup command anywhere
                    if (accept || isSpotBotChannel(event.getChannel(), settings.serverSettings().spotBotChannel())) {
                        LOGGER.info("Discord message received from user {} : {}", event.getAuthor().getEffectiveName(), command);
                        onCommand(CommandContext.of(context, settings, event, command));
                    }
                }
            }
        } catch (RuntimeException e) {
            LOGGER.warn(() -> "Internal error while processing discord message command : " + event, e);
            if(null != command) {
                event.getMessage().replyEmbeds(errorEmbed(event.getAuthor().getAsMention(), e.getMessage()))
                        .queue(m -> m.delete().queueAfter(ERROR_REPLY_DELETE_DELAY_SECONDS, TimeUnit.SECONDS));
            }
        }
    }

    static MessageEmbed errorEmbed(@NotNull String userMention, @Nullable String error) {
        return embedBuilder(":confused: Oops !", Color.red, requireNonNull(userMention) + " Something went wrong !\n\n" + error).build();
    }

    static boolean acceptCommand(@NotNull MessageChannel channel, @NotNull String spotBotChannel) {
        requireNonNull(spotBotChannel);
        return isPrivateMessage(channel.getType()) || isSpotBotChannel(channel, spotBotChannel);
    }

    static boolean isPrivateMessage(@Nullable ChannelType channelType) {
        return ChannelType.PRIVATE.equals(channelType);
    }

    static boolean isSpotBotChannel(@NotNull MessageChannel channel, @NotNull String spotBotChannel) {
        return spotBotChannel.equals(channel.getName());
    }

    @NotNull
    static String removeStartingMentions(@NotNull String content) {
        String command;
        do {
            command = content;
            content = START_WITH_DISCORD_USER_ID_PATTERN.matcher(content)
                    .replaceFirst("").strip();
        } while (!content.equals(command));
        return command;
    }

    private void onCommand(@NotNull CommandContext command) {
        requireNonNull(command);
        Thread.ofVirtual().name("Discord command handler").start(() -> processCommand(command));
    }

    void processCommand(@NotNull CommandContext command) {
        CommandListener listener = null;
        try {
            listener = context.discord().getCommandListener(command.name);
            if (null != listener) {
                listener.onCommand(command);
            } else {
                throw new UnsupportedOperationException();
            }
        } catch (Exception e) {
            onError(command, e, listener);
        }
    }

    static void onError(@NotNull CommandContext command, @NotNull Exception exception, @Nullable CommandListener listener) {
        requireNonNull(exception);
        String error = command.isStringReader() && !isPrivate(command.serverId()) ? "<@" + command.userId + "> " : "";
        var footer = "";
        if(exception instanceof UnsupportedOperationException) {
            error += "I don't know this command : " + command.name;
        } else {
            if(exception instanceof IllegalArgumentException) {
                error += exception.getMessage();
                if (null != listener && command.isStringReader()) {
                    // for string commands, print command description and arguments
                    error += commandArguments(listener);
                    footer = "this command " + listener.description();
                }
            } else {
                error += "Something went wrong !\n\n" + exception.getMessage();
                LOGGER.warn(() -> "Internal error while processing discord command : " + command.name, exception);
            }
        }
        command.reply(Message.of(embedBuilder(":confused: Oops !", Color.red, error).setFooter(footer)), ERROR_REPLY_DELETE_DELAY_SECONDS);
    }

    static String commandArguments(@NotNull CommandListener listener) {
        if (listener.options().getSubcommands().isEmpty()) {
            return listener.options().getOptions().isEmpty() ? "" :
                    "\n\n" + MarkdownUtil.bold(listener.name()) + ' ' + optionsDescription(listener.options(), false);
        } else {
            return "\n\n" + MarkdownUtil.bold(listener.name()) + " *parameters :*\n\n" + optionsDescription(listener.options(), false);
        }
    }

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        onInteraction(event, event.getUser(), user -> CommandContext.of(context, user, event));
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        onInteraction(event, event.getUser(), user -> CommandContext.of(context, user, event));
    }

    private void onInteraction(@NotNull IReplyCallback event, @NotNull User user, @NotNull Function<Settings, CommandContext> commandContext) {
        requireNonNull(event);
        requireNonNull(commandContext);
        if(!user.isBot()) { // ignore bot actions, this will give them a not responding error
            Thread.ofVirtual().name("Discord interaction handler").start(() -> processInteraction(event, user, commandContext));
        }
    }

    void processInteraction(@NotNull IReplyCallback event, @NotNull User user, @NotNull Function<Settings, CommandContext> commandContext) {
        try {
            var settings = settings(event.getUser(), event.getUserLocale().toLocale(), event.getMember());
            var command = commandContext.apply(settings);
            context.discord().getInteractionListener(command.name).onInteraction(command);
        } catch (RuntimeException e) {
            LOGGER.warn(() -> "Internal error while processing discord select interaction : " + event, e);
            event.replyEmbeds(errorEmbed(user.getAsMention(), e.getMessage()))
                    .queue(m -> m.deleteOriginal().queueAfter(ERROR_REPLY_DELETE_DELAY_SECONDS, TimeUnit.SECONDS));
        }
    }
}
