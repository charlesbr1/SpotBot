package org.sbot.discord;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.Channel;
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
import org.sbot.SpotBot;
import org.sbot.commands.reader.CommandContext;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Comparator.comparing;
import static java.util.Optional.empty;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static org.sbot.commands.CommandAdapter.embedBuilder;
import static org.sbot.utils.PartitionSpliterator.split;
import static org.sbot.utils.PropertiesReader.readFile;

public final class Discord {

    public static final int MESSAGE_PAGE_SIZE = 1001; // this limit the number of messages that can be sent in bulk, 1000 + 1 for the next command message
    private static final int MAX_MESSAGE_EMBEDS = 10;

    public static final String DISCORD_BOT_CHANNEL = SpotBot.appProperties.get("discord.bot.channel");
    public static final String DISCORD_BOT_ROLE = SpotBot.appProperties.get("discord.bot.role");

    private static final int PRIVATE_CHANNEL_CACHE_TLL_MIN = 5;


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

    // this allows to keep the minimum jda cache settings, user private channel may be requested by many threads in bulk
    private final Cache<Long, Optional<MessageChannel>> privateChannelCache = Caffeine.newBuilder()
            .expireAfterWrite(PRIVATE_CHANNEL_CACHE_TLL_MIN, TimeUnit.MINUTES)
            .maximumSize(1024)
            .build();

    public Discord() {
        jda = loadDiscordConnection();
    }

    public Optional<BotChannel> spotBotChannel(@NotNull Guild guild) {
        return getSpotBotChannel(guild).map(channel ->
                (messages, messageSetup) -> sendMessages(messages, channel::sendMessageEmbeds, messageSetup));
    }

    public Optional<BotChannel> userChannel(long userId) {
        return getPrivateChannel(userId).map(channel ->
                (messages, messageSetup) -> sendMessages(messages, channel::sendMessageEmbeds, messageSetup));
    }

    public static Optional<Role> spotBotRole(@NotNull Guild guild) {
        return guild.getRolesByName(DISCORD_BOT_ROLE, true).stream()
                .filter(not(Role::isManaged))
                .max(comparing(Role::getPositionRaw));
    }

    public static <T extends MessageCreateRequest<?> & FluentRestAction<?, ?>> void sendMessages(@NotNull List<EmbedBuilder> messages, @NotNull Function<List<MessageEmbed>, T> sendMessage, @NotNull List<Consumer<MessageCreateRequest<?>>> messageSetup) {
        // Discord limit to 10 embeds by message
        asyncOrderedSubmit(split(MAX_MESSAGE_EMBEDS, messages)
                .map(messagesList -> toMessageRequest(messagesList, sendMessage, messageSetup)));
    }

    @NotNull
    private static <T extends MessageCreateRequest<?> & FluentRestAction<?, ?>> RestAction<?> toMessageRequest(@NotNull List<EmbedBuilder> messages, @NotNull Function<List<MessageEmbed>, T> sendMessage, @NotNull List<Consumer<MessageCreateRequest<?>>> messageSetup) {
        List<MessageEmbed> embeds = messages.stream()
                .peek(message -> LOGGER.debug("Sending discord message : {}", message.getDescriptionBuilder()))
                .map(EmbedBuilder::build)
                .toList(); // list of 10 messages

        var messageRequest = sendMessage.apply(embeds);
        messageSetup.forEach(setup -> setup.accept(messageRequest));
        String mentionedRolesAndUsers = Stream.concat(messageRequest.getMentionedRoles().stream().map(str -> "<@&" + str + '>'),
                messageRequest.getMentionedUsers().stream().map(str -> "<@" + str + '>')).collect(joining(" "));
        messageRequest.setContent(mentionedRolesAndUsers);
        return messageRequest;
    }

    // this ensures the rest action are done in order
    private static void asyncOrderedSubmit(@NotNull Stream<RestAction<?>> restActions) {
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
                    .setRequestTimeoutRetry(true)
                    .build()
                    .awaitReady();
        } catch (Exception e) {
            LOGGER.error("Unable to establish discord connection");
            throw new IllegalStateException(e);
        }
    }

    @NotNull
    public Guild getDiscordServer(long discordServerId) {
        LOGGER.debug("Retrieving discord server {}...", discordServerId);
        return Optional.ofNullable(jda.getGuildById(discordServerId))
                .orElseThrow(() -> new IllegalStateException("Failed to load discord server " + discordServerId));
    }

    @NotNull
    public static Optional<TextChannel> getSpotBotChannel(@NotNull Guild guild) {
        return guild.getTextChannelsByName(DISCORD_BOT_CHANNEL, true)
                .stream().findFirst();
    }

    @NotNull
    private Optional<MessageChannel> getPrivateChannel(long userId) {
        LOGGER.debug("Retrieving user private channel {}...", userId);
        return privateChannelCache.get(userId, this::loadPrivateChannel);
    }

    private Optional<MessageChannel> loadPrivateChannel(long userId) {
        try {
            return Optional.of(jda.retrieveUserById(userId).complete().openPrivateChannel().complete());
        } catch (RuntimeException e) {
            LOGGER.warn("Failed to retrieve discord private channel for user " + userId, e);
            return empty();
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
                event.replyEmbeds(embedBuilder("Sorry !", Color.black,
                                "SpotBot disabled on this channel. Use it in private or on channel " +
                                        Optional.ofNullable(event.getGuild()).flatMap(Discord::getSpotBotChannel)
                                                .map(Channel::getAsMention).orElse('#' + DISCORD_BOT_CHANNEL)).build())
                        .setEphemeral(true)
                        .queue(message -> message.deleteOriginal().queueAfter(5, TimeUnit.SECONDS));
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
                LOGGER.warn("Internal error while processing discord command: " + command.name, e);
                command.reply(embedBuilder("Oups !", Color.red,
                        command.user.getAsMention() + " Something get wrong ! **Internal Error** "));
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
