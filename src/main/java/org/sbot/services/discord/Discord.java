package org.sbot.services.discord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.utils.Compression;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.services.context.Context;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Comparator.comparing;
import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static net.dv8tion.jda.api.entities.Message.MAX_EMBED_COUNT;
import static net.dv8tion.jda.api.requests.ErrorResponse.*;
import static org.sbot.utils.PartitionSpliterator.split;
import static org.sbot.utils.PropertiesReader.readFile;

public final class Discord {

    private static final Logger LOGGER = LogManager.getLogger(Discord.class);

    public static final int MESSAGE_PAGE_SIZE = 1001; // this limit the number of messages that can be sent in bulk, 1000 + 1 for the next command message

    private final JDA jda;
    private final Map<String, CommandListener> commands = new ConcurrentHashMap<>();
    private final Map<String, InteractionListener> interactions = new ConcurrentHashMap<>();


    public Discord(@NotNull Context context, @NotNull List<CommandListener> commands, @NotNull List<InteractionListener> interactions) {
        jda = loadDiscordConnection(context.parameters().discordTokenFile());
        registerCommands(commands);
        registerInteractions(interactions);
        jda.addEventListener(new EventAdapter(context));
    }

    // notifications service
    public static void sendMessage(@NotNull MessageChannel channel, @NotNull org.sbot.entities.Message message, @NotNull Runnable onSuccess, @NotNull Consumer<Boolean> onFailure) {
        sendMessages(List.of(message), channel::sendMessageEmbeds, onSuccess, onFailure, 0);
    }

    // CommandContext responses
    public static void sendMessages(@NotNull List<org.sbot.entities.Message> messages, @NotNull Function<List<MessageEmbed>, MessageCreateAction> mapper, int ttlSeconds) {
        sendMessages(messages, mapper, null, null, ttlSeconds);
    }


    private static void sendMessages(@NotNull List<org.sbot.entities.Message> messages, @NotNull Function<List<MessageEmbed>, MessageCreateAction> mapper, @Nullable Runnable onSuccess, @Nullable Consumer<Boolean> onFailure, int ttlSeconds) {
        submitWithTtl(messages.stream().flatMap(message -> asMessageRequests(message, mapper)), onSuccess, onFailure, ttlSeconds);
    }

    // CommandContext reply
    public static void replyMessages(@NotNull List<org.sbot.entities.Message> messages, @NotNull InteractionHook interactionHook, int ttlSeconds) {
        submitWithTtl(messages.stream().flatMap(message -> asMessageRequests(message, interactionHook::sendMessageEmbeds)), null, null, ttlSeconds);
    }

