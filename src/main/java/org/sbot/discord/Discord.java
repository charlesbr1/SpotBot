package org.sbot.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.utils.ArgumentReader;
import org.sbot.utils.PropertiesReader;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static net.dv8tion.jda.api.entities.MessageEmbed.TEXT_MAX_LENGTH;
import static org.sbot.utils.PropertiesReader.loadProperties;
import static org.sbot.utils.PropertiesReader.readFile;

public final class Discord {

    @FunctionalInterface
    public interface SpotBotChannel {
        //TODO remplacer par class DiscordServeur qui hold le channel name
        void sendMessage(@NotNull String message);
    }

    @FunctionalInterface
    public interface MessageSender {
        RestAction<?> sendMessage(@NotNull String message);
    }


    private static final Logger LOGGER = LogManager.getLogger(Discord.class);

    public static final PropertiesReader discordProperties = loadProperties("discord.properties");
    public static final String DISCORD_SERVER_ID_PROPERTY = "discord.server.id";
    public static final String DISCORD_BOT_CHANNEL_PROPERTY = "discord.bot.channel";

    private static final String DISCORD_BOT_TOKEN_FILE = "discord.token";

    private static final Object DISCORD_SEND_MESSAGE_LOCK = new Object();

    public static final String PLAIN_TEXT_MARKDOWN = "``` ";
    public static final String SINGLE_LINE_BLOCK_QUOTE_MARKDOWN = "> ";
    public static final String MULTI_LINE_BLOCK_QUOTE_MARKDOWN = ">>> ";

    private final JDA jda;
    private final Map<String, DiscordCommand> commands = new ConcurrentHashMap<>();

    private final Map<String, String> serverIDSpotBotChannel = new ConcurrentHashMap<>();

    public Discord() {
        jda = loadDiscordConnection();
    }

    public SpotBotChannel spotBotChannel(@NotNull String discordServerId, @NotNull String spotBotChannel) {
//        return serverIDSpotBotChannel.computeIfAbsent(discordServerId, id -> {
//            var channel = loadDiscordChannel(loadDiscordServer(id), spotBotChannel);
//            return new SpotBotChannelMessageSender(spotBotChannel, message -> sendMessage(channel::sendMessage, message));
//        }).spotBotChannel();
        return s -> s.isEmpty();
    }

    public static void sendMessage(@NotNull MessageSender sender, @NotNull String message) {
        synchronized (DISCORD_SEND_MESSAGE_LOCK) {
            split(message)
                    .peek(line -> LOGGER.debug("Discord message sent: {}", line))
                    .forEach(line -> sender.sendMessage(line).queue());
        }
    }

    // discord limit message size to 2000 chars.
    // this split a string on MESSAGE_SECTION_DELIMITER, then every 2000 chars if the line is bigger
    private static Stream<String> split(String message) {
/*

        List<String> contents = SplitUtil.split(
                someLargeString,  // input string of arbitrary length
                2000,             // the split limit, can be arbitrary (>0)
                true,             // whether to trim the strings (empty will be discarded)
                Strategy.NEWLINE, // split on '\n' characters if possible
                Strategy.ANYWHERE // otherwise split on the limit
        );
// Convert to instance of MessageCreateData (optional, you can just send strings directly!)
        List<MessageCreateData> messages = contents.stream().map(MessageCreateData::fromContent).toList();

 */
        return Arrays.stream(message.split("MESSAGE_SECTION_DELIMITER"))
                .flatMap(line -> IntStream
                        .range(0, (line.length() + TEXT_MAX_LENGTH - 1) / TEXT_MAX_LENGTH)
                        .mapToObj(i -> line.substring(i * TEXT_MAX_LENGTH,
                                Math.min((i + 1) * TEXT_MAX_LENGTH, line.length()))));


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
    private Guild loadDiscordServer(@NotNull String discordServerId) {
        LOGGER.info("Connection to discord server {}...", discordServerId);
        return Optional.ofNullable(jda.getGuildById(discordServerId))
                .orElseThrow(() -> new IllegalStateException("Failed to load discord server " + discordServerId));
    }

    @NotNull
    private TextChannel loadDiscordChannel(@NotNull Guild discordServer, @NotNull String discordBotChannel) {
        return discordServer.getTextChannelsByName(discordBotChannel, true)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("channel " + discordBotChannel + " not found"));
    }


    public void registerCommands(DiscordCommand... discordCommands) {
        synchronized (commands) {
            commands.clear();
            var commandDescriptions = Optional.ofNullable(discordCommands).stream().flatMap(Arrays::stream)
                    .filter(Objects::nonNull)
                    .filter(this::registerCommand)
                    .map(this::getOptions).toList();
            // this call replace previous content, that's why commands was clear
            jda.updateCommands().addCommands(commandDescriptions).queue();
        }
    }

    public boolean registerCommand(@NotNull DiscordCommand discordCommand) {
        LOGGER.info("Registering discord command: {}", discordCommand.name());
        if(null != commands.put(discordCommand.name(), discordCommand)) {
            LOGGER.warn("Discord command {} was already registered", discordCommand.name());
            return false;
        }
        return true;
    }

    @NotNull
    public CommandData getOptions(@NotNull DiscordCommand discordCommand) {
        return Commands.slash(discordCommand.name(), discordCommand.description())
                .addOptions(discordCommand.options());
    }

    private final class EventAdapter extends ListenerAdapter {

        @Override
        public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
            LOGGER.debug("Discord slash command received: {}", event);
            if (isPrivateMessage(event.getChannelType()) || checkAccess(event.getGuild(), event.getChannel().getName())) {
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
            if (isPrivateMessage(event.getChannelType()) || checkAccess(event.getGuild(), event.getChannel().getName())) {
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

        private boolean isPrivateMessage(ChannelType channelType) {
            return ChannelType.PRIVATE.equals(channelType);
        }

        private boolean checkAccess(@Nullable Guild guild, @NotNull String channelName) {
            return true;
            //Optional.ofNullable(guild)
            //        .map(Guild::getId)
              //      .map(serverIDSpotBotChannel::get)
//                    .map(SpotBotChannelMessageSender::channelName)
//                    .filter(channelName::equalsIgnoreCase)
                //    .isPresent();
        }

        public void onGuildJoin(@NotNull GuildJoinEvent event) {
            LOGGER.error("QUILD JOIN " + event);
        }

        public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
            LOGGER.error("USER LEAVE " + event);

        }

        public void onGuildLeave(@NotNull GuildLeaveEvent event) {
            LOGGER.error("QUILD LEAVE " + event);
        }
    }
}
