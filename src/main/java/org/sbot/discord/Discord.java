package org.sbot.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
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
import org.sbot.utils.ArgumentReader;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.sbot.SpotBot.DISCORD_BOT_CHANNEL;
import static org.sbot.utils.PropertiesReader.readFile;

public final class Discord {

    @FunctionalInterface
    public interface SpotBotChannel {
        void sendMessage(@NotNull String message);
    }

    private static final Logger LOGGER = LogManager.getLogger(Discord.class);


    private static final String DISCORD_BOT_TOKEN_FILE = "discord.token";

    public static final String PLAIN_TEXT_MARKDOWN = "``` ";
    public static final String SINGLE_LINE_BLOCK_QUOTE_MARKDOWN = "> ";
    public static final String MULTI_LINE_BLOCK_QUOTE_MARKDOWN = ">>> ";

    private final JDA jda;
    private final Map<String, DiscordCommand> commands = new ConcurrentHashMap<>();

    public Discord() {
        jda = loadDiscordConnection();
    }

    public SpotBotChannel spotBotChannel(long discordServerId) {
        var channel = getSpotBotChannel(getDiscordServer(discordServerId));
        return message -> sendMessage(channel::sendMessage, message);
    }

    private static void sendMessage(@NotNull Function<MessageCreateData, RestAction<?>> sender, @NotNull String message) {
        split(message) // split message if bigger than 2000 chars (discord limitation)
                .peek(line -> LOGGER.debug("Discord message sent: {}", line))
                .map(MessageCreateData::fromContent)
                .map(sender)
                .reduce((a, b) -> a.and((RestAction<?>) b)) // this ensures the messages are sent in order
                .ifPresent(RestAction::queue);
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
        LOGGER.info("Retrieving discord server {}...", discordServerId);
        return Optional.ofNullable(jda.getGuildById(discordServerId))
                .orElseThrow(() -> new IllegalStateException("Failed to load discord server " + discordServerId));
    }

    @NotNull
    private TextChannel getSpotBotChannel(@NotNull Guild discordServer) {
        return discordServer.getTextChannelsByName(DISCORD_BOT_CHANNEL, true)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("channel " + DISCORD_BOT_CHANNEL + " not found"));
    }


    public void registerCommands(DiscordCommand... discordCommands) {
        synchronized (commands) {
            commands.clear();
            var commandDescriptions = Optional.ofNullable(discordCommands).stream().flatMap(Arrays::stream)
                    .filter(Objects::nonNull)
                    .filter(this::registerCommand)
                    .map(Discord::getOptions).toList();
            // this call replace previous content, that's why commands was clear
            jda.updateCommands().addCommands(commandDescriptions).queue();
        }
    }

    private boolean registerCommand(@NotNull DiscordCommand discordCommand) {
        LOGGER.info("Registering discord command: {}", discordCommand.name());
        if(null != commands.put(discordCommand.name(), discordCommand)) {
            LOGGER.warn("Discord command {} was already registered", discordCommand.name());
            return false;
        }
        return true;
    }

    @NotNull
    private static CommandData getOptions(@NotNull DiscordCommand discordCommand) {
        return Commands.slash(discordCommand.name(), discordCommand.description())
                .addOptions(discordCommand.options());
    }

    private final class EventAdapter extends ListenerAdapter {

        @Override
        public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
            LOGGER.debug("Discord slash command received: {}", event);
            if (isPrivateMessage(event.getChannelType()) || isSpotBotChannel(event.getChannel())) {
                try {
                    Optional.ofNullable(commands.get(event.getName()))
                            .ifPresent(listener -> listener.onEvent(event));
                } catch (RuntimeException e) {
                    LOGGER.warn("Error while processing discord command: " + event.getName(), e);
                    sendMessage(event.getChannel()::sendMessage, event.getUser().getAsMention() + " Execution failed with error: " + e.getMessage());
                }
            } else {
                event.reply("Channel disabled").queue(message -> message.deleteOriginal().queue());
            }
        }

        @Override
        public void onMessageReceived(@NotNull MessageReceivedEvent event) {
            LOGGER.debug("Discord message received: {}", event.getMessage().getContentRaw());
            if (isPrivateMessage(event.getChannelType()) || isSpotBotChannel(event.getChannel())) {
                ArgumentReader argumentReader = new ArgumentReader(event.getMessage().getContentRaw().trim());
                try {
                    argumentReader.getNextString()
                            .filter(command -> command.startsWith("!"))
                            .map(command -> command.replaceFirst("!", ""))
                            .map(commands::get)
                            .ifPresent(listener -> listener.onEvent(argumentReader, event));
                } catch (RuntimeException e) {
                    LOGGER.warn("Error while processing discord command: " + event.getMessage().getContentRaw(), e);
                    sendMessage(event.getChannel()::sendMessage,
                            event.getAuthor().getAsMention() + " Execution failed with error: " + e.getMessage());
                }
            }
        }

        private boolean isPrivateMessage(@Nullable ChannelType channelType) {
            return ChannelType.PRIVATE.equals(channelType);
        }

        private boolean isSpotBotChannel(@NotNull MessageChannel channel) {
            return DISCORD_BOT_CHANNEL.equals(channel.getName());
        }

        public void onGuildJoin(@NotNull GuildJoinEvent event) {
            LOGGER.error("Guild server joined : " + event.getGuild());
        }

        public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
            LOGGER.error("Guild member leaved : " + event.getGuild() + " user : " + event.getUser());
        }

        public void onGuildLeave(@NotNull GuildLeaveEvent event) {
            LOGGER.error("Guild server leaved : " + event.getGuild());
        }
    }
}
