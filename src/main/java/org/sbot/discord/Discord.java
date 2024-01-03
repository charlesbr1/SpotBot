package org.sbot.discord;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
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
import net.dv8tion.jda.api.requests.FluentRestAction;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.Compression;
import net.dv8tion.jda.api.utils.messages.MessageCreateRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.commands.reader.CommandContext;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Map.Entry.comparingByKey;
import static java.util.stream.Collectors.*;
import static net.dv8tion.jda.api.entities.MessageEmbed.DESCRIPTION_MAX_LENGTH;
import static org.sbot.SpotBot.DISCORD_BOT_CHANNEL;
import static org.sbot.commands.CommandAdapter.embedBuilder;
import static org.sbot.utils.PropertiesReader.readFile;

public final class Discord {

    public static final int MESSAGE_PAGE_SIZE = 1001; // this limit the number of messages that can be sent in bulk, 1000 + 1 for the next command message
    private static final int MAX_MESSAGE_EMBEDS = 10;

    private static final int PRIVATE_CHANNEL_CACHE_TLL_MIN = 10;


    @FunctionalInterface
    public interface BotChannel {
        void sendMessages(@NotNull List<EmbedBuilder> messages, @NotNull List<Consumer<MessageCreateRequest<?>>> messageSetup);
    }

    private static final Logger LOGGER = LogManager.getLogger(Discord.class);


    private static final String DISCORD_BOT_TOKEN_FILE = "discord.token";

    public static final String PLAIN_TEXT_MARKDOWN = "``` ";
    public static final String SINGLE_LINE_BLOCK_QUOTE_MARKDOWN = "> ";
    public static final String MULTI_LINE_BLOCK_QUOTE_MARKDOWN = ">>> ";

    private final JDA jda;
    private final Map<String, CommandListener> commands = new ConcurrentHashMap<>();

    // we cache a supplier to be able to store null messageChannel when unavailable
    private final Cache<Long, Supplier<MessageChannel>> privateChannelCache = Caffeine.newBuilder()
            .expireAfterWrite(PRIVATE_CHANNEL_CACHE_TLL_MIN, TimeUnit.MINUTES)
            .maximumSize(1024)
            .build();

    public Discord() {
        jda = loadDiscordConnection();
    }

    public BotChannel spotBotChannel(long discordServerId) {
        var channel = getSpotBotChannel(getDiscordServer(discordServerId));
        return (messages, messageSetup) -> sendMessages(messages, channel::sendMessageEmbeds, messageSetup);
    }

    public Optional<BotChannel> userChannel(long userId) {
        var channel = getPrivateChannel(userId).orElse(null);
        return null != channel ? Optional.of((messages, messageSetup) -> sendMessages(messages, channel::sendMessageEmbeds, messageSetup)) : Optional.empty();
    }

    public static <T extends MessageCreateRequest<?> & FluentRestAction<?, ?>> void sendMessages(@NotNull List<EmbedBuilder> messages, @NotNull Function<List<MessageEmbed>, T> sendMessage, @NotNull List<Consumer<MessageCreateRequest<?>>> messageSetup) {
        // Discord limit to 10 embeds by message
        asyncOrdered(IntStream.range(0, messages.size()).boxed()
                .collect(groupingBy(index -> index / MAX_MESSAGE_EMBEDS, // split this stream into lists of 10 messages
                        mapping(messages::get, toList())))
                .entrySet().stream().sorted(comparingByKey())
                .map(entry -> toMessageRequest(entry.getValue(), sendMessage, messageSetup)));
    }

    @NotNull
    private static <T extends MessageCreateRequest<?> & FluentRestAction<?, ?>> RestAction<?> toMessageRequest(@NotNull List<EmbedBuilder> messages, @NotNull Function<List<MessageEmbed>, T> sendMessage, @NotNull List<Consumer<MessageCreateRequest<?>>> messageSetup) {
        List<MessageEmbed> embeds = messages.stream()
                .peek(message -> LOGGER.debug("Sending discord message : {}", message.getDescriptionBuilder()))
                .map(EmbedBuilder::build)
                .toList(); // list of 10 messages

        var messageRequest = sendMessage.apply(embeds);
        messageSetup.forEach(setup -> setup.accept(messageRequest));
        String mentionedUsers = messageRequest.getMentionedUsers().stream().map(str -> "<@" + str + '>').collect(joining(" "));
        messageRequest.setContent(mentionedUsers);
        return messageRequest;
    }

    // this ensures the rest action are done in order
    private static void asyncOrdered(@NotNull Stream<RestAction<?>> restActions) {
        restActions.limit(MESSAGE_PAGE_SIZE)
                .reduce(CompletableFuture.<Void>completedFuture(null),
                        (future, nextMessage) -> future.thenRun(nextMessage::submit),
                        CompletableFuture::allOf)
                .whenComplete((v, error) -> {
                    if (null != error) {
                        LOGGER.error("Exception occurred while sending discord message", error);
                    }
                });
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
    private Optional<MessageChannel> getPrivateChannel(long userId) {
        LOGGER.debug("Retrieving user private channel {}...", userId);
        return Optional.ofNullable(privateChannelCache.get(userId, this::getPrivateChannelSupplier).get());
    }

    private Supplier<MessageChannel> getPrivateChannelSupplier(long userId) {
        try {
            MessageChannel messageChannel = jda.retrieveUserById(userId).complete().openPrivateChannel().complete();
            return () -> messageChannel;
        } catch (RuntimeException e) {
            LOGGER.warn("Failed to retrieve discord private channel for user " + userId, e);
            return () -> null;
        }
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
            LOGGER.error("User " + event.getUser() + " leaved server : " + event.getGuild());
        }

        @Override
        public void onGuildLeave(@NotNull GuildLeaveEvent event) {
            LOGGER.error("Guild server leaved : " + event.getGuild());
        }

        @Override
        public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
            if (acceptCommand(event.getUser(), event.getChannel())) {
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
            if (acceptCommand(event.getAuthor(), event.getChannel())) {
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
                String error = Optional.ofNullable(e.getMessage()).stream()
                        .flatMap(str ->  Stream.of(str.split("\n", 1))).findFirst()
                        .map(str -> command.user.getAsMention() + " Something get wrong !\n* " + str)
                        .map(str -> str.substring(0, Math.min(str.length(), DESCRIPTION_MAX_LENGTH)))
                        .orElse("");
                command.reply(embedBuilder("Oups !", Color.red, error));
            }
        }

        private boolean acceptCommand(@NotNull User user, @NotNull MessageChannel channel) {
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
