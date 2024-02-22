package org.sbot.commands.interactions;

import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import org.jetbrains.annotations.NotNull;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.Message;
import org.sbot.entities.alerts.Alert;
import org.sbot.services.discord.InteractionListener;

import java.util.function.Function;

import static org.sbot.commands.UpdateCommand.*;
import static org.sbot.commands.interactions.ModalEditInteraction.*;
import static org.sbot.entities.alerts.Alert.DEFAULT_REPEAT;
import static org.sbot.entities.alerts.Alert.DEFAULT_SNOOZE_HOURS;
import static org.sbot.entities.alerts.Alert.Type.range;
import static org.sbot.entities.alerts.Alert.Type.remainder;
import static org.sbot.utils.ArgumentValidator.ALERT_MESSAGE_ARG_MAX_LENGTH;
import static org.sbot.utils.ArgumentValidator.requirePositive;
import static org.sbot.utils.Dates.DATE_TIME_FORMAT;

public class SelectEditInteraction implements InteractionListener {

    static final String NAME = "edit-alert";
    private static final String CHOICE_EDIT = "edit";
    public static final String CHOICE_DISABLE = "disable";
    public static final String CHOICE_DELETE = "delete";

    public static StringSelectMenu updateMenuOf(@NotNull Alert alert) {
        return updateMenuOf(NAME + "#" + alert.id, alert);
    }

    public static StringSelectMenu.Builder selectMenu(@NotNull String menuId) {
        return StringSelectMenu.create(menuId)
                .addOptions(SelectOption.of(CHOICE_EDIT, CHOICE_EDIT)
                        .withDescription("edit a field")
                        .withEmoji(Emoji.fromUnicode("U+2699"))
                        .withDefault(true));
    }

    private static StringSelectMenu updateMenuOf(@NotNull String menuId, @NotNull Alert alert) {
        StringSelectMenu.Builder menu = selectMenu(menuId);
        if(remainder == alert.type) {
            menu.addOption(CHOICE_DATE, CHOICE_DATE, "a future date when to trigger the remainder, UTC expected format : " + DATE_TIME_FORMAT, Emoji.fromUnicode("U+1F550"));
            menu.addOption(CHOICE_MESSAGE, CHOICE_MESSAGE, "a message for this remainder (" + ALERT_MESSAGE_ARG_MAX_LENGTH + " chars max)", Emoji.fromUnicode("U+1F5D2"));
        } else {
            if(alert.isEnabled()) {
                menu.addOptions(disableOption());
            } else {
                menu.addOptions(enableOption(alert.repeat > 0));
            }
            menu.addOption(CHOICE_MESSAGE, CHOICE_MESSAGE, "a message to show when the alert is raised : add a link to your AT ! (" + ALERT_MESSAGE_ARG_MAX_LENGTH + " chars max)", Emoji.fromUnicode("U+1F5D2"));
            if(range == alert.type) {
                menu.addOption(DISPLAY_FROM_DATE, CHOICE_FROM_DATE, "a date to start the box, UTC expected format : " + DATE_TIME_FORMAT, Emoji.fromUnicode("U+27A1"));
                menu.addOption(DISPLAY_TO_DATE, CHOICE_TO_DATE, "a future date to end the box, UTC expected format : " + DATE_TIME_FORMAT, Emoji.fromUnicode("U+2B05"));
                menu.addOption(CHOICE_LOW, CHOICE_LOW, "the low range price", Emoji.fromUnicode("U+2B06"));
                menu.addOption(CHOICE_HIGH, CHOICE_HIGH, "to high range price", Emoji.fromUnicode("U+2B07"));
            } else {
                menu.addOption(DISPLAY_FROM_DATE, CHOICE_FROM_DATE, "the date of first price, UTC expected format : " + DATE_TIME_FORMAT, Emoji.fromUnicode("U+1F4C5"));
                menu.addOption(DISPLAY_TO_DATE, CHOICE_TO_DATE, "the date of second price, UTC expected format : " + DATE_TIME_FORMAT, Emoji.fromUnicode("U+1F4C6"));
                menu.addOption(DISPLAY_FROM_PRICE, CHOICE_FROM_PRICE, "the first price", Emoji.fromUnicode("U+1F4C8"));
                menu.addOption(DISPLAY_TO_PRICE, CHOICE_TO_PRICE, "the second price", Emoji.fromUnicode("U+1F4C9"));
            }
            menu.addOption(CHOICE_MARGIN, CHOICE_MARGIN, "a new margin for the alert, in ticker2 unit (like USD for pair BTC/USD), 0 to disable", Emoji.fromUnicode("U+2195"));
            menu.addOption(CHOICE_REPEAT, CHOICE_REPEAT, "update the number of time the alert will be raise, 0 to disable", Emoji.fromUnicode("U+1F504"));
            menu.addOption(CHOICE_SNOOZE, CHOICE_SNOOZE, "update the delay to wait between two raises of the alert, in hours, 0 will set to default " + DEFAULT_SNOOZE_HOURS + " hours", Emoji.fromUnicode("U+1F910"));
        }
        menu.addOption(CHOICE_DELETE, CHOICE_DELETE, "delete this alert", Emoji.fromUnicode("U+1F5D1"));
        return menu.build();
    }

