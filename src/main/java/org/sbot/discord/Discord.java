package org.sbot.discord;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.requests.FluentRestAction;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.api.utils.Compression;
import net.dv8tion.jda.api.utils.messages.MessageCreateRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.sbot.SpotBot;
import org.sbot.services.dao.AlertsDao;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.empty;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static org.sbot.commands.DiscordCommands.DISCORD_COMMANDS;
import static org.sbot.utils.PartitionSpliterator.split;
import static org.sbot.utils.PropertiesReader.readFile;

public final class Discord {

    private static final Logger LOGGER = LogManager.getLogger(Discord.class);

    public static final int MESSAGE_PAGE_SIZE = 1001; // this limit the number of messages that can be sent in bulk, 1000 + 1 for the next command message
    public static final int MAX_MESSAGE_EMBEDS = 10;

    public static final String DISCORD_BOT_CHANNEL = SpotBot.appProperties.get("discord.bot.channel");
    public static final String DISCORD_BOT_ROLE = SpotBot.appProperties.get("discord.bot.role");

    private static final int PRIVATE_CHANNEL_CACHE_TLL_MIN = Math.max(1, SpotBot.appProperties.getIntOr("discord.private-channel.cache.ttl-minutes", 5));


    @FunctionalInterface
    public interface BotChannel {
        void sendMessages(@NotNull List<EmbedBuilder> messages, @NotNull List<Consumer<MessageCreateRequest<?>>> messageSetup);
    }

    public static final String SINGLE_LINE_BLOCK_QUOTE_MARKDOWN = "> ";
    public static final String MULTI_LINE_BLOCK_QUOTE_MARKDOWN = ">>> ";

    private final JDA jda;
    private final Map<String, CommandListener> commands = new ConcurrentHashMap<>();

    // this allows to keep the minimum jda cache settings, user private channel may be requested by many threads in bulk
    private final Cache<Long, Optional<MessageChannel>> privateChannelCache = Caffeine.newBuilder()
            .expireAfterWrite(PRIVATE_CHANNEL_CACHE_TLL_MIN, TimeUnit.MINUTES)
            .maximumSize(Short.MAX_VALUE)
            .build();

    public Discord(@NotNull String tokenFile, @NotNull AlertsDao alertsDao) {
        requireNonNull(alertsDao);
        jda = loadDiscordConnection(tokenFile);
        registerCommands(DISCORD_COMMANDS);
        jda.addEventListener(new EventAdapter(this, alertsDao, jda.getSelfUser().getAsMention()));
    }

    @NotNull
    public static String guildName(@NotNull Guild guild) {
        return guild.getName() + " (" + guild.getIdLong() + ")";
    }

    public static Optional<BotChannel> spotBotChannel(@NotNull Guild guild) {
        return getSpotBotChannel(guild).map(channel ->
                (messages, messageSetup) -> sendMessages(0, messages, channel::sendMessageEmbeds, messageSetup));
    }

    public Optional<BotChannel> userChannel(long userId) {
        return getPrivateChannel(userId).map(channel ->
                (messages, messageSetup) -> sendMessages(0, messages, channel::sendMessageEmbeds, messageSetup));
    }

    public static Optional<Role> spotBotRole(@NotNull Guild guild) {
        return guild.getRolesByName(DISCORD_BOT_ROLE, true).stream()
                .filter(not(Role::isManaged))
                .max(comparing(Role::getPositionRaw));
    }

    public static <T extends MessageCreateRequest<?> & FluentRestAction<?, ?>> void sendMessages(int ttlSeconds, @NotNull List<EmbedBuilder> messages, @NotNull Function<List<MessageEmbed>, T> sendMessage, @NotNull List<Consumer<MessageCreateRequest<?>>> messageSetup) {
        asyncOrderedSubmit(toMessageRequests(messages, sendMessage, messageSetup), ttlSeconds);
    }

    @NotNull
    private static <T extends MessageCreateRequest<?> & FluentRestAction<?, ?>> Stream<RestAction<?>> toMessageRequests(@NotNull List<EmbedBuilder> embedBuilders, @NotNull Function<List<MessageEmbed>, T> sendMessage, @NotNull List<Consumer<MessageCreateRequest<?>>> messageSetup) {
        return split(MAX_MESSAGE_EMBEDS, embedBuilders.stream() // Discord limit to 10 embeds by message
                .peek(message -> LOGGER.debug("Sending discord message : {}", message.getDescriptionBuilder()))
                .map(EmbedBuilder::build)).map(messages -> {
                    var messageRequest = sendMessage.apply(messages);
                    messageSetup.forEach(setup -> setup.accept(messageRequest));
                    String mentionedRolesAndUsers = Stream.concat(messageRequest.getMentionedRoles().stream().map(str -> "<@&" + str + '>'),
                            messageRequest.getMentionedUsers().stream().map(str -> "<@" + str + '>')).collect(joining(" "));
                    messageRequest.setContent(mentionedRolesAndUsers);
                    return messageRequest;
                });
    }

    // this ensures the rest action are done in order
    private static void asyncOrderedSubmit(@NotNull Stream<RestAction<?>> restActions, int ttlSeconds) {
        restActions.limit(MESSAGE_PAGE_SIZE)
                .reduce(CompletableFuture.<Void>completedFuture(null),
                        (future, nextMessage) -> future.thenRun(() -> submitWithTtl(nextMessage, ttlSeconds)),
                        CompletableFuture::allOf)
                .whenComplete((v, error) -> {
                    if (null != error) {
                        LOGGER.error("Exception occurred while sending discord message", error);
                    }
                });
    }

    private static void submitWithTtl(RestAction<?> message, int ttlSeconds) {
        if(ttlSeconds > 0 && message instanceof ReplyCallbackAction) {
            ((ReplyCallbackAction) message).submit()
                    .thenApply(m -> m.deleteOriginal().queueAfter(ttlSeconds, TimeUnit.SECONDS));
        } else if(ttlSeconds > 0 && message instanceof MessageCreateAction) {
            ((MessageCreateAction) message).submit()
                    .thenApply(m -> m.delete().queueAfter(ttlSeconds, TimeUnit.SECONDS));
        } else {
            message.submit();
        }
    }

    @NotNull
    private JDA loadDiscordConnection(@NotNull String tokenFile) {
        try {
            LOGGER.info("Loading discord connection...");
            return JDABuilder.createLight(readFile(tokenFile),
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.DIRECT_MESSAGES)
                    .setActivity(Activity.watching("prices"))
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

    public Optional<Guild> getGuildServer(long guildServerId) {
        LOGGER.debug("Retrieving discord server {}...", guildServerId);
        return Optional.ofNullable(jda.getGuildById(guildServerId));
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

    public CommandListener getGetCommandListener(@NotNull String command) {
        return commands.get(command);
    }

    private void registerCommands(List<CommandListener> commands) {
        jda.updateCommands().addCommands(commands.stream()
                .filter(this::registerCommand)
                .map(CommandListener::options).toList()).queue();
    }

    private boolean registerCommand(@NotNull CommandListener commandListener) {
        LOGGER.info("Registering discord command: {}", commandListener.name());
        if(null != commands.put(commandListener.name(), commandListener)) {
            LOGGER.warn("Discord command {} was already registered", commandListener.name());
            return false;
        }
        return commandListener.isSlashCommand();
    }
}
