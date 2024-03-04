package org.sbot.commands.interactions;

import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import org.junit.jupiter.api.Test;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.User;
import org.sbot.services.context.Context;
import org.sbot.utils.Dates;
import org.sbot.utils.DatesTest;

import java.util.List;
import java.util.Locale;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sbot.commands.CommandAdapter.SELECTION_ARGUMENT;
import static org.sbot.commands.CommandAdapterTest.assertExceptionContains;
import static org.sbot.commands.Commands.INTERACTION_ID_SEPARATOR;
import static org.sbot.commands.UpdateCommand.*;
import static org.sbot.commands.context.CommandContext.TOO_MANY_ARGUMENTS;
import static org.sbot.commands.interactions.SelectEditInteraction.*;
import static org.sbot.entities.alerts.Alert.Type.*;
import static org.sbot.entities.alerts.AlertTest.createTestAlertWithType;

class SelectEditInteractionTest {

    @Test
    void updateMenuOf() {
        // remainder, disabled
        var alert = createTestAlertWithType(remainder).withListeningDateRepeat(null, (short) 0);
        assertFalse(alert.isEnabled());
        var menu = SelectEditInteraction.updateMenuOf(alert);
        assertNotNull(menu);
        assertEquals(NAME + INTERACTION_ID_SEPARATOR + alert.id, menu.getId());
        assertEquals(7, menu.getOptions().size());
        assertEquals(CHOICE_EDIT, menu.getOptions().get(0).getLabel());
        assertTrue(menu.getOptions().get(0).isDefault());
        assertEquals(CHOICE_DATE, menu.getOptions().get(1).getLabel());
        assertEquals(CHOICE_MESSAGE, menu.getOptions().get(2).getLabel());
        assertEquals(CHOICE_REPEAT, menu.getOptions().get(3).getLabel());
        assertEquals(CHOICE_SNOOZE, menu.getOptions().get(4).getLabel());
        assertEquals(CHOICE_MIGRATE, menu.getOptions().get(5).getLabel());
        assertEquals(CHOICE_DELETE, menu.getOptions().get(6).getLabel());

        // remainder, enabled
        alert = alert.withListeningDateRepeat(DatesTest.nowUtc(), (short) 0);
        assertTrue(alert.isEnabled());
        menu = SelectEditInteraction.updateMenuOf(alert);
        assertNotNull(menu);
        assertEquals(NAME + INTERACTION_ID_SEPARATOR + alert.id, menu.getId());
        assertEquals(7, menu.getOptions().size());

        // range, disabled
        alert = createTestAlertWithType(range).withListeningDateRepeat(null, (short) 0);
        assertFalse(alert.isEnabled());
        menu = SelectEditInteraction.updateMenuOf(alert);
        assertNotNull(menu);
        assertEquals(NAME + INTERACTION_ID_SEPARATOR + alert.id, menu.getId());
        assertEquals(12, menu.getOptions().size());
        assertEquals(CHOICE_EDIT, menu.getOptions().get(0).getLabel());
        assertTrue(menu.getOptions().get(0).isDefault());
        assertEquals(CHOICE_ENABLE, menu.getOptions().get(1).getLabel());
        assertEquals(CHOICE_MESSAGE, menu.getOptions().get(2).getLabel());
        assertEquals(DISPLAY_FROM_DATE, menu.getOptions().get(3).getLabel());
        assertEquals(DISPLAY_TO_DATE, menu.getOptions().get(4).getLabel());
        assertEquals(CHOICE_LOW, menu.getOptions().get(5).getLabel());
        assertEquals(CHOICE_HIGH, menu.getOptions().get(6).getLabel());
        assertEquals(CHOICE_MARGIN, menu.getOptions().get(7).getLabel());
        assertEquals(CHOICE_REPEAT, menu.getOptions().get(8).getLabel());
        assertEquals(CHOICE_SNOOZE, menu.getOptions().get(9).getLabel());
        assertEquals(CHOICE_MIGRATE, menu.getOptions().get(10).getLabel());
        assertEquals(CHOICE_DELETE, menu.getOptions().get(11).getLabel());

        // range, enabled
        alert = alert.withListeningDateRepeat(DatesTest.nowUtc(), (short) 0);
        assertTrue(alert.isEnabled());
        menu = SelectEditInteraction.updateMenuOf(alert);
        assertNotNull(menu);
        assertEquals(NAME + INTERACTION_ID_SEPARATOR + alert.id, menu.getId());
        assertEquals(12, menu.getOptions().size());
        assertEquals(CHOICE_EDIT, menu.getOptions().get(0).getLabel());
        assertTrue(menu.getOptions().get(0).isDefault());
        assertEquals(CHOICE_DISABLE, menu.getOptions().get(1).getLabel());
        assertEquals(CHOICE_MESSAGE, menu.getOptions().get(2).getLabel());
        assertEquals(DISPLAY_FROM_DATE, menu.getOptions().get(3).getLabel());
        assertEquals(DISPLAY_TO_DATE, menu.getOptions().get(4).getLabel());
        assertEquals(CHOICE_LOW, menu.getOptions().get(5).getLabel());
        assertEquals(CHOICE_HIGH, menu.getOptions().get(6).getLabel());
        assertEquals(CHOICE_MARGIN, menu.getOptions().get(7).getLabel());
        assertEquals(CHOICE_REPEAT, menu.getOptions().get(8).getLabel());
        assertEquals(CHOICE_SNOOZE, menu.getOptions().get(9).getLabel());
        assertEquals(CHOICE_MIGRATE, menu.getOptions().get(10).getLabel());
        assertEquals(CHOICE_DELETE, menu.getOptions().get(11).getLabel());

        // trend, disabled
        alert = createTestAlertWithType(trend).withListeningDateRepeat(null, (short) 0);
        assertFalse(alert.isEnabled());
        menu = SelectEditInteraction.updateMenuOf(alert);
        assertNotNull(menu);
        assertEquals(NAME + INTERACTION_ID_SEPARATOR + alert.id, menu.getId());
        assertEquals(12, menu.getOptions().size());
        assertEquals(CHOICE_EDIT, menu.getOptions().get(0).getLabel());
        assertTrue(menu.getOptions().get(0).isDefault());
        assertEquals(CHOICE_ENABLE, menu.getOptions().get(1).getLabel());
        assertEquals(CHOICE_MESSAGE, menu.getOptions().get(2).getLabel());
        assertEquals(DISPLAY_FROM_DATE, menu.getOptions().get(3).getLabel());
        assertEquals(DISPLAY_TO_DATE, menu.getOptions().get(4).getLabel());
        assertEquals(DISPLAY_FROM_PRICE, menu.getOptions().get(5).getLabel());
        assertEquals(DISPLAY_TO_PRICE, menu.getOptions().get(6).getLabel());
        assertEquals(CHOICE_MARGIN, menu.getOptions().get(7).getLabel());
        assertEquals(CHOICE_REPEAT, menu.getOptions().get(8).getLabel());
        assertEquals(CHOICE_SNOOZE, menu.getOptions().get(9).getLabel());
        assertEquals(CHOICE_MIGRATE, menu.getOptions().get(10).getLabel());
        assertEquals(CHOICE_DELETE, menu.getOptions().get(11).getLabel());

        // trend, enabled
        alert = alert.withListeningDateRepeat(DatesTest.nowUtc(), (short) 0);
        assertTrue(alert.isEnabled());
        menu = SelectEditInteraction.updateMenuOf(alert);
        assertNotNull(menu);
        assertEquals(NAME + INTERACTION_ID_SEPARATOR + alert.id, menu.getId());
        assertEquals(12, menu.getOptions().size());
        assertEquals(CHOICE_EDIT, menu.getOptions().get(0).getLabel());
        assertTrue(menu.getOptions().get(0).isDefault());
        assertEquals(CHOICE_DISABLE, menu.getOptions().get(1).getLabel());
        assertEquals(CHOICE_MESSAGE, menu.getOptions().get(2).getLabel());
        assertEquals(DISPLAY_FROM_DATE, menu.getOptions().get(3).getLabel());
        assertEquals(DISPLAY_TO_DATE, menu.getOptions().get(4).getLabel());
        assertEquals(DISPLAY_FROM_PRICE, menu.getOptions().get(5).getLabel());
        assertEquals(DISPLAY_TO_PRICE, menu.getOptions().get(6).getLabel());
        assertEquals(CHOICE_MARGIN, menu.getOptions().get(7).getLabel());
        assertEquals(CHOICE_REPEAT, menu.getOptions().get(8).getLabel());
        assertEquals(CHOICE_SNOOZE, menu.getOptions().get(9).getLabel());
        assertEquals(CHOICE_MIGRATE, menu.getOptions().get(10).getLabel());
        assertEquals(CHOICE_DELETE, menu.getOptions().get(11).getLabel());
    }

