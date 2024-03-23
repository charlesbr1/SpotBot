package org.sbot.commands;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.sbot.commands.context.CommandContext;
import org.sbot.commands.reader.ArgumentReader;
import org.sbot.commands.reader.SlashArgumentReader;

import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sbot.commands.AlertCommand.TYPE_ARGUMENT;
import static org.sbot.entities.alerts.Alert.Type.*;
import static org.sbot.entities.alerts.AlertTest.TEST_USER_ID;

class AlertCommandTest {

    @Test
    void onCommand() {
        // check the command is dispatched between Range/Trend/RemainderCommand
        var alertCommand = new AlertCommand();
        assertThrows(NullPointerException.class, () -> alertCommand.onCommand(null));
        var context = mock(CommandContext.class);

        // test string command
        var argumentReader = mock(ArgumentReader.class);
        try {
            var field = CommandContext.class.getField("args");
            field.setAccessible(true);
            field.set(context, argumentReader);
            field = CommandContext.class.getField("userId");
            field.setAccessible(true);
            field.set(context, TEST_USER_ID);
            when(argumentReader.getMandatoryType(TYPE_ARGUMENT)).thenReturn(range);
            alertCommand.onCommand(context);
            Assertions.fail();
        } catch (Exception e) {
            assertTrue(Stream.of(e.getStackTrace()).map(Object::toString).collect(joining()).contains("RangeCommand"));
        }

        try {
            when(argumentReader.getMandatoryType(TYPE_ARGUMENT)).thenReturn(trend);
            alertCommand.onCommand(context);
            Assertions.fail();
        } catch (Exception e) {
            assertTrue(Stream.of(e.getStackTrace()).map(Object::toString).collect(joining()).contains("TrendCommand"));
        }

        try {
            when(argumentReader.getMandatoryType(TYPE_ARGUMENT)).thenReturn(remainder);
            alertCommand.onCommand(context);
            Assertions.fail();
        } catch (Exception e) {
            assertTrue(Stream.of(e.getStackTrace()).map(Object::toString).collect(joining()).contains("RemainderCommand"));
        }

        // test slash command
        var slashReader = mock(SlashArgumentReader.class);
        try {
            var field = CommandContext.class.getField("args");
            field.setAccessible(true);
            field.set(context, slashReader);
            when(slashReader.getSubcommandName()).thenReturn(RangeCommand.NAME);
            alertCommand.onCommand(context);
            Assertions.fail();
        } catch (Exception e) {
            assertTrue(Stream.of(e.getStackTrace()).map(Object::toString).collect(joining()).contains("RangeCommand"));
        }

        try {
            when(slashReader.getSubcommandName()).thenReturn(TrendCommand.NAME);
            alertCommand.onCommand(context);
            Assertions.fail();
        } catch (Exception e) {
            assertTrue(Stream.of(e.getStackTrace()).map(Object::toString).collect(joining()).contains("TrendCommand"));
        }

        try {
            when(slashReader.getSubcommandName()).thenReturn(RemainderCommand.NAME);
            alertCommand.onCommand(context);
            Assertions.fail();
        } catch (Exception e) {
            assertTrue(Stream.of(e.getStackTrace()).map(Object::toString).collect(joining()).contains("RemainderCommand"));
        }
    }
}