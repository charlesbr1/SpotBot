package org.sbot.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.Compression;
import net.dv8tion.jda.api.utils.SplitUtil;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.commands.reader.CommandContext;

import java.awt.*;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.sbot.SpotBot.DISCORD_BOT_CHANNEL;
import static org.sbot.commands.CommandAdapter.embedBuilder;
import static org.sbot.utils.PropertiesReader.readFile;

public final class Discord {

    public static final int MESSAGE_PAGE_SIZE = 1000; // this limit the number of messages that be sent in bulk

    @FunctionalInterface
    public interface BotChannel {
        void sendMessage(@NotNull String message);
    }

    private static final Logger LOGGER = LogManager.getLogger(Discord.class);


    private static final String DISCORD_BOT_TOKEN_FILE = "discord.token";

    public static final String PLAIN_TEXT_MARKDOWN = "``` ";
    public static final String SINGLE_LINE_BLOCK_QUOTE_MARKDOWN = "> ";
    public static final String MULTI_LINE_BLOCK_QUOTE_MARKDOWN = ">>> ";

    private final JDA jda;
    private final Map<String, CommandListener> commands = new ConcurrentHashMap<>();

    public Discord() {
        jda = loadDiscordConnection();
    }

    public BotChannel spotBotChannel(long discordServerId) {
        var channel = getSpotBotChannel(getDiscordServer(discordServerId));
        return message -> sendMessage(channel, message);
    }

    public BotChannel userChannel(long userId) {
        LOGGER.debug("Retrieving user private channel {}...", userId);
        var channel = getPrivateChannel(userId);
        return message -> sendMessage(channel, message);
    }

    private static void sendMessage(@NotNull MessageChannel channel, @NotNull String message) {
        asyncOrdered(split(message) // split message if bigger than 2000 chars (discord limitation)
                .peek(line -> LOGGER.debug("Discord message sent: {}", line))
                .map(MessageCreateData::fromContent)
                .map(channel::sendMessage));
    }

// this ensures the rest action are done in order
    public static void asyncOrdered(Stream<RestAction<?>> restActions) {
        restActions.limit(MESSAGE_PAGE_SIZE)
                .reduce(CompletableFuture.<Void>completedFuture(null),
                        (future, nextMessage) -> future.thenRun(nextMessage::submit),
                        CompletableFuture::allOf)
                .whenComplete((v, error) -> {
                    if (null != error) {
                        LOGGER.error("Exception occurred while sending message", error);
                    }
                });
    }

    private static Stream<String> split(@NotNull String message) {
        return SplitUtil.split(
                message,
                Message.MAX_CONTENT_LENGTH,
                false,
                SplitUtil.Strategy.NEWLINE,
                SplitUtil.Strategy.WHITESPACE,
                SplitUtil.Strategy.ANYWHERE
        ).stream();
    }

    @NotNull
    private JDA loadDiscordConnection() {
        try {
            LOGGER.info("Loading discord connection...");
            return JDABuilder.createLight(readFile(DISCORD_BOT_TOKEN_FILE),
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT,
                            GatewayIntent.DIRECT_MESSAGES)
                    .setActivity(Activity.watching("prices"))
                    .addEventListeners(new EventAdapter())
                    .setCompression(Compression.ZLIB)
                    .setAutoReconnect(true)
                    .build()
                    .awaitReady();
        } catch (Exception e) {
            LOGGER.error("Unable to establish discord connection");
            throw new IllegalStateException(e);
        }
    }

    @NotNull
    private Guild getDiscordServer(long discordServerId) {
        LOGGER.debug("Retrieving discord server {}...", discordServerId);
        return Optional.ofNullable(jda.getGuildById(discordServerId))
                .orElseThrow(() -> new IllegalStateException("Failed to load discord server " + discordServerId));
    }

    @NotNull
    private TextChannel getSpotBotChannel(@NotNull Guild discordServer) {
        return discordServer.getTextChannelsByName(DISCORD_BOT_CHANNEL, true)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Channel " + DISCORD_BOT_CHANNEL + " not found"));
    }

