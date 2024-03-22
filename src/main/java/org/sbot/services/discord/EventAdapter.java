package org.sbot.services.discord;

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
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.Message;
import org.sbot.entities.notifications.MigratedNotification.Reason;
import org.sbot.services.context.Context;

import java.awt.Color;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static org.sbot.commands.CommandAdapter.embedBuilder;
import static org.sbot.commands.MigrateCommand.migrateServerAlertsToPrivateChannel;
import static org.sbot.commands.MigrateCommand.migrateUserAlertsToPrivateChannel;
import static org.sbot.entities.alerts.Alert.DISABLED_COLOR;
import static org.sbot.entities.alerts.Alert.isPrivate;
import static org.sbot.services.discord.CommandListener.optionsDescription;
import static org.sbot.utils.ArgumentValidator.START_WITH_DISCORD_USER_ID_PATTERN;

final class EventAdapter extends ListenerAdapter {

    private static final Logger LOGGER = LogManager.getLogger(EventAdapter.class);

    private static final int ERROR_REPLY_DELETE_DELAY_SECONDS = 90;
    private static final int UNKNOWN_COMMAND_REPLY_DELAY_SECONDS = 5;

    private final Context context;

    public EventAdapter(@NotNull Context context) {
        this.context = requireNonNull(context);
    }

    @Override
    public void onGuildLeave(@NotNull GuildLeaveEvent event) {
        LOGGER.debug("onGuildLeave, event {}", event);
        // guild removed this bot, migrate each alert of this guild to private and notify each user
        var ids = context.transactional(txCtx -> migrateServerAlertsToPrivateChannel(txCtx, event.getGuild().getIdLong(), event.getGuild()));
        if(!ids.isEmpty()) {
            context.notificationService().sendNotifications();
        }
    }

    @Override
    public void onGuildBan(@NotNull GuildBanEvent event) {
        LOGGER.debug("onGuildBan, event {}", event);
        var nbMigrated = context.transactional(txCtx -> migrateUserAlertsToPrivateChannel(txCtx, event.getUser().getIdLong(), null, event.getGuild(), Reason.BANNED));
        if(nbMigrated > 0) {
            context.notificationService().sendNotifications();
        }
    }

    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        LOGGER.debug("onGuildMemberRemove, event {}", event);
        var nbMigrated = context.transactional(txCtx -> migrateUserAlertsToPrivateChannel(txCtx, event.getUser().getIdLong(), null, event.getGuild(), Reason.LEAVED));
        if(nbMigrated > 0) {
            context.notificationService().sendNotifications();
        }
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        LOGGER.debug("onGuildMemberJoin, event {}", event);
        // unblock possibly bocked notifications since this user leaved the server
        var nbUpdated = context.transactional(txCtx -> txCtx.notificationsDao().unblockStatusOfDiscordUser(event.getUser().getId()));
        if(nbUpdated > 0) {
            context.notificationService().sendNotifications();
        }
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        requireNonNull(event);
        try {
            LOGGER.info("Discord slash command received from user {} : {}, with options {}", event.getUser().getEffectiveName(), event.getName(), event.getOptions());
            if (!acceptCommand(event.getUser(), event.getChannel())) {
                throw new UnsupportedOperationException();
            }
            event.deferReply(true).queue();
            var user = context.transactional(txCtx -> txCtx.usersDao().setupUser(event.getUser().getIdLong(), event.getUserLocale().toLocale(), context.clock()));
            onCommand(CommandContext.of(context, user, event));
        } catch (UnsupportedOperationException e) {
            event.replyEmbeds(embedBuilder("Sorry !", DISABLED_COLOR,
                            "SpotBot disabled on this channel. Use it in private or on channel " +
                                    Optional.ofNullable(event.getGuild()).flatMap(Discord::spotBotChannel).map(Channel::getAsMention)
                                            .orElse(MarkdownUtil.bold("#" + Discord.DISCORD_BOT_CHANNEL))).build())
                    .setEphemeral(true)
                    .queue(message -> message.deleteOriginal().queueAfter(UNKNOWN_COMMAND_REPLY_DELAY_SECONDS, TimeUnit.SECONDS));
        } catch (RuntimeException e) {
            LOGGER.warn("Internal error while processing discord slash command : " + event, e);
            event.replyEmbeds(errorEmbed(event.getUser().getAsMention(), e.getMessage()))
                    .queue(m -> m.deleteOriginal().queueAfter(ERROR_REPLY_DELETE_DELAY_SECONDS, TimeUnit.SECONDS));
        }
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        requireNonNull(event);
        try {
            if (acceptCommand(event.getAuthor(), event.getChannel())) {
                var command = event.getMessage().getContentRaw().strip();
                if (isPrivateMessage(event.getChannel().getType()) || command.startsWith(context.discord().spotBotUserMention())) {
                    LOGGER.info("Discord message received from user {} : {}", event.getAuthor().getEffectiveName(), event.getMessage().getContentRaw());
                    command = removeStartingMentions(command);
                    if(!command.isBlank()) {
                        var user = context.transactional(txCtx -> txCtx.usersDao()
                                .accessUser(event.getAuthor().getIdLong(), context.clock())).orElse(null);
                        onCommand(CommandContext.of(context, user, event, command));
                    }
                }
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Internal error while processing discord message command : " + event, e);
            event.getMessage().replyEmbeds(errorEmbed(event.getAuthor().getAsMention(), e.getMessage()))
                    .queue(m -> m.delete().queueAfter(ERROR_REPLY_DELETE_DELAY_SECONDS, TimeUnit.SECONDS));
        }
    }

