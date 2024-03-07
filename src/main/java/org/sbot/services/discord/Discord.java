package org.sbot.services.discord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
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
import org.sbot.SpotBot;
import org.sbot.entities.Message;
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

    private final JDA jda;
    private final Map<String, CommandListener> commands = new ConcurrentHashMap<>();
    private final Map<String, InteractionListener> interactions = new ConcurrentHashMap<>();


    public Discord(@NotNull Context context, @NotNull List<CommandListener> commands, @NotNull List<InteractionListener> interactions) {
        jda = loadDiscordConnection(context.parameters().discordTokenFile());
        registerCommands(commands);
        registerInteractions(interactions);
        jda.addEventListener(new EventAdapter(context));
    }

    public void sendGuildMessage(@NotNull Guild guild, @NotNull Message message, @NotNull Runnable onSuccess) {
        spotBotChannel(guild).ifPresent(channel -> sendMessages(List.of(message), channel::sendMessageEmbeds, onSuccess, 0));
    }

    public void sendPrivateMessage(long userId, @NotNull Message message, @Nullable Runnable onSuccess) {
        jda.retrieveUserById(userId) // TODO check cache usage JDABuilder.createLight
                .flatMap(User::openPrivateChannel)
                .onSuccess(channel -> sendMessages(List.of(message), channel::sendMessageEmbeds, onSuccess, 0))
                .queue();
    }

    public static void sendMessages(@NotNull List<Message> messages, @NotNull Function<List<MessageEmbed>, MessageCreateAction> mapper, int ttlSeconds) {
        sendMessages(messages, mapper, null, ttlSeconds);
    }

    private static void sendMessages(@NotNull List<Message> messages, @NotNull Function<List<MessageEmbed>, MessageCreateAction> mapper, @Nullable Runnable onSuccess,  int ttlSeconds) {
        submitWithTtl(messages.stream().flatMap(message -> asMessageRequests(message, mapper)), onSuccess, ttlSeconds);
    }

    public static void replyMessages(@NotNull List<Message> messages, @NotNull InteractionHook interactionHook, int ttlSeconds) {
        submitWithTtl(messages.stream().flatMap(message -> asMessageRequests(message, interactionHook::sendMessageEmbeds)), null, ttlSeconds);
    }

    static <T extends MessageCreateRequest<?>> Stream<T> asMessageRequests(@NotNull Message message, @NotNull Function<List<MessageEmbed>, T> mapper) {
        return split(MAX_EMBED_COUNT, message.embeds().stream().map(EmbedBuilder::build)).map(mapper)
                .map(request -> {
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
                    return request;
                });
    }

    // this ensures the rest action are done in order
    private static void submitWithTtl(@NotNull Stream<RestAction<net.dv8tion.jda.api.entities.Message>> restActions, @Nullable Runnable onSuccess, int ttlSeconds) {
        Consumer<net.dv8tion.jda.api.entities.Message> doNothing = message ->  {};
        Consumer<net.dv8tion.jda.api.entities.Message> delete = message -> message.delete().queueAfter(ttlSeconds, TimeUnit.SECONDS);
        for (var iterator = restActions.iterator(); iterator.hasNext();) {
            var request = iterator.next();
            var success = ttlSeconds > 0 ? delete : doNothing;
            success = null == onSuccess || iterator.hasNext() ? success : success.andThen(m -> onSuccess.run());
            request.queue(success,
                    err -> LOGGER.error("Exception occurred while sending discord message", err));
        }
        // TODO check  if it finally works without chaining
/*        restActions.reduce((message, nextMessage) -> message
                .onSuccess(m -> m.delete().queueAfter(ttlSeconds, TimeUnit.SECONDS))
                .flatMap(m -> nextMessage)).ifPresent(request ->
                request.queue(
                        m -> m.delete().queueAfter(ttlSeconds, TimeUnit.SECONDS),
                        err -> LOGGER.error("Exception occurred while sending discord message", err)));
 */
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
        } catch (Exception e) {
            LOGGER.error("Unable to establish discord connection");
            throw new IllegalStateException(e);
        }
    }

    public Optional<Guild> guildServer(long guildServerId) {
        LOGGER.debug("Retrieving discord server {}...", guildServerId);
        return Optional.ofNullable(jda.getGuildById(guildServerId));
    }

    @NotNull
    public static Optional<TextChannel> spotBotChannel(@NotNull Guild guild) {
        return guild.getTextChannelsByName(DISCORD_BOT_CHANNEL, true)
                .stream().findFirst();
    }

    @NotNull
    public static String guildName(@NotNull Guild guild) {
        return guild.getName() + " (" + guild.getIdLong() + ")";
    }

    @NotNull
    public String spotBotUserMention() {
        return jda.getSelfUser().getAsMention();
    }

    public static Optional<Role> spotBotRole(@NotNull Guild guild) {
        return guild.getRolesByName(DISCORD_BOT_ROLE, true).stream()
                .filter(not(Role::isManaged))
                .max(comparing(Role::getPositionRaw));
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
