package org.sbot.services.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.requests.restaction.CacheRestAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateRequest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.sbot.commands.Commands;
import org.sbot.commands.interactions.Interactions;
import org.sbot.entities.Message;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static net.dv8tion.jda.api.requests.ErrorResponse.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.sbot.commands.CommandAdapter.embedBuilder;
import static org.sbot.entities.ServerSettings.DEFAULT_BOT_CHANNEL;
import static org.sbot.entities.ServerSettings.DEFAULT_BOT_ROLE;

class DiscordTest {

    private static void setJDA(@NotNull Discord discord, @NotNull JDA jda) {
        try {
            var field = discord.getClass().getDeclaredField("jda");
            field.setAccessible(true);
            field.set(discord, jda);
            field = discord.getClass().getDeclaredField("commands");
            field.setAccessible(true);
            field.set(discord, new ConcurrentHashMap<>());
            field = discord.getClass().getDeclaredField("interactions");
            field.setAccessible(true);
            field.set(discord, new ConcurrentHashMap<>());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void errorHandler() {
        AtomicBoolean onSuccess = new AtomicBoolean(false);
        AtomicReference<Boolean> onFailure = new AtomicReference<>(null);
        var errorHandler = Discord.errorHandler(() -> onSuccess.set(true), onFailure::set);

        errorHandler.accept(new Exception());
        assertEquals(false, onFailure.get());
        assertFalse(onSuccess.get());

        ErrorResponseException error = mock();
        for(var err : List.of(UNKNOWN_USER, UNKNOWN_GUILD, UNKNOWN_CHANNEL, NO_USER_WITH_TAG_EXISTS, REQUEST_ENTITY_TOO_LARGE, EMPTY_MESSAGE, MESSAGE_BLOCKED_BY_HARMFUL_LINK_FILTER, MESSAGE_BLOCKED_BY_AUTOMOD, TITLE_BLOCKED_BY_AUTOMOD)) {
            when(error.getErrorResponse()).thenReturn(err);
            onSuccess.set(false);
            onFailure.set(null);
            errorHandler.accept(error);
            assertNull(onFailure.get());
            assertTrue(onSuccess.get());
        }

        when(error.getErrorResponse()).thenReturn(CANNOT_SEND_TO_USER);
        onSuccess.set(false);
        onFailure.set(null);
        errorHandler.accept(error);
        assertEquals(true, onFailure.get());
        assertFalse(onSuccess.get());
    }

    @Test
    void asMessageRequests() {
        MessageCreateRequest<?> request = mock();
        Function<List<MessageEmbed>, MessageCreateRequest<?>> mapper = m -> request;
        Message message = Message.of(List.of(embedBuilder("test")), List.of("role"), List.of("user"));
        when(request.getMentionedRoles()).thenReturn(Set.of("roles"));
        when(request.getMentionedUsers()).thenReturn(Set.of("users"));
        var results = Discord.asMessageRequests(message, mapper).toList();
        assertEquals(1, results.size());
        assertEquals(request, results.getFirst());
        verify(request).mentionRoles(List.of("role"));
        verify(request).mentionUsers(List.of("user"));
        verify(request).setContent("<@&roles> <@users>");

        LayoutComponent component = mock();
        message = Message.of(embedBuilder("test"), component);
        results = Discord.asMessageRequests(message, mapper).toList();
        assertEquals(1, results.size());
        assertEquals(request, results.getFirst());
        verify(request).setComponents(List.of(component));

        Message.File file = Message.File.of("name", "test".getBytes());
        message = Message.of(embedBuilder("test"), file);
        results = Discord.asMessageRequests(message, mapper).toList();
        assertEquals(1, results.size());
        assertEquals(request, results.getFirst());
        verify(request).setFiles(anyCollection());
    }

    @Test
    void userPrivateChannel() {
        JDA jda = mock();
        Discord discord = mock();
        setJDA(discord, jda);
        doCallRealMethod().when(discord).userPrivateChannel(any(), any(), any());
        CacheRestAction<User> restAction = mock();
        when(jda.retrieveUserById("123")).thenReturn(restAction);
        when(restAction.flatMap(any())).thenReturn(mock());
        discord.userPrivateChannel("123", mock(), mock());
        verify(jda).retrieveUserById("123");
    }

    @Test
    void guildServer() {
        JDA jda = mock();
        Guild guild = mock();
        when(jda.getGuildById(123L)).thenReturn(guild);
        Discord discord = mock();
        setJDA(discord, jda);
        when(discord.guildServer(123L)).thenCallRealMethod();
        when(discord.guildServer("123")).thenCallRealMethod();
        assertEquals(Optional.of(guild), discord.guildServer(123L));
        assertEquals(guild, discord.guildServer("123"));
        when(jda.getGuildById(123L)).thenReturn(null);
        assertEquals(Optional.empty(), discord.guildServer(123L));
        assertNull(discord.guildServer("123"));
    }

    @Test
    void spotBotChannel() {
        Guild guild = mock();
        TextChannel textChannel = mock();
        when(guild.getTextChannelsByName(DEFAULT_BOT_CHANNEL, false)).thenReturn(List.of(textChannel));
        assertEquals(Optional.of(textChannel), Discord.spotBotChannel(guild, DEFAULT_BOT_CHANNEL));
        when(guild.getTextChannelsByName(DEFAULT_BOT_CHANNEL, false)).thenReturn(List.of());
        assertEquals(Optional.empty(), Discord.spotBotChannel(guild, DEFAULT_BOT_CHANNEL));
    }

    @Test
    void spotBotRole() {
        JDA jda = mock();
        Discord discord = mock();
        setJDA(discord, jda);
        Guild guild = mock();
        Role role = mock();
        when(guild.getRolesByName(DEFAULT_BOT_ROLE, false)).thenReturn(List.of(role));
        assertEquals(Optional.of(role), Discord.spotBotRole(guild, DEFAULT_BOT_ROLE));
        when(guild.getRolesByName(DEFAULT_BOT_ROLE, false)).thenReturn(List.of());
        assertEquals(Optional.empty(), Discord.spotBotRole(guild, DEFAULT_BOT_ROLE));
    }

    @Test
    void guildName() {
        Guild guild = mock();
        when(guild.getName()).thenReturn("guild name");
        when(guild.getIdLong()).thenReturn(111L);
        assertEquals("guild name (111)", Discord.guildName(guild));
    }

    @Test
    void spotBotUserMention() {
        JDA jda = mock();
        Discord discord = mock();
        setJDA(discord, jda);
        SelfUser user = mock();
        when(jda.getSelfUser()).thenReturn(user);
        when(user.getAsMention()).thenReturn("selfmention");
        when(discord.spotBotUserMention()).thenCallRealMethod();
        assertEquals("selfmention", discord.spotBotUserMention());
    }

    @Test
    void getCommandListener() {
        JDA jda = mock();
        Discord discord = mock();
        setJDA(discord, jda);
        when(discord.registerCommand(any())).thenCallRealMethod();
        when(discord.getCommandListener(any())).thenCallRealMethod();

        assertThrows(NullPointerException.class, () -> discord.getCommandListener(null));
        assertNull(discord.getCommandListener(Commands.SPOTBOT_COMMANDS.getFirst().name()));
        Commands.SPOTBOT_COMMANDS.forEach(discord::registerCommand);
        assertNotNull(discord.getCommandListener(Commands.SPOTBOT_COMMANDS.getFirst().name()));
        for(var command : Commands.SPOTBOT_COMMANDS) {
            assertEquals(command, discord.getCommandListener(command.name()));
        }
    }

    @Test
    void getInteractionListener() {
        JDA jda = mock();
        Discord discord = mock();
        setJDA(discord, jda);
        doCallRealMethod().when(discord).registerInteractions(any());
        when(discord.getInteractionListener(any())).thenCallRealMethod();

        assertThrows(NullPointerException.class, () -> discord.getInteractionListener(null));
        assertNull(discord.getInteractionListener(Interactions.SPOTBOT_INTERACTIONS.getFirst().name()));
        discord.registerInteractions(Interactions.SPOTBOT_INTERACTIONS);
        assertNotNull(discord.getInteractionListener(Interactions.SPOTBOT_INTERACTIONS.getFirst().name()));
        for(var command : Interactions.SPOTBOT_INTERACTIONS) {
            assertEquals(command, discord.getInteractionListener(command.name()));
        }
    }
}