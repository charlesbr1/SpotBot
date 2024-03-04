package org.sbot.commands.context;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
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
import static org.sbot.commands.Commands.INTERACTION_ID_SEPARATOR;
import static org.sbot.entities.User.DEFAULT_LOCALE;
import static org.sbot.entities.alerts.Alert.PRIVATE_MESSAGES;
import static org.sbot.utils.ArgumentValidator.requireNotBlank;

public abstract class CommandContext implements Context {

    protected static final Logger LOGGER = LogManager.getLogger(CommandContext.class);

    public static final String TOO_MANY_ARGUMENTS = "Too many arguments provided";

    private final @NotNull Context context;
    public final @NotNull String name;
    public final @NotNull User user;
    public final @NotNull Locale locale;
    public final @Nullable ZoneId timezone;
    public final @NotNull ArgumentReader args;
    public final @Nullable Member member;


    private CommandContext(@NotNull Context context, @NotNull Locale locale, @Nullable ZoneId timezone, @NotNull MessageReceivedEvent event, @NotNull String command) {
        this.context = requireNonNull(context);
        this.args = new StringArgumentReader(command);
        this.name = requireNotBlank(args.getString("").orElseThrow(() -> new IllegalArgumentException("Missing command")), "name");
        this.user = requireNonNull(event.getAuthor());
        this.locale = requireNonNull(locale);
        this.timezone = timezone;
        this.member = event.getMember();
    }

    private CommandContext(@NotNull Context context, @NotNull Locale locale, @Nullable ZoneId timezone, @NotNull SlashCommandInteractionEvent event) {
        this.context = requireNonNull(context);
        this.name = requireNotBlank(event.getName(), "name");
        this.user = requireNonNull(event.getUser());
        this.locale = requireNonNull(locale);
        this.timezone = timezone;
        this.args = new SlashArgumentReader(event);
        this.member = event.getMember();
    }

    private CommandContext(@NotNull Context context, @NotNull Locale locale, @Nullable ZoneId timezone, @NotNull GenericInteractionCreateEvent event, @NotNull String componentId, @NotNull String args) {
        this.context = requireNonNull(context);
        String[] nameId = componentId.split(INTERACTION_ID_SEPARATOR);
        if(nameId.length != 2) {
            throw new IllegalArgumentException("Invalid componentId : " + componentId);
        }
        this.name = requireNotBlank(nameId[0].strip(), "name");
        this.user = requireNonNull(event.getUser());
        this.locale = requireNonNull(locale);
        this.timezone = timezone;
        String alertId = requireNotBlank(nameId[1].strip(), "alertId");
        this.args = new StringArgumentReader(alertId + ' ' + requireNonNull(args));
        this.member = event.getMember();
    }

    private CommandContext(@NotNull CommandContext commandContext, @NotNull String arguments) {
        this.context = commandContext.context;
        this.args = new StringArgumentReader(arguments);
        this.name = commandContext.name;
        this.user = commandContext.user;
        this.locale = commandContext.locale;
        this.timezone = commandContext.timezone;
        this.member = commandContext.member;
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

    public static CommandContext of(@NotNull Context context, @Nullable org.sbot.entities.User user, @NotNull MessageReceivedEvent event, @NotNull String command) {
        requireNonNull(event.getMessage());
        return new CommandContext(context, null != user ? user.locale() : DEFAULT_LOCALE, null != user ? user.timeZone() : null, event, command) {
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

    public static CommandContext of(@NotNull Context context, @NotNull org.sbot.entities.User user, @NotNull SlashCommandInteractionEvent event) {
        return new CommandContext(context, user.locale(), user.timeZone(), event) {
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

    public static CommandContext of(@NotNull Context context, @NotNull org.sbot.entities.User user, @NotNull StringSelectInteractionEvent event) {
        return new CommandContext(context, user.locale(), user.timeZone(), event, event.getComponentId(), String.join(" ", event.getValues())) {
            @Override
            public void reply(@NotNull List<Message> messages, int ttlSeconds) {
                reply(messages, modal -> event.replyModal(modal).queue(),
                        msg -> Discord.replyMessages(messages, event.getHook().setEphemeral(true), ttlSeconds),
                        editMapper -> event.editMessage(editMapper.apply(MessageEditBuilder.fromMessage(event.getMessage()))).queue());
            }
        };
    }

    public static CommandContext of(@NotNull Context context, @NotNull org.sbot.entities.User user, @NotNull ModalInteractionEvent event) {
        return new CommandContext(context, user.locale(), user.timeZone(), event, event.getModalId(), event.getValues().stream()
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
        return null != member ? member.getGuild().getIdLong() : PRIVATE_MESSAGES;
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
