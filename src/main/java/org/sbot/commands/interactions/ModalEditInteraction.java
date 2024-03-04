package org.sbot.commands.interactions;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageEmbed.Footer;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.commands.DeleteCommand;
import org.sbot.commands.MigrateCommand;
import org.sbot.commands.UpdateCommand;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.Message;
import org.sbot.services.discord.CommandListener;
import org.sbot.services.discord.InteractionListener;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;
import static org.sbot.commands.CommandAdapter.ALERT_TIPS;
import static org.sbot.commands.CommandAdapter.embedBuilder;
import static org.sbot.commands.ListCommand.ALERT_TITLE_PAIR_FOOTER;
import static org.sbot.commands.UpdateCommand.*;
import static org.sbot.commands.interactions.SelectEditInteraction.*;
import static org.sbot.entities.alerts.Alert.DISABLED;
import static org.sbot.utils.ArgumentValidator.requireOneItem;
import static org.sbot.utils.ArgumentValidator.requirePositive;

public final class ModalEditInteraction implements InteractionListener {

    private static final Logger LOGGER = LogManager.getLogger(ModalEditInteraction.class);

    private static final String NAME = "edit-field";
    private static final String UPDATE_FAILED_FOOTER = "```\n";

    public static Modal deleteModalOf(long alertId) {
        TextInput input = TextInput.create(CHOICE_DELETE, "alert will be be deleted", TextInputStyle.SHORT)
                .setPlaceholder("type 'ok' to delete").setRequiredRange(0, 2).build();
        return Modal.create(NAME + "#" + alertId, "Delete Alert #" + alertId)
                .addComponents(ActionRow.of(input)).build();
    }

    public static Modal updateModalOf(long alertId, @NotNull String field, @NotNull String hint, int minLength, int maxLength) {
        TextInput input = TextInput.create(field, field, TextInputStyle.SHORT)
                .setPlaceholder(hint)
                .setRequiredRange(minLength, maxLength)
                .build();
        return Modal.create(NAME + "#" + alertId, "Edit Alert #" + alertId)
                .addComponents(ActionRow.of(input)).build();
    }

    @NotNull
    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void onInteraction(@NotNull CommandContext context) {
        String field = "?";
        String value = null;
        Long alertId = null;
        try {
            alertId = requirePositive(context.args.getMandatoryLong(ALERT_ID_ARGUMENT));
            field = context.args.getMandatoryString(SELECTION_ARGUMENT);
            CommandListener command;
            CommandContext commandContext;
            if(CHOICE_DELETE.equals(field)) {
                if("ok".equalsIgnoreCase(context.args.getMandatoryString(VALUE_ARGUMENT))) {
                    command = new DeleteCommand();
                    commandContext = context.noMoreArgs().withArgumentsAndReplyMapper(String.valueOf(alertId), replaceMapper());
                } else {
                    context.noMoreArgs().reply(replyOriginal(null), 0);
                    return;
                }
            } else {
                value =  context.args.getLastArgs(VALUE_ARGUMENT).orElseThrow(() -> new IllegalArgumentException("Missing update value"));
                if(CHOICE_MIGRATE.equals(field)) {
                    command = new MigrateCommand();
                    commandContext = context.withArgumentsAndReplyMapper(alertId + " " + value, replaceMapper());
                } else {
                    command = new UpdateCommand();
                    commandContext = context.withArgumentsAndReplyMapper(field + " " + alertId + " " + value,
                            fromUpdateMapper(CHOICE_MESSAGE.equals(field) ? value : null, alertId));
                }
            }
            command.onCommand(commandContext);
        } catch (RuntimeException e) { // always reply with an edited message
            LOGGER.debug("Error while updating alert", e);
            String failed = "```diff\n- failed to " + switch (field) {
                case CHOICE_DELETE -> "delete alert " + alertId;
                case CHOICE_MIGRATE -> "migrate alert " + alertId + " to guild " + value;
                default -> "update " + field + " with value : " + value;
            } + "\n" + e.getMessage() + UPDATE_FAILED_FOOTER;
            context.reply(replyOriginal(failed), 0);
        }
    }

    @NotNull
    private static UnaryOperator<List<Message>> replaceMapper() {
        // replace listed alert content with result of delete/migrate command, this remove the menu interaction
        BiFunction<Message, MessageEditBuilder, MessageEditData> editMapper = (message, editBuilder) -> {
            var newEmbedBuilder = requireOneItem(message.embeds());
            var built = newEmbedBuilder.build();
            if(DENIED_COLOR.equals(built.getColor())) { // user rights changed
                throw new ConcurrentModificationException(built.getDescription());
            }
            var originalEmbed = requireOneItem(editBuilder.getEmbeds());
            newEmbedBuilder.setTitle(null);
            newEmbedBuilder.setColor(originalEmbed.getColor());
            editBuilder.setEmbeds(newEmbedBuilder.build());
            editBuilder.setComponents(Collections.emptyList());
            return editBuilder.build();
        };
        return messages -> messages.stream().map(message -> Message.of(editBuilder -> editMapper.apply(message, editBuilder))).toList();
    }

