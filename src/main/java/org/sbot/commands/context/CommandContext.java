package org.sbot.commands.context;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.commands.reader.ArgumentReader;
import org.sbot.commands.reader.SlashArgumentReader;
import org.sbot.commands.reader.StringArgumentReader;
import org.sbot.entities.Message;
import org.sbot.entities.settings.ServerSettings;
import org.sbot.entities.settings.Settings;
import org.sbot.entities.alerts.ClientType;
import org.sbot.exchanges.Exchanges;
import org.sbot.services.context.Context;
import org.sbot.services.discord.Discord;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static org.sbot.commands.CommandAdapter.isPrivateChannel;
import static org.sbot.commands.interactions.Interactions.alertIdOf;
import static org.sbot.commands.interactions.Interactions.componentIdOf;
import static org.sbot.entities.alerts.Alert.PRIVATE_MESSAGES;
import static org.sbot.entities.alerts.ClientType.DISCORD;
import static org.sbot.utils.ArgumentValidator.requireNotBlank;

public abstract class CommandContext implements Context {

    protected static final Logger LOGGER = LogManager.getLogger(CommandContext.class);

    public static final String TOO_MANY_ARGUMENTS = "Too many arguments provided";

    private final @NotNull Context context;
    public final @NotNull ClientType clientType;
    public final @NotNull String name;
    public final long userId;
    public final @NotNull String userName;
    public final @NotNull Locale locale;
    public final @NotNull ZoneId timezone;
    public final @NotNull ServerSettings serverSettings;
    public final @NotNull ArgumentReader args;
    public final @Nullable Member discordMember; // discord specific field when a command comes from a guild (not a PM)


    private CommandContext(@NotNull Context context, @NotNull ClientType clientType, @NotNull Settings settings, @NotNull MessageReceivedEvent event, @NotNull String command) {
        this.context = requireNonNull(context);
        this.clientType = requireNonNull(clientType);
        this.args = new StringArgumentReader(command);
        this.name = args.getMandatoryString("command");
        this.userId = event.getAuthor().getIdLong();
        this.userName = event.getAuthor().getEffectiveName();
        this.locale = settings.userSettings().locale();
        this.timezone = settings.userSettings().timezone();
        this.serverSettings = settings.serverSettings();
        this.discordMember = event.getMember();
    }

    private CommandContext(@NotNull Context context, @NotNull ClientType clientType, @NotNull Settings settings, @NotNull SlashCommandInteractionEvent event) {
        this.context = requireNonNull(context);
        this.clientType = requireNonNull(clientType);
        this.args = new SlashArgumentReader(event);
        this.name = requireNotBlank(event.getName(), "command");
        this.userId = event.getUser().getIdLong();
        this.userName = event.getUser().getEffectiveName();
        this.locale = settings.userSettings().locale();
        this.timezone = settings.userSettings().timezone();
        this.serverSettings = settings.serverSettings();
        this.discordMember = event.getMember();
    }

    private CommandContext(@NotNull Context context, @NotNull ClientType clientType, @NotNull Settings settings, @NotNull GenericInteractionCreateEvent event, @NotNull String interactionId, @NotNull String args) {
        this.context = requireNonNull(context);
        this.clientType = requireNonNull(clientType);
        this.args = new StringArgumentReader(alertIdOf(interactionId) + ' ' + requireNonNull(args));
        this.name = componentIdOf(interactionId);
        this.userId = event.getUser().getIdLong();
        this.userName = event.getUser().getEffectiveName();
        this.locale = settings.userSettings().locale();
        this.timezone = settings.userSettings().timezone();
        this.serverSettings = settings.serverSettings();
        this.discordMember = event.getMember();
    }

    private CommandContext(@NotNull CommandContext commandContext, @NotNull String arguments) {
        this.context = commandContext.context;
        this.clientType = commandContext.clientType;
        this.args = new StringArgumentReader(arguments);
        this.name = commandContext.name;
        this.userId = commandContext.userId;
        this.userName = commandContext.userName;
        this.locale = commandContext.locale;
        this.timezone = commandContext.timezone;
        this.serverSettings = commandContext.serverSettings;
        this.discordMember = commandContext.discordMember;
    }

    public boolean isStringReader() {
        return args instanceof StringArgumentReader;
    }

    public final void reply(@NotNull Message message, int ttlSeconds) {
        reply(List.of(message), ttlSeconds);
    }

    public abstract void reply(@NotNull List<Message> messages, int ttlSeconds);

    private static Optional<Modal> getModal(@NotNull List<Message> messages) {
        return messages.stream().map(Message::modal).filter(Objects::nonNull).findFirst();
    }

    private static Optional<Function<MessageEditBuilder, MessageEditData>> getEditMapper(@NotNull List<Message> messages) {
        return messages.stream().map(Message::editMapper).filter(Objects::nonNull).findFirst();
    }

