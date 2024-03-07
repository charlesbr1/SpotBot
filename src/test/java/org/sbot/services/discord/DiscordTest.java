package org.sbot.services.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
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
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.sbot.commands.CommandAdapter.embedBuilder;
import static org.sbot.services.discord.Discord.DISCORD_BOT_CHANNEL;
import static org.sbot.services.discord.Discord.DISCORD_BOT_ROLE;

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
    void sendGuildMessage() {
    }

    @Test
    void sendPrivateMessage() {
    }

    @Test
    void sendMessages() {
    }

    @Test
    void replyMessages() {
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

        Message.File file = Message.File.of("test".getBytes(), "name");
        message = Message.of(embedBuilder("test"), file);
        results = Discord.asMessageRequests(message, mapper).toList();
        assertEquals(1, results.size());
        assertEquals(request, results.getFirst());
        verify(request).setFiles(anyCollection());
    }

    @Test
    void guildServer() {
        JDA jda = mock();
        Guild guild = mock();
        when(jda.getGuildById(123L)).thenReturn(guild);
        Discord discord = mock();
        setJDA(discord, jda);
        when(discord.guildServer(123L)).thenCallRealMethod();
        assertEquals(Optional.of(guild), discord.guildServer(123L));
    }

    @Test
    void spotBotChannel() {
        Guild guild = mock();
        TextChannel textChannel = mock();
        when(guild.getTextChannelsByName(DISCORD_BOT_CHANNEL, true)).thenReturn(List.of(textChannel));
        assertEquals(Optional.of(textChannel), Discord.spotBotChannel(guild));
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
    void spotBotRole() {
        JDA jda = mock();
        Discord discord = mock();
        setJDA(discord, jda);
        Guild guild = mock();
        Role role = mock();
        when(guild.getRolesByName(DISCORD_BOT_ROLE, true)).thenReturn(List.of(role));
        assertEquals(Optional.of(role), Discord.spotBotRole(guild));
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