    @NotNull
    private static UnaryOperator<List<Message>> fromUpdateMapper(@Nullable String newMessage, long alertId) {
        // update listed alert content with new state and optional error message
        BiFunction<Message, MessageEditBuilder, MessageEditData> editMapper = (message, editBuilder) -> {
            var newEmbedBuilder = requireOneItem(message.embeds());
            var built = newEmbedBuilder.build();
            if(DENIED_COLOR.equals(built.getColor())) { // user rights changed
                throw new ConcurrentModificationException(built.getDescription());
            }
            var originalEmbed = requireOneItem(editBuilder.getEmbeds());
            // switch the enable / disable item in menu if enabled state changed
            switchEnableItem(editBuilder, alertId, requireNonNull(built.getDescription()).contains(DISABLED));
            // update message reply with update command reply updated to be in same format as ones produced by list command
            return editBuilder.setEmbeds(updatedMessageEmbeds(originalEmbed, newEmbedBuilder, newMessage, false)).build();
        };
        return messages -> messages.stream().map(message -> Message.of(editBuilder -> editMapper.apply(message, editBuilder))).toList();
    }

    private static void switchEnableItem(@NotNull MessageEditBuilder editBuilder, long alertId, boolean isDisabled) {
        StringSelectMenu select = (StringSelectMenu) requireOneItem(requireOneItem(editBuilder.getComponents()).getActionComponents());
        Optional.ofNullable(switchEnableItem(select.getOptions(), isDisabled)).ifPresent(options -> {
            var selectComponent = StringSelectMenu.create(SelectEditInteraction.NAME + "#" + alertId)
                    .addOptions(options);
            editBuilder.setComponents(List.of(ActionRow.of(selectComponent.build())));
        });
    }

    @Nullable
    private static List<SelectOption> switchEnableItem(@NotNull List<SelectOption> selectOptions, boolean isDisabled) {
        for(int i = 0; i < selectOptions.size(); i++) {
            var option = selectOptions.get(i);
            if(!isDisabled && CHOICE_ENABLE.equals(option.getLabel())) {
                var options = new ArrayList<>(selectOptions);
                options.set(i, disableOption()); // i should be 1 (remainder) or 3 (range / trend)
                return options;
            } else if (isDisabled && CHOICE_DISABLE.equals(option.getLabel())) {
                var options = new ArrayList<>(selectOptions);
                options.set(i, enableOption(false));
                return options;
            }
        }
        return null;
    }

    @NotNull
    static List<Message> replyOriginal(@Nullable String errorMessage) {
        // update listed alert content with optional error message
        Function<MessageEditBuilder, MessageEditData> editMapper = editBuilder -> {
            var originalEmbed = requireOneItem(editBuilder.getEmbeds());
            var embedBuilder = null != errorMessage ? embedBuilder(errorMessage) : // this will prepend the provided errorMessage
                    new EmbedBuilder(); // this will just restore the original description, removing errors or update adds
            return editBuilder.setEmbeds(updatedMessageEmbeds(originalEmbed, embedBuilder, null, true)).build();
        };
        return List.of(Message.of(editMapper));
    }

    @NotNull
    private static List<MessageEmbed> updatedMessageEmbeds(@NotNull MessageEmbed originalEmbed, @NotNull EmbedBuilder newEmbedBuilder, @Nullable String newMessage, boolean asOriginal) {
        // newEmbedBuilder is coming from UpdateCommand reply and should contain properly updated embeds description, with some adds to clean up
        newEmbedBuilder.setTitle(originalEmbed.getTitle());
        if(asOriginal) { // restore message, prepend error message if any
            asOriginal(originalEmbed, newEmbedBuilder);
        } else if(null != newMessage) { // update message in the title
            int index = requirePositive(requireNonNull(originalEmbed.getTitle()).indexOf(ALERT_TITLE_PAIR_FOOTER));
            newEmbedBuilder.setTitle(originalEmbed.getTitle().substring(0, index + ALERT_TITLE_PAIR_FOOTER.length()) + newMessage);
        }
        newEmbedBuilder.setFooter(Optional.ofNullable(originalEmbed.getFooter()).map(Footer::getText).orElse(null));
        return List.of(newEmbedBuilder.build());
    }

    private static void asOriginal(@NotNull MessageEmbed originalEmbed, @NotNull EmbedBuilder newEmbedBuilder) {
        newEmbedBuilder.setColor(originalEmbed.getColor());
        originalEmbed.getFields().forEach(newEmbedBuilder::addField);
        Optional.ofNullable(originalEmbed.getDescription()).map(ModalEditInteraction::originalDescription)
                .ifPresent(newEmbedBuilder.getDescriptionBuilder()::append);
    }

    private static String originalDescription(@NotNull String originalDescription) {
        // remove messages append or prepended by update command to the original list description
        // another option would be to rebuild a new message from alert, here it avoid a database access
        for(String header : List.of(UPDATE_FAILED_FOOTER, UPDATE_SUCCESS_FOOTER, UPDATE_ENABLED_HEADER, UPDATE_DISABLED_HEADER)) {
            int index = originalDescription.indexOf(header); // prepended at the start
            if(index >= 0) {
                originalDescription = originalDescription.substring(header.length() + index);
            }
        }
        for(String footer : List.of(ALERT_TIPS)) {
            int index = originalDescription.indexOf(footer); // appended at the end
            if(index >= 0) {
                originalDescription = originalDescription.substring(0, index);
            }
        }
        return originalDescription;
    }
}
