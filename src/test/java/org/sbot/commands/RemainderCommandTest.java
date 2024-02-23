package org.sbot.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.Message;
import org.sbot.services.context.Context;
import org.sbot.services.context.Context.DataServices;
import org.sbot.services.dao.AlertsDao;
import org.sbot.services.dao.UsersDao;
import org.sbot.utils.Dates;
import org.sbot.utils.DatesTest;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.sbot.entities.User.DEFAULT_LOCALE;
import static org.sbot.utils.Dates.UTC;

class RemainderCommandTest {

    @Test
    void onCommand() {
        MessageReceivedEvent messageReceivedEvent = mock(MessageReceivedEvent.class);
        when(messageReceivedEvent.getMessage()).thenReturn(mock());
        when(messageReceivedEvent.getAuthor()).thenReturn(mock());
        ZonedDateTime now = DatesTest.nowUtc().truncatedTo(ChronoUnit.MINUTES);
        String date = Dates.formatUTC(DEFAULT_LOCALE, now.plusHours(3L).plusDays(2L));
        Context context = mock(Context.class);
        when(context.clock()).thenReturn(Clock.fixed(now.toInstant(), UTC));
        UsersDao usersDao = mock();
        AlertsDao alertsDao = mock();
        DataServices dataServices = mock();
        when(dataServices.usersDao()).thenReturn(v -> usersDao);
        when(dataServices.alertsDao()).thenReturn(v -> alertsDao);
        when(context.dataServices()).thenReturn(dataServices);


        var command = new RemainderCommand();

        assertThrows(NullPointerException.class, () -> command.onCommand(null));

        CommandContext[] commandContext = new CommandContext[1];
        commandContext[0] = spy(CommandContext.of(context, null, messageReceivedEvent, RemainderCommand.NAME + " eth/usd   a  message fe fe  " + date));
        doNothing().when(commandContext[0]).reply(anyList(), anyInt());
        command.onCommand(commandContext[0]);

        verify(usersDao).userExists(0);
        verify(alertsDao, never()).addAlert(any());
        ArgumentCaptor<List<Message>> messagesReply = ArgumentCaptor.forClass(List.class);
        verify(commandContext[0]).reply(messagesReply.capture(), anyInt());
        List<Message> messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("Missing user account setup"));

        commandContext[0] = spy(CommandContext.of(context, null, messageReceivedEvent, RemainderCommand.NAME + " eth/usd   a  message fe fe  " + date));
        doNothing().when(commandContext[0]).reply(anyList(), eq(command.responseTtlSeconds));
        when(usersDao.userExists(0)).thenReturn(true);
        command.onCommand(commandContext[0]);

        verify(usersDao, times(2)).userExists(0);
        verify(alertsDao).addAlert(any());
        messagesReply = ArgumentCaptor.forClass(List.class);
        verify(commandContext[0]).reply(messagesReply.capture(), eq(command.responseTtlSeconds));
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertFalse(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("Missing user account setup"));
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().toUpperCase()
                .contains("Remainder".toUpperCase()));
    }

    @Test
    void arguments() {
        MessageReceivedEvent messageReceivedEvent = mock(MessageReceivedEvent.class);
        when(messageReceivedEvent.getMessage()).thenReturn(mock());
        when(messageReceivedEvent.getAuthor()).thenReturn(mock());
        ZonedDateTime now = DatesTest.nowUtc().truncatedTo(ChronoUnit.MINUTES);
        Context context = mock(Context.class);

        assertExceptionContains(IllegalArgumentException.class, "Missing command",
                () -> CommandContext.of(context, null, messageReceivedEvent, ""));

        CommandContext[] commandContext = new CommandContext[1];

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, RemainderCommand.NAME);
        assertExceptionContains(IllegalArgumentException.class, "pair",
                () -> RemainderCommand.arguments(commandContext[0], now));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, RemainderCommand.NAME + "  a message fe fe fe");
        assertExceptionContains(IllegalArgumentException.class, "pair",
                () -> RemainderCommand.arguments(commandContext[0], now));

        String date = Dates.formatUTC(commandContext[0].locale, now.plusHours(3L).plusDays(2L));
        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, RemainderCommand.NAME + " " + date);
        assertExceptionContains(IllegalArgumentException.class, "pair",
                () -> RemainderCommand.arguments(commandContext[0], now));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, RemainderCommand.NAME + " eth/usd");
        assertExceptionContains(IllegalArgumentException.class, "date",
                () -> RemainderCommand.arguments(commandContext[0], now));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, RemainderCommand.NAME + " eth/usd a message fe fe fe");
        assertExceptionContains(IllegalArgumentException.class, "date",
                () -> RemainderCommand.arguments(commandContext[0], now));

        when(context.clock()).thenReturn(Clock.fixed(now.toInstant(), UTC));
        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, RemainderCommand.NAME + " eth/usd " + date);
        assertExceptionContains(IllegalArgumentException.class, "message",
                () -> RemainderCommand.arguments(commandContext[0], now));

        when(context.clock()).thenReturn(Clock.fixed(now.toInstant(), UTC));
        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, RemainderCommand.NAME + " eth/usd " + date + " to");
        assertExceptionContains(IllegalArgumentException.class, "date",
                () -> RemainderCommand.arguments(commandContext[0], now));

        when(context.clock()).thenReturn(Clock.fixed(now.toInstant(), UTC));
        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, RemainderCommand.NAME + " eth/usd   a  message fe fe  " + date + " too mu");
        assertExceptionContains(IllegalArgumentException.class, "date",
                () -> RemainderCommand.arguments(commandContext[0], now));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, RemainderCommand.NAME + " eth/usd   a  message fe fe  " + date);
        var arguments = RemainderCommand.arguments(commandContext[0], now);
        assertNotNull(arguments);
        assertEquals("ETH/USD", arguments.pair());
        assertEquals(now.plusHours(3L).plusDays(2L), arguments.date());
        assertEquals("a  message fe fe", arguments.message());
    }

    static void assertExceptionContains(@NotNull Class<? extends  Exception> type, @NotNull String message, @NotNull Runnable runnable) {
        try {
            runnable.run();
            fail("Expected exception " + type.getName() + " to be thrown with message : " + message);
        } catch (Exception e) {
            if(type.isAssignableFrom(e.getClass())) {
                assertTrue(e.getMessage().contains(message), "Exception " + type.getName() + " thrown, expected message : " + message + ", actual : " + e.getMessage());
            } else {
                fail("Expected exception " + type.getName() + " to be thrown with message : " + message + ", but got exception " + e);
            }
        }
    }
}