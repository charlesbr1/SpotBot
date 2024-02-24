package org.sbot.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.junit.jupiter.api.Test;
import org.sbot.commands.context.CommandContext;
import org.sbot.services.context.Context;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sbot.commands.CommandAdapter.*;
import static org.sbot.commands.CommandAdapterTest.assertExceptionContains;
import static org.sbot.commands.DeleteCommand.DELETE_ALL;
import static org.sbot.commands.context.CommandContext.TOO_MANY_ARGUMENTS;

class DeleteCommandTest {

    @Test
    void onCommand() {
    }

    @Test
    void arguments() {
        MessageReceivedEvent messageReceivedEvent = mock(MessageReceivedEvent.class);
        when(messageReceivedEvent.getMessage()).thenReturn(mock());
        when(messageReceivedEvent.getAuthor()).thenReturn(mock());
        Context context = mock(Context.class);

        CommandContext[] commandContext = new CommandContext[1];

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, DeleteCommand.NAME);
        assertExceptionContains(IllegalArgumentException.class, EXCHANGE_ARGUMENT,
                () -> QuoteCommand.arguments(commandContext[0]));

        // id command
        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, DeleteCommand.NAME + "  123 az");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> DeleteCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, DeleteCommand.NAME + "  123 45");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> DeleteCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, DeleteCommand.NAME + "  123");
        var arguments = DeleteCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertEquals(123L, arguments.alertId());
        assertNull(arguments.tickerOrPair());
        assertNull(arguments.ownerId());

        // filter command
        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, DeleteCommand.NAME + "  az");
        assertExceptionContains(IllegalArgumentException.class, "ticker",
                () -> DeleteCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, DeleteCommand.NAME + "  azef efefe");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> DeleteCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, DeleteCommand.NAME + "  azefefefel/lklk");
        assertExceptionContains(IllegalArgumentException.class, PAIR_ARGUMENT,
                () -> DeleteCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, DeleteCommand.NAME + "  btc/lk");
        assertExceptionContains(IllegalArgumentException.class, PAIR_ARGUMENT,
                () -> DeleteCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, DeleteCommand.NAME + "  ETH/USD <@321> az");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> DeleteCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, DeleteCommand.NAME + " eth/usd <@32>  " );
        arguments = DeleteCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals("eth/usd", arguments.tickerOrPair());
        assertEquals(32L, arguments.ownerId());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, DeleteCommand.NAME + " dot/usd   " );
        arguments = DeleteCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals("dot/usd", arguments.tickerOrPair());
        assertNull(arguments.ownerId());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, DeleteCommand.NAME + " all   " );
        arguments = DeleteCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals(DELETE_ALL, arguments.tickerOrPair());
        assertNull(arguments.ownerId());
    }
}