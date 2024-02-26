package org.sbot.services.discord;

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
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.api.utils.Compression;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.sbot.SpotBot;
import org.sbot.entities.Message;
import org.sbot.services.context.Context;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Comparator.comparing;
import static java.util.Optional.empty;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static net.dv8tion.jda.api.entities.Message.MAX_EMBED_COUNT;
import static org.sbot.utils.PartitionSpliterator.split;
import static org.sbot.utils.PropertiesReader.readFile;

public final class Discord {

    private static final Logger LOGGER = LogManager.getLogger(Discord.class);

    public static final int MESSAGE_PAGE_SIZE = 1001; // this limit the number of messages that can be sent in bulk, 1000 + 1 for the next command message

    public static final String DISCORD_BOT_CHANNEL = SpotBot.appProperties.get("discord.bot.channel");
    public static final String DISCORD_BOT_ROLE = SpotBot.appProperties.get("discord.bot.role");

    private static final int PRIVATE_CHANNEL_CACHE_TLL_MIN = Math.max(1, SpotBot.appProperties.getIntOr("discord.private-channel.cache.ttl-minutes", 5));


    @FunctionalInterface
    public interface DiscordLoader {
        Discord newInstance(@NotNull Context context, @NotNull List<CommandListener> commands, @NotNull List<InteractionListener> interactions);
    }

    @FunctionalInterface
    public interface BotChannel {
        void sendMessages(@NotNull List<Message> messages);
    }

    private final JDA jda;
    private final Map<String, CommandListener> commands = new ConcurrentHashMap<>();
    private final Map<String, InteractionListener> interactions = new ConcurrentHashMap<>();

    // this allows to keep the minimum jda cache settings, user private channel may be requested by many threads in bulk
    private final Cache<Long, Optional<MessageChannel>> privateChannelCache = Caffeine.newBuilder()
            .expireAfterWrite(PRIVATE_CHANNEL_CACHE_TLL_MIN, TimeUnit.MINUTES)
            .maximumSize(Short.MAX_VALUE)
            .build();

    public Discord(@NotNull Context context, @NotNull List<CommandListener> commands, @NotNull List<InteractionListener> interactions) {
        jda = loadDiscordConnection(context.parameters().discordTokenFile());
        registerCommands(commands);
        registerInteractions(interactions);
        jda.addEventListener(new EventAdapter(context));
    }

    @NotNull
    public static String guildName(@NotNull Guild guild) {
        return guild.getName() + " (" + guild.getIdLong() + ")";
    }

    public static Optional<BotChannel> spotBotChannel(@NotNull Guild guild) {
        return getSpotBotChannel(guild).map(channel ->
                messages -> sendMessages(messages, channel::sendMessageEmbeds, 0));
    }

    @NotNull
    public String spotBotUserMention() {
        return jda.getSelfUser().getAsMention();
    }

    public Optional<BotChannel> userChannel(long userId) {
        return getPrivateChannel(userId).map(channel ->
                messages -> sendMessages(messages, channel::sendMessageEmbeds, 0));
    }

    public static Optional<Role> spotBotRole(@NotNull Guild guild) {
        return guild.getRolesByName(DISCORD_BOT_ROLE, true).stream()
                .filter(not(Role::isManaged))
                .max(comparing(Role::getPositionRaw));
    }

    public static void sendMessages(@NotNull List<Message> messages, @NotNull Function<List<MessageEmbed>, MessageCreateRequest<?>> mapper, int ttlSeconds) {
        asyncOrderedSubmit(messages.stream().flatMap(message -> asMessageRequests(message, mapper)), ttlSeconds);
    }

    private static Stream<MessageCreateRequest<?>> asMessageRequests(@NotNull Message message, @NotNull Function<List<MessageEmbed>, MessageCreateRequest<?>> mapper) {
        return split(MAX_EMBED_COUNT, message.embeds().stream().map(EmbedBuilder::build)).map(mapper)
                .peek(request -> {
                    Optional.ofNullable(message.files()).map(files -> files.stream().map(file -> FileUpload.fromData(file.content(), file.name())).toList()).ifPresent(request::setFiles);
                    Optional.ofNullable(message.component()).ifPresent(request::setComponents);
                    Optional.ofNullable(message.mentionRoles()).ifPresent(request::mentionRoles);
                    Optional.ofNullable(message.mentionUsers()).ifPresent(request::mentionUsers);
                    String mentionedRolesAndUsers = Stream.concat(
                                    request.getMentionedRoles().stream().distinct().map(str -> "<@&" + str + '>'),
                                    request.getMentionedUsers().stream().distinct().map(str -> "<@" + str + '>'))
                            .collect(joining(" "));
                    if(!mentionedRolesAndUsers.isEmpty()) {
                        request.setContent(mentionedRolesAndUsers);
                    }
                });
    }

    // this ensures the rest action are done in order
    private static void asyncOrderedSubmit(@NotNull Stream<MessageCreateRequest<?>> restActions, int ttlSeconds) {
        //TODO see if it finally works without chaining, or with restaction flatMap()
        restActions
                .reduce(CompletableFuture.<Void>completedFuture(null),
                        (future, nextMessage) -> future.thenRun(() -> submitWithTtl(nextMessage, ttlSeconds)),
                        CompletableFuture::allOf)
                .whenComplete((v, error) -> {
                    if (null != error) {
                        LOGGER.error("Exception occurred while sending discord message", error);
                    }
                });
    }

    private static void submitWithTtl(MessageCreateRequest<?> message, int ttlSeconds) {
        switch (message) {
            case ReplyCallbackAction reply when ttlSeconds > 0 ->
                    reply.submit().thenApply(m -> m.deleteOriginal().queueAfter(ttlSeconds, TimeUnit.SECONDS));
            case MessageCreateAction reply when ttlSeconds > 0 ->
                    reply.submit().thenApply(m -> m.delete().queueAfter(ttlSeconds, TimeUnit.SECONDS));
            case RestAction<?> reply -> reply.queue();
            case null, default -> throw new IllegalArgumentException("Unsupported message type : " + message);
        }
    }

    @NotNull
    private JDA loadDiscordConnection(@NotNull String tokenFile) {
        try {
            LOGGER.info("Loading discord connection...");
            return JDABuilder.createLight(readFile(tokenFile), // read the file here to avoid keeping the discord token in memory
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

    public InteractionListener getGetInteractionListener(@NotNull String interaction) {
        return interactions.get(interaction);
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

    private void registerInteractions(@NotNull List<InteractionListener> interactionListeners) {
        interactionListeners.forEach(interactionListener -> {
            LOGGER.info("Registering discord interaction: {}", interactionListener.name());
            if(null != interactions.put(interactionListener.name(), interactionListener)) {
                LOGGER.warn("Discord interaction {} was already registered", interactionListener.name());
            }
        });
    }
}
