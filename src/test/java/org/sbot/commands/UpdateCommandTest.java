package org.sbot.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.junit.jupiter.api.Test;
import org.sbot.commands.context.CommandContext;
import org.sbot.services.context.Context;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sbot.commands.CommandAdapter.ALERT_ID_ARGUMENT;
import static org.sbot.commands.CommandAdapter.SELECTION_ARGUMENT;
import static org.sbot.commands.CommandAdapterTest.assertExceptionContains;
import static org.sbot.commands.UpdateCommand.*;
import static org.sbot.commands.context.CommandContext.TOO_MANY_ARGUMENTS;

class UpdateCommandTest {

    @Test
    void arguments() {
        MessageReceivedEvent messageReceivedEvent = mock(MessageReceivedEvent.class);
        when(messageReceivedEvent.getMessage()).thenReturn(mock());
        when(messageReceivedEvent.getAuthor()).thenReturn(mock());
        Context context = mock(Context.class);

        CommandContext[] commandContext = new CommandContext[1];

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME);
        assertExceptionContains(IllegalArgumentException.class, SELECTION_ARGUMENT,
                () -> UpdateCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + "  badSelection");
        assertExceptionContains(IllegalArgumentException.class, ALERT_ID_ARGUMENT,
                () -> UpdateCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + "  locale locale err");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> UpdateCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + "  timezone timezone err");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> UpdateCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + "  message");
        assertExceptionContains(IllegalArgumentException.class, ALERT_ID_ARGUMENT,
                () -> UpdateCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + "  message err");
        assertExceptionContains(IllegalArgumentException.class, ALERT_ID_ARGUMENT,
                () -> UpdateCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + "  message  value 123");
        assertExceptionContains(IllegalArgumentException.class, ALERT_ID_ARGUMENT,
                () -> UpdateCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + "  locale locale");
        var arguments = UpdateCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertEquals(CHOICE_LOCALE, arguments.selection());
        assertEquals("locale", arguments.value());
        assertNull(arguments.alertId());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + "  timezone timezone");
        arguments = UpdateCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertEquals(CHOICE_TIMEZONE, arguments.selection());
        assertEquals("timezone", arguments.value());
        assertNull(arguments.alertId());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + "  message 123 message");
        arguments = UpdateCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertEquals(CHOICE_MESSAGE, arguments.selection());
        assertNull(arguments.value());
        assertEquals(123L, arguments.alertId());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + "  from_date 123 message");
        arguments = UpdateCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertEquals(CHOICE_FROM_DATE, arguments.selection());
        assertNull(arguments.value());
        assertEquals(123L, arguments.alertId());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + "  repeat 123 message");
        arguments = UpdateCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertEquals(CHOICE_REPEAT, arguments.selection());
        assertNull(arguments.value());
        assertEquals(123L, arguments.alertId());
    }

    @Test
    void onCommandLocale() {
    }

    @Test
    void onCommandTimezone() {
    }

    @Test
    void onCommandFromPrice() {
    }

    @Test
    void onCommandToPrice() {
    }

    @Test
    void onCommandFromDate() {
    }

    @Test
    void onCommandToDate() {
    }

    @Test
    void onCommandMargin() {
    }

    @Test
    void onCommandRepease() {
    }

    @Test
    void onCommandSnooze() {
    }

    @Test
    void onCommandEnable() {
    }

    @Test
    void listeningDate() {
    }
}