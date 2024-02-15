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
import org.sbot.commands.UpdateCommand;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.Message;
import org.sbot.services.discord.InteractionListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static org.sbot.commands.CommandAdapter.ALERT_TIPS;
import static org.sbot.commands.CommandAdapter.embedBuilder;
import static org.sbot.commands.ListCommand.ALERT_TITLE_PAIR_FOOTER;
import static org.sbot.commands.UpdateCommand.*;
import static org.sbot.commands.interactions.SelectEditInteraction.*;
import static org.sbot.entities.alerts.Alert.DISABLED;
import static org.sbot.utils.ArgumentValidator.requirePositive;

public final class ModalEditInteraction implements InteractionListener {

    private static final Logger LOGGER = LogManager.getLogger(ModalEditInteraction.class);

    private static final String NAME = "edit-field";
    private static final String UPDATE_FAILED_FOOTER = "```\n";

    public static Modal updateModalOf(long alertId, @NotNull String field, int minLength, int maxLength) {
        TextInput inputValue = TextInput.create(field, field, TextInputStyle.SHORT)
                .setPlaceholder("edit " + field)
                .setRequiredRange(minLength, maxLength)
                .build();
        return Modal.create(NAME + "#" + alertId, "Edit Alert " + alertId)
                .addComponents(ActionRow.of(inputValue)).build();
    }

    @NotNull
    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void onInteraction(@NotNull CommandContext context) {
        long alertId = requirePositive(context.args.getMandatoryLong("alert_id"));
        String field = context.args.getMandatoryString("field");
        String value, newMessage = null;
        if(CHOICE_MESSAGE.equals(field)) {
            value = newMessage = context.args.getLastArgs("value")
                    .orElseThrow(() -> new IllegalArgumentException("Missing message value"));
        } else {
            value = context.args.getMandatoryString("value");
            context.noMoreArgs();
        }
        try {
            new UpdateCommand().onCommand(context.withArgumentsAndReplyMapper(field + " " + alertId + " " + value,
                    replyMapper(newMessage, alertId)));
        } catch (RuntimeException e) {
            LOGGER.debug("Error while updating alert", e);
            String failed = "```diff\n- failed to update " + field + " with value : " + value + "\n" + e.getMessage() + UPDATE_FAILED_FOOTER;
            context.reply(replyOriginal(failed), 0);
        }
    }

    @NotNull
    private static Function<List<Message>, List<Message>> replyMapper(@Nullable String newMessage, long alertId) {
        BiFunction<Message, MessageEditBuilder, MessageEditData> editMapper = (message, editBuilder) -> {
            var originalEmbed = getOneItem(editBuilder.getEmbeds());
            var newEmbedBuilder = getOneItem(message.embeds());
            // switch the enable / disable item in menu if enabled state changed
            switchEnableItem(editBuilder, alertId, newEmbedBuilder.getDescriptionBuilder().indexOf(DISABLED) >= 0);
            // update message reply with update command reply updated to be in same format as ones produced by list command
            return editBuilder.setEmbeds(updatedMessageEmbeds(originalEmbed, newEmbedBuilder, newMessage, false)).build();
        };
        return messages -> messages.stream().map(message -> Message.of(editBuilder -> editMapper.apply(message, editBuilder))).toList();
    }

    private static void switchEnableItem(@NotNull MessageEditBuilder editBuilder, long alertId, boolean isDisabled) {
        StringSelectMenu select = (StringSelectMenu) getOneItem(getOneItem(editBuilder.getComponents()).getActionComponents());
        Optional.ofNullable(switchEnableItem(select.getOptions(), isDisabled)).ifPresent(options -> {
            options.remove(0); // first item ("edit" default entry) must be removed, it still appears though, don't know why...
            var selectComponent = SelectEditInteraction
                    .selectMenu(SelectEditInteraction.NAME + "#" + alertId)
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
                options.set(i, disableOption()); // i should be = 1
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
        Function<MessageEditBuilder, MessageEditData> editMapper = editBuilder -> {
            var originalEmbed = getOneItem(editBuilder.getEmbeds());
            var embedBuilder = null != errorMessage ? embedBuilder(errorMessage) : // this will prepend the provided errorMessage
                    new EmbedBuilder(); // this will just restore the original description, removing errors or update adds
            return editBuilder.setEmbeds(updatedMessageEmbeds(originalEmbed, embedBuilder, null, true)).build();
        };
        return List.of(Message.of(editMapper));
    }

    private static <T> T getOneItem(@NotNull List<T> list) {
        if(list.size() != 1) {
            throw new IllegalArgumentException("Unexpected list size (wanted 1) : " + list.size() + ", content : " + list);
        }
        return list.get(0);
    }

    @NotNull
    private static List<MessageEmbed> updatedMessageEmbeds(@NotNull MessageEmbed originalEmbed, @NotNull EmbedBuilder newEmbedBuilder, @Nullable String newMessage, boolean asOriginal) {
        // newEmbedBuilder is coming from UpdateCommand reply and should contain properly updated embeds description, with some adds to clean up
        newEmbedBuilder.setTitle(originalEmbed.getTitle());
        if(asOriginal) { // restore message, prepend error message if any
            asOriginal(originalEmbed, newEmbedBuilder);
        } else if(null != newMessage) { // update in the title
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