    static MessageEmbed errorEmbed(@NotNull String userMention, @Nullable String error) {
        return embedBuilder(":confused: Oops !", Color.red, requireNonNull(userMention) + " Something went wrong !\n\n" + error).build();
    }

    static boolean acceptCommand(@NotNull User user, @NotNull MessageChannel channel) {
        return !user.isBot() && (isPrivateMessage(channel.getType()) || isSpotBotChannel(channel));
    }

    static boolean isPrivateMessage(@Nullable ChannelType channelType) {
        return ChannelType.PRIVATE.equals(channelType);
    }

    static boolean isSpotBotChannel(@NotNull MessageChannel channel) {
        return Discord.DISCORD_BOT_CHANNEL.equals(channel.getName());
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
            String error = command.isStringReader() && !isPrivate(command.serverId()) ? command.user.getAsMention() + ' ' : "";
            var footer = "";
            if(e instanceof UnsupportedOperationException) {
                error += "I don't know this command : " + command.name;
            } else {
                if(e instanceof IllegalArgumentException) {
                    error += e.getMessage();
                    if (null != listener && command.isStringReader()) {
                        // print command arguments
                        error += commandArguments(listener);
                        footer = "this command " + listener.description();
                    }
                } else {
                    error += "Something went wrong !\n\n" + e.getMessage();
                    LOGGER.warn("Internal error while processing discord command : " + command.name, e);
                }
            }
            command.reply(Message.of(embedBuilder(":confused: Oops !", Color.red, error).setFooter(footer)), ERROR_REPLY_DELETE_DELAY_SECONDS);
        }
    }

    static String commandArguments(@NotNull CommandListener listener) {
        if (listener.options().getSubcommands().isEmpty()) {
            return  listener.options().getOptions().isEmpty() ? "" :
                    "\n\n" + MarkdownUtil.bold(listener.name()) + ' ' + optionsDescription(listener.options(), false);
        } else {
            return "\n\n" + MarkdownUtil.bold(listener.name()) + " *parameters :*" + optionsDescription(listener.options(), false);
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

    private void onInteraction(@NotNull IReplyCallback event, @NotNull User user, @NotNull Function<org.sbot.entities.User, CommandContext> commandContext) {
        requireNonNull(event);
        requireNonNull(commandContext);
        if(!user.isBot()) { // ignore bot actions, this will give them a not responding error
            Thread.ofVirtual().name("Discord interaction handler").start(() -> processInteraction(event, user, commandContext));
        }
    }

    void processInteraction(@NotNull IReplyCallback event, @NotNull User user, @NotNull Function<org.sbot.entities.User, CommandContext> commandContext) {
        try {
            var userSettings = context.transactional(txCtx -> txCtx.usersDao().getUser(event.getUser().getIdLong()))
                    .orElseThrow(() -> new IllegalStateException("User is not configured"));
            var command = commandContext.apply(userSettings);
            context.discord().getInteractionListener(command.name).onInteraction(command);
        } catch (RuntimeException e) {
            LOGGER.warn("Internal error while processing discord select interaction : " + event, e);
            event.replyEmbeds(errorEmbed(user.getAsMention(), e.getMessage()))
                    .queue(m -> m.deleteOriginal().queueAfter(ERROR_REPLY_DELETE_DELAY_SECONDS, TimeUnit.SECONDS));
        }
    }
}