    static <T extends MessageCreateRequest<?>> Stream<T> asMessageRequests(@NotNull org.sbot.entities.Message message, @NotNull Function<List<MessageEmbed>, T> mapper) {
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

    private static final Consumer<Message> VOID_HANDLER = m -> {};

    private static void submitWithTtl(@NotNull Stream<RestAction<Message>> restActions, @Nullable Runnable onSuccess, @Nullable Consumer<Boolean> onFailure, int ttlSeconds) {
        Consumer<Message> successHandler = ttlSeconds <= 0 ?
                (null != onSuccess ? message -> onSuccess.run() : VOID_HANDLER) :
                message -> {
                    try {
                        message.delete().queueAfter(ttlSeconds, TimeUnit.SECONDS, null,
                                new ErrorHandler(err -> LOGGER.error("Unable to delete message {} : {}", message.getId(), err.getMessage())));
                    } finally {
                        Optional.ofNullable(onSuccess).ifPresent(Runnable::run);
                    }
                };
        var errorHandler = null != onFailure ? errorHandler(requireNonNull(onSuccess), onFailure) :
                new ErrorHandler(err -> LOGGER.error("Unable to send message : {}", err.getMessage()));
        // jda api ensures that the messages posted on a channel are sent in order
        restActions.forEach(restAction -> restAction.queue(successHandler, errorHandler));
    }

    @NotNull
    static ErrorHandler errorHandler(@NotNull Runnable onSuccess, @NotNull Consumer<Boolean> onFailure) {
        return new ErrorHandler(ex -> {
            LOGGER.info("Exception occurred while sending discord message", ex);
            onFailure.accept(false); // network error, will try to send the message again
        }).handle(List.of(UNKNOWN_USER, UNKNOWN_GUILD, UNKNOWN_CHANNEL, NO_USER_WITH_TAG_EXISTS, REQUEST_ENTITY_TOO_LARGE, EMPTY_MESSAGE, MESSAGE_BLOCKED_BY_HARMFUL_LINK_FILTER, MESSAGE_BLOCKED_BY_AUTOMOD, TITLE_BLOCKED_BY_AUTOMOD), e -> {
            LOGGER.info("Failed to send message, user or server deleted, or message has unsafe content", e);
            onSuccess.run(); // this will consider the message sent and delete it
        }).handle(List.of(CANNOT_SEND_TO_USER), e -> {
            LOGGER.info("Failed to send message, user leaved or blocked private messages", e);
            onFailure.accept(true); // this will set the message as blocked
        });
    }

    @NotNull
    private JDA loadDiscordConnection(@NotNull String tokenFile) {
        try {
            LOGGER.info("Loading discord connection...");
            return JDABuilder.createLight(readFile(tokenFile), // read the file here to avoid keeping the discord token in memory
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.GUILD_MEMBERS, // for user retrieval (migrate command)
                            GatewayIntent.DIRECT_MESSAGES)
                    .setActivity(Activity.watching("prices"))
                    .setCompression(Compression.ZLIB)
                    .setAutoReconnect(true)
                    .setRequestTimeoutRetry(true)
                    .build()
                    .awaitReady();
        } catch (InterruptedException e) {
            LOGGER.error("Unable to establish discord connection", e);
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    public void userPrivateChannel(@NotNull String userId, @NotNull Consumer<MessageChannel> onSuccess, @NotNull ErrorHandler onFailure) {
        jda.retrieveUserById(userId)
                .flatMap(User::openPrivateChannel)
                .queue(onSuccess, onFailure);
    }

    @Nullable
    public Guild guildServer(@NotNull String guildServerId) {
        return guildServer(Long.parseLong(guildServerId)).orElse(null);
    }

    public Optional<Guild> guildServer(long guildServerId) {
        LOGGER.debug("Retrieving discord server {}...", guildServerId);
        return Optional.ofNullable(jda.getGuildById(guildServerId));
    }

    public static Optional<TextChannel> spotBotChannel(@Nullable Guild guild, @NotNull String spotBotChannel) {
        return null != guild ? guild.getTextChannelsByName(spotBotChannel, false)
                .stream().findFirst() : Optional.empty();
    }

    public static Optional<Role> spotBotRole(@Nullable Guild guild, @NotNull String spotBotRole) {
        return null != guild ? guild.getRolesByName(spotBotRole, false).stream()
                .filter(not(Role::isManaged))
                .max(comparing(Role::getPositionRaw)) : Optional.empty();
    }

    @NotNull
    public static String guildName(@NotNull Guild guild) {
        return guild.getName() + " (" + guild.getIdLong() + ")";
    }

    @NotNull
    public String spotBotUserMention() {
        return jda.getSelfUser().getAsMention();
    }

    public CommandListener getCommandListener(@NotNull String command) {
        return commands.get(command);
    }

    public InteractionListener getInteractionListener(@NotNull String interaction) {
        return interactions.get(interaction);
    }

    private void registerCommands(List<CommandListener> commands) {
        jda.updateCommands().addCommands(commands.stream()
                .filter(this::registerCommand)
                .map(CommandListener::options).toList()).queue();
    }

    boolean registerCommand(@NotNull CommandListener commandListener) {
        LOGGER.info("Registering discord command: {}", commandListener.name());
        if(null != commands.put(commandListener.name(), commandListener)) {
            LOGGER.warn("Discord command {} was already registered", commandListener.name());
            return false;
        }
        return commandListener.isSlashCommand();
    }

    void registerInteractions(@NotNull List<InteractionListener> interactionListeners) {
        interactionListeners.forEach(interactionListener -> {
            LOGGER.info("Registering discord interaction: {}", interactionListener.name());
            if(null != interactions.put(interactionListener.name(), interactionListener)) {
                LOGGER.warn("Discord interaction {} was already registered", interactionListener.name());
            }
        });
    }
}