    static SelectOption enableOption(boolean hasRepeat) {
        return SelectOption.of(CHOICE_ENABLE, CHOICE_ENABLE).withDescription("enable this alert" + (hasRepeat ? "" : " (this will set repeat to " + DEFAULT_REPEAT + ")")).withEmoji(Emoji.fromUnicode("U+1F7E2"));
    }

    static SelectOption disableOption() {
        return SelectOption.of(CHOICE_DISABLE, CHOICE_DISABLE).withDescription("disable this alert").withEmoji(Emoji.fromUnicode("U+1F6D1"));
    }

    @NotNull
    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void onInteraction(@NotNull CommandContext context) {
        // this will open a Modal to get a new value from the user

        long alertId = requirePositive(context.args.getMandatoryLong(ALERT_ID_ARGUMENT));
        String field = context.args.getMandatoryString(SELECTION_ARGUMENT);
        context.noMoreArgs();

        int minLength = 1;
        int maxLength = 0;

        switch (field) {
            case CHOICE_EDIT:  // send a response that restore previous alert message, if needed
                context.reply(replyOriginal(null), 0);
                break;
            case CHOICE_DELETE:
                context.reply(Message.of(deleteModalOf(alertId)), 0); // confirmation modal
                break;
            case CHOICE_ENABLE, CHOICE_DISABLE: // directly performs the update
                new ModalEditInteraction().onInteraction(context.withArgumentsAndReplyMapper(alertId + " " + CHOICE_ENABLE + " " + (CHOICE_ENABLE.equals(field) ? "true" : "false"), Function.identity()));
                break;
            case CHOICE_MESSAGE:
                maxLength = ALERT_MESSAGE_ARG_MAX_LENGTH;
            case CHOICE_DATE, CHOICE_FROM_DATE, CHOICE_TO_DATE:
                if(maxLength == 0) {
                    minLength = 3; // 'now'
                    maxLength = DATE_TIME_FORMAT.length() + 64;
                }
            case CHOICE_LOW, CHOICE_HIGH, CHOICE_FROM_PRICE, CHOICE_TO_PRICE, CHOICE_MARGIN:
                if(maxLength == 0) {
                    maxLength = 20;
                }
            case CHOICE_REPEAT, CHOICE_SNOOZE:
                if(maxLength == 0) {
                    maxLength = 3;
                } // create a modal to get the value from the user
                context.reply(Message.of(updateModalOf(alertId, field, minLength, maxLength)), 0);
                break;
            default:
                throw new IllegalArgumentException("Invalid field to edit : " + field);
        }
    }
}