    @NotNull
    private MessageChannel getPrivateChannel(long userId) {
        LOGGER.debug("Retrieving user private channel {}...", userId);
        return Optional.ofNullable(jda.getPrivateChannelById(userId))
                .orElseThrow(() -> new IllegalStateException("User " + userId + " not found"));
    }

    private static final Map<Long, String> userNameCache = new ConcurrentHashMap<>(); //TODO use cache eviction
    public static Optional<String> getEffectiveName(@NotNull JDA jda, long userId) {
        return Optional.ofNullable(userNameCache.computeIfAbsent(userId,
                id -> Optional.ofNullable(jda.retrieveUserById(userId).complete()).map(User::getEffectiveName).orElse(null)));
    }

    public void registerCommands(CommandListener... commandListeners) {
        synchronized (commands) {
            commands.clear();
            var commandDescriptions = Optional.ofNullable(commandListeners).stream().flatMap(Arrays::stream)
                    .filter(Objects::nonNull)
                    .filter(this::registerCommand)
                    .map(Discord::getOptions).toList();
            // this call replace previous content, that's why commands was clear
            jda.updateCommands().addCommands(commandDescriptions).queue();
        }
    }

    private boolean registerCommand(@NotNull CommandListener commandListener) {
        LOGGER.info("Registering discord command: {}", commandListener.name());
        if(null != commands.put(commandListener.name(), commandListener)) {
            LOGGER.warn("Discord command {} was already registered", commandListener.name());
            return false;
        }
        return true;
    }

    @NotNull
    private static CommandData getOptions(@NotNull CommandListener discordCommand) {
        return Commands.slash(discordCommand.name(), discordCommand.description())
                .addOptions(discordCommand.options());
    }

    private final class EventAdapter extends ListenerAdapter {

        @Override
        public void onGuildJoin(@NotNull GuildJoinEvent event) {
            LOGGER.error("Guild server joined : " + event.getGuild());
        }

        @Override
        public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
            LOGGER.error("Guild member leaved : " + event.getGuild() + " user : " + event.getUser());
        }

        @Override
        public void onGuildLeave(@NotNull GuildLeaveEvent event) {
            LOGGER.error("Guild server leaved : " + event.getGuild());
        }

        @Override
        public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
            if (handleCommand(event.getUser(), event.getChannel())) {
                LOGGER.debug("Discord slash command received : {}, with options {}", event.getName(), event.getOptions());
                onCommand(new CommandContext(event));
            } else {
                event.replyEmbeds(embedBuilder(event.getName(), Color.black,
                                "SpotBot disabled on this channel. Use it in private or on #" + DISCORD_BOT_CHANNEL).build())
                        .queue(message -> message.deleteOriginal().queueAfter(3, TimeUnit.SECONDS));
            }
        }

        @Override
        public void onMessageReceived(@NotNull MessageReceivedEvent event) {
            if (handleCommand(event.getAuthor(), event.getChannel())) {
                LOGGER.debug("Discord message received : {}", event.getMessage().getContentRaw());
                onCommand(new CommandContext(event));
            }
        }

        private void onCommand(@NotNull CommandContext command) {
            try {
                CommandListener listener = commands.get(command.name);
                if(null != listener) {
                    listener.onCommand(command);
                }
            } catch (RuntimeException e) {
                LOGGER.warn("Error while processing discord command: " + command, e);
                command.reply(embedBuilder("Error", Color.red, command.user.getAsMention() + " Execution failed with error: " + e.getMessage()));
            }
        }

        private boolean handleCommand(@NotNull User user, @NotNull MessageChannel channel) {
            return !user.isBot() && (isPrivateMessage(channel.getType()) || isSpotBotChannel(channel));
        }

        private boolean isPrivateMessage(@Nullable ChannelType channelType) {
            return ChannelType.PRIVATE.equals(channelType);
        }

        private boolean isSpotBotChannel(@NotNull MessageChannel channel) {
            return DISCORD_BOT_CHANNEL.equals(channel.getName());
        }
    }
}
