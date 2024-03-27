package org.sbot.commands.interactions;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.utils.MarkdownUtil;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.Message;
import org.sbot.entities.Settings;
import org.sbot.entities.UserSettings;
import org.sbot.services.context.Context;
import org.sbot.utils.Dates;
import org.sbot.utils.DatesTest;

import java.awt.Color;
import java.util.List;
import java.util.Locale;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.sbot.commands.CommandAdapter.*;
import static org.sbot.commands.CommandAdapterTest.assertExceptionContains;
import static org.sbot.commands.UpdateCommand.ALERT_TITLE_PAIR_FOOTER;
import static org.sbot.commands.UpdateCommand.embedBuilder;
import static org.sbot.commands.UpdateCommand.*;
import static org.sbot.commands.context.CommandContext.TOO_MANY_ARGUMENTS;
import static org.sbot.commands.interactions.Interactions.INTERACTION_ID_SEPARATOR;
import static org.sbot.commands.interactions.Interactions.interactionId;
import static org.sbot.commands.interactions.ModalEditInteraction.CHOICE_DENIED;
import static org.sbot.commands.interactions.ModalEditInteraction.UPDATE_FAILED_FOOTER;
import static org.sbot.commands.interactions.SelectEditInteraction.CHOICE_DELETE;
import static org.sbot.commands.interactions.SelectEditInteraction.CHOICE_MIGRATE;
import static org.sbot.entities.ServerSettings.PRIVATE_SERVER;

class ModalEditInteractionTest {

    @Test
    void deniedModalOf() {
        var modal = ModalEditInteraction.deniedModalOf(123L);
        assertEquals(interactionId(ModalEditInteraction.NAME, 123L), modal.getId());
        assertEquals("Not allowed to edit alert #123", modal.getTitle());
        assertEquals(1, modal.getComponents().size());
        assertEquals("TextInput[SHORT](id=denied, value=null)", modal.getComponents().get(0).getComponents().get(0).toString());
    }

    @Test
    void notFoundModalOf() {
        var modal = ModalEditInteraction.notFoundModalOf(123L);
        assertEquals(interactionId(ModalEditInteraction.NAME, 123L), modal.getId());
        assertEquals("Unable to edit alert #123", modal.getTitle());
        assertEquals(1, modal.getComponents().size());
        assertEquals("TextInput[SHORT](id=notfound, value=null)", modal.getComponents().get(0).getComponents().get(0).toString());
    }

    @Test
    void deleteModalOf() {
        var modal = ModalEditInteraction.deleteModalOf(123L);
        assertEquals(interactionId(ModalEditInteraction.NAME, 123L), modal.getId());
        assertEquals("Delete alert #123", modal.getTitle());
        assertEquals(1, modal.getComponents().size());
        assertEquals("TextInput[SHORT](id=delete, value=null)", modal.getComponents().get(0).getComponents().get(0).toString());
    }

    @Test
    void updateModalOf() {
        var modal = ModalEditInteraction.updateModalOf(123L, "field", "hint", 1, 10);
        assertEquals(interactionId(ModalEditInteraction.NAME, 123L), modal.getId());
        assertEquals("Edit alert #123", modal.getTitle());
        assertEquals(1, modal.getComponents().size());
        assertEquals("TextInput[SHORT](id=field, value=null)", modal.getComponents().get(0).getComponents().get(0).toString());
    }

    @Test
    void name() {
        var interaction = new ModalEditInteraction();
        assertEquals(ModalEditInteraction.NAME, interaction.name());
    }

    @Test
    void onInteraction() {
        // test UPDATE_FAILED_FOOTER
        assertTrue(MarkdownUtil.codeblock("diff", "test").concat("\n")
                .endsWith(UPDATE_FAILED_FOOTER));

        MessageReceivedEvent messageReceivedEvent = mock(MessageReceivedEvent.class);
        when(messageReceivedEvent.getMessage()).thenReturn(mock());
        when(messageReceivedEvent.getAuthor()).thenReturn(mock());
        Context context = mock(Context.class);
        var settings = new Settings(UserSettings.NO_USER, PRIVATE_SERVER);

        var command = new ModalEditInteraction();
        assertThrows(NullPointerException.class, () -> command.onInteraction(null));

        var fc1 = spy(CommandContext.of(context, settings, messageReceivedEvent, ModalEditInteraction.NAME +   " invalidarg"));
        assertExceptionContains(IllegalArgumentException.class, ALERT_ID_ARGUMENT, () -> command.onInteraction(fc1));

        var fc2 = spy(CommandContext.of(context, settings, messageReceivedEvent, ModalEditInteraction.NAME +   " 123 delete value too"));
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS, () -> command.onInteraction(fc2));