    static void reply(@NotNull List<Message> messages,
                              @NotNull Consumer<Modal> modalReply,
                              @NotNull Consumer<List<Message>> messagesReply,
                              @NotNull Consumer<Function<MessageEditBuilder, MessageEditData>> editReply) {
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("Empty message reply");
        }
        var modal = getModal(messages);
        if (modal.isPresent()) {
            modalReply.accept(modal.get());
        } else {
            var editMapper = getEditMapper(messages);
            if(editMapper.isPresent()) {
                editReply.accept(editMapper.get());
            } else {
                messagesReply.accept(messages);
            }
        }
    }

    public static CommandContext of(@NotNull Context context, @NotNull Settings settings, @NotNull MessageReceivedEvent event, @NotNull String command) {
        requireNonNull(event.getMessage());
        return new CommandContext(context, DISCORD, settings, event, command) {
            boolean firstReply = !isPrivateChannel(this); // this set a "reply to" once on the first message
            @Override
            public void reply(@NotNull List<Message> messages, int ttlSeconds) {
                reply(messages, modal -> { throw new UnsupportedOperationException("No modal on string command reply"); },
                        msg -> Discord.sendMessages(messages, firstReply ? event.getMessage()::replyEmbeds : event.getChannel()::sendMessageEmbeds, ttlSeconds),
                        editMapper -> event.getMessage().editMessage(editMapper.apply(MessageEditBuilder.fromMessage(event.getMessage()))).queue());
                firstReply = false;
            }
        };
    }

    public static CommandContext of(@NotNull Context context, @NotNull Settings settings, @NotNull SlashCommandInteractionEvent event) {
        return new CommandContext(context, DISCORD, settings, event) {
            @Override
            public void reply(@NotNull List<Message> messages, int ttlSeconds) {
                reply(messages, modal -> event.replyModal(modal).queue(),
                        msg -> Discord.replyMessages(messages, event.getHook().setEphemeral(true), ttlSeconds),
                        editMapper -> event.getHook().retrieveOriginal()
                                .queue(message -> message.editMessage(editMapper.apply(MessageEditBuilder.fromMessage(message))).queue(),
                                        err -> LOGGER.error("Failed to retrieve message on slash interaction", err)));
            }
        };
    }

    public static CommandContext of(@NotNull Context context, @NotNull Settings settings, @NotNull StringSelectInteractionEvent event) {
        return new CommandContext(context, DISCORD, settings, event, event.getComponentId(), String.join(" ", event.getValues())) {
            @Override
            public void reply(@NotNull List<Message> messages, int ttlSeconds) {
                reply(messages, modal -> event.replyModal(modal).queue(),
                        msg -> Discord.replyMessages(messages, event.getHook().setEphemeral(true), ttlSeconds),
                        editMapper -> event.editMessage(editMapper.apply(MessageEditBuilder.fromMessage(event.getMessage()))).queue());
            }
        };
    }

    public static CommandContext of(@NotNull Context context, @NotNull Settings settings, @NotNull ModalInteractionEvent event) {
        return new CommandContext(context, DISCORD, settings, event, event.getModalId(), event.getValues().stream()
                .map(m -> m.getId() + " " + m.getAsString()).collect(joining(" "))) {
            @Override
            public void reply(@NotNull List<Message> messages, int ttlSeconds) {
                reply(messages, modal -> { throw new UnsupportedOperationException("No modal on modal command reply"); },
                        msg -> Discord.replyMessages(messages, event.getHook().setEphemeral(true), ttlSeconds),
                        null == event.getMessage() ? editMapper -> { throw new UnsupportedOperationException("This modal have no message"); } :
                                editMapper -> event.editMessage(editMapper.apply(MessageEditBuilder.fromMessage(event.getMessage()))).queue());
            }
        };
    }

    public final CommandContext withArgumentsAndReplyMapper(@NotNull String arguments, @NotNull UnaryOperator<List<Message>> replyMapper) {
        return new CommandContext(this, arguments) {
            @Override
            public void reply(@NotNull List<Message> messages, int ttlSeconds) {
                CommandContext.this.reply(replyMapper.apply(messages), ttlSeconds);
            }
        };
    }

    public final long serverId() {
        return switch (clientType) {
            case DISCORD -> null != discordMember ? discordMember.getGuild().getIdLong() : PRIVATE_MESSAGES;
        };
    }

    public final CommandContext noMoreArgs() {
        if(args.getLastArgs("").filter(not(String::isBlank)).isPresent()) {
            throw new IllegalArgumentException(TOO_MANY_ARGUMENTS);
        }
        return this;
    }

    @NotNull
    @Override
    public final Clock clock() {
        return context.clock();
    }

    @NotNull
    @Override
    public final DataServices dataServices() {
        return context.dataServices();
    }

    @NotNull
    @Override
    public final Services services() {
        return context.services();
    }

    @NotNull
    @Override
    public final Exchanges exchanges() {
        return context.exchanges();
    }

    @NotNull
    @Override
    public final Parameters parameters() {
        return context.parameters();
    }
}