    @Test
    void selectMenu() {
        var menu = SelectEditInteraction.selectMenu("menuId");
        assertNotNull(menu);
        assertEquals("menuId", menu.getId());
        assertEquals(1, menu.getOptions().size());
        assertEquals(CHOICE_EDIT, menu.getOptions().get(0).getLabel());
        assertTrue(menu.getOptions().get(0).isDefault());
    }

    @Test
    void enableOption() {
        var option = SelectEditInteraction.enableOption(true);
        assertNotNull(option);
        assertEquals(CHOICE_ENABLE, option.getLabel());
        assertTrue(option.getDescription().contains("repeat"));
        option = SelectEditInteraction.enableOption(false);
        assertNotNull(option);
        assertEquals(CHOICE_ENABLE, option.getLabel());
        assertFalse(option.getDescription().contains("repeat"));
    }

    @Test
    void disableOption() {
        var option = SelectEditInteraction.disableOption();
        assertNotNull(option);
        assertTrue(option.getLabel().equals(CHOICE_DISABLE));
    }

    @Test
    void name() {
        var interaction = new SelectEditInteraction();
        assertEquals(SelectEditInteraction.NAME, interaction.name());
    }

    @Test
    void onInteraction() {
    }

    @Test
    void arguments() {
        StringSelectInteractionEvent stringSelectInteractionEvent = mock(StringSelectInteractionEvent.class);
        when(stringSelectInteractionEvent.getMessage()).thenReturn(mock());
        when(stringSelectInteractionEvent.getUser()).thenReturn(mock());
        Context context = mock(Context.class);

        CommandContext[] commandContext = new CommandContext[1];

        User user = new User(123L, Locale.JAPAN, Dates.UTC, DatesTest.nowUtc());

        when(stringSelectInteractionEvent.getComponentId()).thenReturn("");
        assertExceptionContains(IllegalArgumentException.class, "componentId",
                () -> CommandContext.of(context, user, stringSelectInteractionEvent));

        when(stringSelectInteractionEvent.getComponentId()).thenReturn("123");
        assertExceptionContains(IllegalArgumentException.class, "componentId",
                () -> CommandContext.of(context, user, stringSelectInteractionEvent));

        when(stringSelectInteractionEvent.getComponentId()).thenReturn(" " + INTERACTION_ID_SEPARATOR + " ");
        assertExceptionContains(IllegalArgumentException.class, "name",
                () -> CommandContext.of(context, user, stringSelectInteractionEvent));

        when(stringSelectInteractionEvent.getComponentId()).thenReturn("123" + INTERACTION_ID_SEPARATOR + " ");
        assertExceptionContains(IllegalArgumentException.class, "alertId",
                () -> CommandContext.of(context, user, stringSelectInteractionEvent));

        when(stringSelectInteractionEvent.getComponentId()).thenReturn("123" + INTERACTION_ID_SEPARATOR + "321");
        when(stringSelectInteractionEvent.getValues()).thenReturn(emptyList());
        commandContext[0] = CommandContext.of(context, user, stringSelectInteractionEvent);
        assertEquals("123", commandContext[0].name);
        assertEquals("321", commandContext[0].args.getLastArgs("").orElse(null));
        assertExceptionContains(IllegalArgumentException.class, SELECTION_ARGUMENT,
                () -> SelectEditInteraction.arguments(commandContext[0]));

        when(stringSelectInteractionEvent.getValues()).thenReturn(List.of("1", "2"));
        commandContext[0] = CommandContext.of(context, user, stringSelectInteractionEvent);
        assertEquals("321 1 2", commandContext[0].args.getLastArgs("").orElse(null));
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> SelectEditInteraction.arguments(commandContext[0]));

        when(stringSelectInteractionEvent.getValues()).thenReturn(List.of("field"));
        commandContext[0] = CommandContext.of(context, user, stringSelectInteractionEvent);
        assertEquals("321 field", commandContext[0].args.getLastArgs("").orElse(null));
        var arguments = SelectEditInteraction.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertEquals(321L, arguments.alertId());
        assertEquals("field", arguments.field());
    }
}