        var fc3 = spy(CommandContext.of(context, settings, messageReceivedEvent, ModalEditInteraction.NAME +   " 123 invalidField"));
        assertExceptionContains(IllegalArgumentException.class, "Missing", () -> command.onInteraction(fc3));

        var commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, ModalEditInteraction.NAME + " 123 " + CHOICE_DELETE + " ok"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onInteraction(commandContext);
        ArgumentCaptor<List<Message>> messagesReply = ArgumentCaptor.forClass(List.class);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        var messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).embeds().isEmpty());
        assertNull(messages.get(0).modal());
        assertNull(messages.get(0).component());
        assertNotNull(messages.get(0).editMapper());

        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, ModalEditInteraction.NAME + " 123 " + CHOICE_DENIED + " ko"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onInteraction(commandContext);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).embeds().isEmpty());
        assertNull(messages.get(0).modal());
        assertNull(messages.get(0).component());
        assertNotNull(messages.get(0).editMapper());

        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, ModalEditInteraction.NAME + " 123 " + CHOICE_MIGRATE + " 0"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onInteraction(commandContext);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).embeds().isEmpty());
        assertNull(messages.get(0).modal());
        assertNull(messages.get(0).component());
        assertNotNull(messages.get(0).editMapper());

        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, ModalEditInteraction.NAME + " 123 " + CHOICE_DATE + " now"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onInteraction(commandContext);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).embeds().isEmpty());
        assertNull(messages.get(0).modal());
        assertNull(messages.get(0).component());
        assertNotNull(messages.get(0).editMapper());
    }

    @Test
    void replyOriginal() {
        String originalDescription = "an alert description";
        var description = "enabled" + UPDATE_ENABLED_HEADER + originalDescription;
        description = "disabled" + UPDATE_DISABLED_HEADER + description;
        description += ALERT_TIPS + "tips";

        var newEmbedBuilder = embedBuilder("test" + ALERT_TITLE_PAIR_FOOTER, Color.cyan, description);
        newEmbedBuilder.addField("f1", "v1", true);
        newEmbedBuilder.addField("f2", "v2", false);
        newEmbedBuilder.setFooter("footer");
        var messageEmbed = newEmbedBuilder.build();

        MessageEditBuilder editBuilder = new MessageEditBuilder().setEmbeds(List.of(messageEmbed));
        var mapper = ModalEditInteraction.replyOriginal("errorMessage").getFirst().editMapper();
        var editData = mapper.apply(editBuilder);
        assertEquals(1, editData.getEmbeds().size());
        var newEmbed = editData.getEmbeds().getFirst();
        assertEquals("test" + ALERT_TITLE_PAIR_FOOTER, newEmbed.getTitle());
        assertEquals(Color.cyan, newEmbed.getColor());
        assertEquals("errorMessage" + originalDescription, newEmbed.getDescription());
        assertEquals(2, newEmbed.getFields().size());
        assertEquals("f1", newEmbed.getFields().get(0).getName());
        assertEquals("v1", newEmbed.getFields().get(0).getValue());
        assertTrue(newEmbed.getFields().get(0).isInline());
        assertEquals("f2", newEmbed.getFields().get(1).getName());
        assertEquals("v2", newEmbed.getFields().get(1).getValue());
        assertFalse(newEmbed.getFields().get(1).isInline());
        assertEquals("footer", newEmbed.getFooter().getText());

        editBuilder = new MessageEditBuilder().setEmbeds(List.of(messageEmbed));
        mapper = ModalEditInteraction.replyOriginal(null).getFirst().editMapper();
        editData = mapper.apply(editBuilder);
        assertEquals(1, editData.getEmbeds().size());
        newEmbed = editData.getEmbeds().getFirst();
        assertEquals("test" + ALERT_TITLE_PAIR_FOOTER, newEmbed.getTitle());
        assertEquals(Color.cyan, newEmbed.getColor());
        assertEquals(originalDescription, newEmbed.getDescription());
    }

    @Test
    void updatedMessageEmbeds() {
        assertThrows(NullPointerException.class, () -> ModalEditInteraction.updatedMessageEmbeds(null, embedBuilder(""), "nm", true));
        assertThrows(NullPointerException.class, () -> ModalEditInteraction.updatedMessageEmbeds(embedBuilder("text").build(), null, "nm", true));
        String originalDescription = "an alert description";
        var description = "enabled" + UPDATE_ENABLED_HEADER + originalDescription;
        description = "disabled" + UPDATE_DISABLED_HEADER + description;
        description += ALERT_TIPS + "tips";

        var newEmbedBuilder = embedBuilder("test" + ALERT_TITLE_PAIR_FOOTER, Color.cyan, description);
        newEmbedBuilder.addField("f1", "v1", true);
        newEmbedBuilder.addField("f2", "v2", false);
        newEmbedBuilder.setFooter("footer");
        var messageEmbed = newEmbedBuilder.build();

        newEmbedBuilder = new EmbedBuilder().setColor(Color.green);
        ModalEditInteraction.updatedMessageEmbeds(messageEmbed, newEmbedBuilder, "new message", true);
        var newEmbed = newEmbedBuilder.build();
        assertEquals("test" + ALERT_TITLE_PAIR_FOOTER, newEmbed.getTitle());
        assertEquals(Color.cyan, newEmbed.getColor());
        assertEquals(originalDescription, newEmbed.getDescription());
        assertEquals(2, newEmbed.getFields().size());
        assertEquals("f1", newEmbed.getFields().get(0).getName());
        assertEquals("v1", newEmbed.getFields().get(0).getValue());
        assertTrue(newEmbed.getFields().get(0).isInline());
        assertEquals("f2", newEmbed.getFields().get(1).getName());
        assertEquals("v2", newEmbed.getFields().get(1).getValue());
        assertFalse(newEmbed.getFields().get(1).isInline());
        assertEquals("footer", newEmbed.getFooter().getText());

        newEmbedBuilder = new EmbedBuilder().setDescription("other desc").setColor(Color.green);
        ModalEditInteraction.updatedMessageEmbeds(messageEmbed, newEmbedBuilder, "new message", false);
        newEmbed = newEmbedBuilder.build();
        assertEquals("test" + ALERT_TITLE_PAIR_FOOTER + "new message", newEmbed.getTitle());
        assertEquals(Color.green, newEmbed.getColor());
        assertEquals("other desc", newEmbed.getDescription());
        assertEquals(0, newEmbed.getFields().size());
        assertEquals("footer", newEmbed.getFooter().getText());
    }

    @Test
    void asOriginal() {
        assertThrows(NullPointerException.class, () -> ModalEditInteraction.asOriginal(null, embedBuilder("")));
        assertThrows(NullPointerException.class, () -> ModalEditInteraction.asOriginal(embedBuilder("text").build(), null));
        String originalDescription = "an alert description";
        var description = "enabled" + UPDATE_ENABLED_HEADER + originalDescription;
        description = "disabled" + UPDATE_DISABLED_HEADER + description;
        description += ALERT_TIPS + "tips";

        var newEmbedBuilder = embedBuilder("title", Color.cyan, description);
        newEmbedBuilder.addField("f1", "v1", true);
        newEmbedBuilder.addField("f2", "v2", false);
        var messageEmbed = newEmbedBuilder.build();
        newEmbedBuilder = new EmbedBuilder();

        ModalEditInteraction.asOriginal(messageEmbed, newEmbedBuilder);
        var newEmbed = newEmbedBuilder.build();
        assertNull(newEmbed.getTitle());
        assertEquals(Color.cyan, newEmbed.getColor());
        assertEquals(originalDescription, newEmbed.getDescription());
        assertEquals(2, newEmbed.getFields().size());
        assertEquals("f1", newEmbed.getFields().get(0).getName());
        assertEquals("v1", newEmbed.getFields().get(0).getValue());
        assertTrue(newEmbed.getFields().get(0).isInline());
        assertEquals("f2", newEmbed.getFields().get(1).getName());
        assertEquals("v2", newEmbed.getFields().get(1).getValue());
        assertFalse(newEmbed.getFields().get(1).isInline());
    }

    @Test
    void removeStatusMessage() {
        String originalDescription = "an alert description";
        var description = new StringBuilder(UPDATE_ENABLED_HEADER + originalDescription);
        ModalEditInteraction.removeStatusMessage(description);
        assertEquals(originalDescription, description.toString());
        description = new StringBuilder(UPDATE_DISABLED_HEADER + originalDescription);
        ModalEditInteraction.removeStatusMessage(description);
        assertEquals(originalDescription, description.toString());
    }

    @Test
    void originalDescription() {
        String originalDescription = "an alert description";
        var description = "update failed" + UPDATE_FAILED_FOOTER + originalDescription;
        description = "updated !" + UPDATE_SUCCESS_FOOTER_NO_AUTHOR + description;
        description = UPDATE_ENABLED_HEADER + description;
        description = " " + UPDATE_DISABLED_HEADER + description;
        description += ALERT_TIPS + "tips";
        assertEquals(originalDescription, ModalEditInteraction.originalDescription(description));
    }

    @Test
    void arguments() {
        ModalInteractionEvent modalInteractionEvent = mock(ModalInteractionEvent.class);
        when(modalInteractionEvent.getMessage()).thenReturn(mock());
        when(modalInteractionEvent.getUser()).thenReturn(mock());
        Context context = mock(Context.class);

        CommandContext[] commandContext = new CommandContext[1];

        Settings user = new Settings(UserSettings.ofDiscordUser(123L, Locale.JAPAN, Dates.UTC, DatesTest.nowUtc()), PRIVATE_SERVER);

        when(modalInteractionEvent.getModalId()).thenReturn("");
        assertExceptionContains(IllegalArgumentException.class, "interactionId",
                () -> CommandContext.of(context, user, modalInteractionEvent));

        when(modalInteractionEvent.getModalId()).thenReturn("123");
        assertExceptionContains(IllegalArgumentException.class, "interactionId",
                () -> CommandContext.of(context, user, modalInteractionEvent));

        when(modalInteractionEvent.getModalId()).thenReturn(" " + INTERACTION_ID_SEPARATOR + " ");
        assertExceptionContains(IllegalArgumentException.class, "alertId",
                () -> CommandContext.of(context, user, modalInteractionEvent));

        when(modalInteractionEvent.getModalId()).thenReturn("123" + INTERACTION_ID_SEPARATOR + " ");
        assertExceptionContains(IllegalArgumentException.class, "alertId",
                () -> CommandContext.of(context, user, modalInteractionEvent));

        when(modalInteractionEvent.getModalId()).thenReturn("123" + INTERACTION_ID_SEPARATOR + "321");
        when(modalInteractionEvent.getValues()).thenReturn(emptyList());
        commandContext[0] = CommandContext.of(context, user, modalInteractionEvent);
        assertEquals("123", commandContext[0].name);
        assertEquals("321", commandContext[0].args.getLastArgs("").orElse(null));
        assertExceptionContains(IllegalArgumentException.class, SELECTION_ARGUMENT,
                () -> ModalEditInteraction.arguments(commandContext[0]));

        ModalMapping mm = mock();
        when(mm.getId()).thenReturn(CHOICE_DELETE);
        when(mm.getAsString()).thenReturn("ok");

        ModalMapping mm2 = mock();
        when(mm2.getId()).thenReturn("2");
        when(mm2.getAsString()).thenReturn("2");

        when(modalInteractionEvent.getValues()).thenReturn(List.of(mm, mm2));
        commandContext[0] = CommandContext.of(context, user, modalInteractionEvent);
        assertEquals("321 delete ok 2 2", commandContext[0].args.getLastArgs("").orElse(null));
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ModalEditInteraction.arguments(commandContext[0]));

        when(mm.getId()).thenReturn("field");
        when(mm.getAsString()).thenReturn("value");
        when(modalInteractionEvent.getValues()).thenReturn(List.of(mm));
        commandContext[0] = CommandContext.of(context, user, modalInteractionEvent);
        assertEquals("321 field value", commandContext[0].args.getLastArgs("").orElse(null));
        var arguments = ModalEditInteraction.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertEquals(321L, arguments.alertId());
        assertEquals("field", arguments.field());
        assertEquals("value", arguments.value());

        when(mm.getId()).thenReturn(CHOICE_DELETE);
        when(mm.getAsString()).thenReturn("ok");
        when(modalInteractionEvent.getValues()).thenReturn(List.of(mm));
        commandContext[0] = CommandContext.of(context, user, modalInteractionEvent);
        assertEquals("321 delete ok", commandContext[0].args.getLastArgs("").orElse(null));
        arguments = ModalEditInteraction.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertEquals(321L, arguments.alertId());
        assertEquals(CHOICE_DELETE, arguments.field());
        assertEquals("ok", arguments.value());
    }
}