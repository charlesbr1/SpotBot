package org.sbot.commands;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.junit.jupiter.api.Test;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.ServerSettings;
import org.sbot.entities.alerts.Alert;

import java.util.List;

import static net.dv8tion.jda.api.Permission.ADMINISTRATOR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.sbot.entities.ServerSettings.DEFAULT_BOT_ROLE_ADMIN;
import static org.sbot.entities.alerts.Alert.PRIVATE_MESSAGES;
import static org.sbot.entities.alerts.AlertTest.TEST_CLIENT_TYPE;
import static org.sbot.entities.alerts.AlertTest.createTestAlertWithUserId;

class SecurityAccessTest {

    private static CommandContext contextOf(long userId, Member member, ServerSettings serverSettings) {
        CommandContext context = mock(CommandContext.class);
        try {
            var field = CommandContext.class.getDeclaredField("userId");
            field.setAccessible(true);
            field.set(context, userId);
            field = CommandContext.class.getDeclaredField("discordMember");
            field.setAccessible(true);
            field.set(context, member);
            field = CommandContext.class.getDeclaredField("clientType");
            field.setAccessible(true);
            field.set(context, TEST_CLIENT_TYPE);
            field = CommandContext.class.getDeclaredField("serverSettings");
            field.setAccessible(true);
            field.set(context, serverSettings);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertEquals(userId, context.userId);
        assertEquals(member, context.discordMember);
        return context;
    }

    @Test
    void notFound() {
        assertThrows(NullPointerException.class, () -> SecurityAccess.notFound(null, mock()));

        long userId = 123L;
        // null alert -> OK
        assertTrue(SecurityAccess.notFound(mock(), null));

        // same user, private channel -> KO
        var context = contextOf(userId, null, null);
        when(context.serverId()).thenReturn(PRIVATE_MESSAGES);
        Alert alert = createTestAlertWithUserId(userId);
        assertFalse(SecurityAccess.notFound(context, alert));

        // not same user, private channel -> OK
        var alertOther = createTestAlertWithUserId(987L);
        assertTrue(SecurityAccess.notFound(context, alertOther));

        // same user, same server channel  -> KO
        long serverId = 999L;
        Member member = mock();
        Guild guild = mock();
        when(guild.getIdLong()).thenReturn(serverId);
        when(member.getGuild()).thenReturn(guild);
        context = contextOf(userId, member, null);
        when(context.serverId()).thenReturn(serverId);
        assertFalse(SecurityAccess.notFound(context, alert.withServerId(serverId)));

        // not same user, same server channel -> KO
        assertFalse(SecurityAccess.notFound(context, alertOther.withServerId(serverId)));

        // same user, not private not same server channel -> OK
        when(context.serverId()).thenReturn(777L);
        when(guild.getIdLong()).thenReturn(777L);
        assertTrue(SecurityAccess.notFound(context, alert.withServerId(serverId)));

        // not same user, not private not same server channel -> OK
        assertTrue(SecurityAccess.notFound(context, alertOther.withServerId(serverId)));
    }

    @Test
    void canUpdate() {
        long userId = 123L;
        assertThrows(NullPointerException.class, () -> SecurityAccess.canUpdate(null, mock()));
        assertThrows(NullPointerException.class, () -> SecurityAccess.canUpdate(mock(), null));

        ServerSettings serverSettings = mock();
        // same user, private channel -> OK
        var context = contextOf(userId, null, serverSettings);
        Alert alert = createTestAlertWithUserId(userId);
        assertTrue(SecurityAccess.canUpdate(context, alert));

        // not same user, private channel -> KO
        alert = createTestAlertWithUserId(987L);
        assertFalse(SecurityAccess.canUpdate(context, alert));

        // same user, same server channel  -> OK
        alert = createTestAlertWithUserId(userId);
        Member member = mock();
        context = contextOf(userId, member, serverSettings);
        assertTrue(SecurityAccess.canUpdate(context, alert));

        // not same user, server channel, not admin -> KO
        long serverId = 999L;
        Guild guild = mock();
        when(guild.getIdLong()).thenReturn(serverId);
        when(member.getGuild()).thenReturn(guild);
        alert = createTestAlertWithUserId(987L).withServerId(serverId);
        assertFalse(SecurityAccess.canUpdate(context, alert));

        // not same user, server channel, admin -> OK
        alert = createTestAlertWithUserId(987L).withServerId(serverId);
        when(member.hasPermission(ADMINISTRATOR)).thenReturn(true);
        assertTrue(SecurityAccess.canUpdate(context, alert));

        // not same user, server channel, spotBotAdmin -> OK
        alert = createTestAlertWithUserId(987L).withServerId(serverId);
        when(member.hasPermission(ADMINISTRATOR)).thenReturn(false);
        assertFalse(SecurityAccess.canUpdate(context, alert));
        Role role = mock();
        when(guild.getRolesByName(DEFAULT_BOT_ROLE_ADMIN, false)).thenReturn(List.of(role));
        when(member.getRoles()).thenReturn(List.of(role));
        assertFalse(SecurityAccess.canUpdate(context, alert));

        when(serverSettings.spotBotAdminRole()).thenReturn(DEFAULT_BOT_ROLE_ADMIN);
        assertTrue(SecurityAccess.canUpdate(context, alert));
        verify(guild).getRolesByName(DEFAULT_BOT_ROLE_ADMIN, false);
        verify(member, times(4)).getRoles();

        when(serverSettings.spotBotAdminRole()).thenReturn("anotherRole");
        assertFalse(SecurityAccess.canUpdate(context, alert));
        verify(guild).getRolesByName("anotherRole", false);
        verify(member, times(5)).getRoles();

        when(guild.getRolesByName("anotherRole", false)).thenReturn(List.of(role));
        assertTrue(SecurityAccess.canUpdate(context, alert));
        verify(guild, times(2)).getRolesByName("anotherRole", false);
        verify(member, times(6)).getRoles();

        // not same user, not same server channel, spotBotAdmin -> KO
        alert = createTestAlertWithUserId(987L).withServerId(555L);
        assertFalse(SecurityAccess.canUpdate(context, alert));
        verify(guild, times(2)).getRolesByName("anotherRole", false);
        verify(member, times(6)).getRoles();

        // not same user, not same server channel, admin -> KO
        when(guild.getRolesByName(anyString(), anyBoolean())).thenReturn(List.of());
        alert = createTestAlertWithUserId(987L).withServerId(555L);
        assertFalse(SecurityAccess.canUpdate(context, alert));
        verify(guild, times(2)).getRolesByName("anotherRole", false);
        verify(member, times(6)).getRoles();

        // not same user, not same server channel -> KO
        alert = createTestAlertWithUserId(987L).withServerId(555L);
        when(member.hasPermission(ADMINISTRATOR)).thenReturn(false);
        assertFalse(SecurityAccess.canUpdate(context, alert));
        verify(guild, times(2)).getRolesByName("anotherRole", false);
        verify(member, times(6)).getRoles();
    }

    @Test
    void sameUserOrAdmin() {
        long userId = 123L;
        assertThrows(NullPointerException.class, () -> SecurityAccess.sameUserOrAdmin(null, userId));

        ServerSettings serverSettings = mock();
        // same user, private channel -> OK
        var context = contextOf(userId, null, serverSettings);
        assertTrue(SecurityAccess.sameUserOrAdmin(context, userId));

        // not same user, private channel -> KO
        assertFalse(SecurityAccess.sameUserOrAdmin(context, 765L));

        // same user, server channel -> OK
        Member member = mock();
        context = contextOf(userId, member, serverSettings);
        assertTrue(SecurityAccess.sameUserOrAdmin(context, userId));

        // not same user, server channel, not admin -> KO
        assertFalse(SecurityAccess.sameUserOrAdmin(context, 765L));

        // not same user, server channel, admin -> OK
        when(member.hasPermission(ADMINISTRATOR)).thenReturn(true);
        assertTrue(SecurityAccess.sameUserOrAdmin(context, 765L));

        // not same user, server channel, spotBotAdmin -> OK
        when(member.hasPermission(ADMINISTRATOR)).thenReturn(false);
        assertFalse(SecurityAccess.sameUserOrAdmin(context, 765L));

        Role role = mock();
        Guild guild = mock();
        when(member.getGuild()).thenReturn(guild);
        when(serverSettings.spotBotAdminRole()).thenReturn(DEFAULT_BOT_ROLE_ADMIN);
        when(guild.getRolesByName(DEFAULT_BOT_ROLE_ADMIN, false)).thenReturn(List.of(role));
        when(member.getRoles()).thenReturn(List.of(role));
        assertTrue(SecurityAccess.sameUserOrAdmin(context, 765L));

        when(serverSettings.spotBotAdminRole()).thenReturn("another");
        assertFalse(SecurityAccess.sameUserOrAdmin(context, 765L));
        when(guild.getRolesByName("another", false)).thenReturn(List.of(role));
        assertTrue(SecurityAccess.sameUserOrAdmin(context, 765L));
    }

    @Test
    void sameUser() {
        long userId = 123L;
        assertThrows(NullPointerException.class, () -> SecurityAccess.sameUser(null, userId));
        CommandContext context = contextOf(33L, null, null);
        assertFalse(SecurityAccess.sameUser(context, userId));
        context = contextOf(userId, null, null);
        assertTrue(SecurityAccess.sameUser(context, userId));
        assertFalse(SecurityAccess.sameUser(context, 543L));
    }

    @Test
    void sameServer() {
        long serverId = 123L;
        assertThrows(NullPointerException.class, () -> SecurityAccess.sameServer(null, serverId));
        Member member = mock();
        Guild guild = mock();
        when(member.getGuild()).thenReturn(guild);
        when(guild.getIdLong()).thenReturn(serverId);
        CommandContext context = contextOf(33L, member, null);
        assertTrue(SecurityAccess.sameServer(context, serverId));
        assertFalse(SecurityAccess.sameServer(context, 543L));
        context = contextOf(33L, null, null);
        assertFalse(SecurityAccess.sameServer(context, serverId));
    }

    @Test
    void isSpotBotAdmin() {
        assertThrows(NullPointerException.class, () -> SecurityAccess.isSpotBotAdmin(null));
        ServerSettings serverSettings = mock();
        Member member = mock();
        var context = contextOf(123L, member, serverSettings);
        Role role = mock();
        Guild guild = mock();
        when(member.getGuild()).thenReturn(guild);
        when(member.getRoles()).thenReturn(List.of(role));
        assertFalse(SecurityAccess.isSpotBotAdmin(context));

        when(serverSettings.spotBotAdminRole()).thenReturn(DEFAULT_BOT_ROLE_ADMIN);
        assertFalse(SecurityAccess.isSpotBotAdmin(context));

        when(guild.getRolesByName(DEFAULT_BOT_ROLE_ADMIN, false)).thenReturn(List.of(role));
        assertTrue(SecurityAccess.isSpotBotAdmin(context));

        when(serverSettings.spotBotAdminRole()).thenReturn("another");
        assertFalse(SecurityAccess.isSpotBotAdmin(context));

        when(guild.getRolesByName("another", false)).thenReturn(List.of(role));
        assertTrue(SecurityAccess.isSpotBotAdmin(context));

        context = contextOf(123L, null, serverSettings);
        assertFalse(SecurityAccess.isSpotBotAdmin(context));
    }

    @Test
    void isAdminMember() {
        CommandContext context = contextOf(33L, null, null);
        assertFalse(SecurityAccess.isAdminMember(context));
        Member member = mock();
        context = contextOf(33L, member, null);
        assertFalse(SecurityAccess.isAdminMember(context));
        when(member.hasPermission(ADMINISTRATOR)).thenReturn(true);
        assertTrue(SecurityAccess.isAdminMember(context));
    }
}