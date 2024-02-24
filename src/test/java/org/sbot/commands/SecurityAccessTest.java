package org.sbot.commands;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.junit.jupiter.api.Test;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.alerts.Alert;

import static net.dv8tion.jda.api.Permission.ADMINISTRATOR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sbot.entities.alerts.Alert.PRIVATE_ALERT;
import static org.sbot.entities.alerts.AlertTest.createTestAlertWithUserId;

class SecurityAccessTest {

    private static CommandContext contextOf(User user, Member member) {
        CommandContext context = mock(CommandContext.class);
        try {
            var field = CommandContext.class.getDeclaredField("user");
            field.setAccessible(true);
            field.set(context, user);
            field = CommandContext.class.getDeclaredField("member");
            field.setAccessible(true);
            field.set(context, member);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertEquals(user, context.user);
        assertEquals(member, context.member);
        return context;
    }

    @Test
    void notFound() {
        long userId = 123L;
        assertThrows(NullPointerException.class, () -> SecurityAccess.notFound(null, mock()));

        // null alert -> OK
        assertTrue(SecurityAccess.notFound(mock(), null));

        // same user, private channel -> KO
        User user = mock();
        when(user.getIdLong()).thenReturn(userId);
        var context = contextOf(user, null);
        when(context.serverId()).thenReturn(PRIVATE_ALERT);
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
        context = contextOf(user, member);
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
    void isDenied() {
        long userId = 123L;
        assertThrows(NullPointerException.class, () -> SecurityAccess.isDenied(null, mock()));
        assertThrows(NullPointerException.class, () -> SecurityAccess.isDenied(mock(), null));

        // same user, private channel -> KO
        User user = mock();
        when(user.getIdLong()).thenReturn(userId);
        var context = contextOf(user, null);
        Alert alert = createTestAlertWithUserId(userId);
        assertFalse(SecurityAccess.isDenied(context, alert));

        // not same user, private channel -> OK
        var alertOther = createTestAlertWithUserId(987L);
        assertTrue(SecurityAccess.isDenied(context, alertOther));

        // same user, same server channel  -> KO
        Member member = mock();
        context = contextOf(user, member);
        assertFalse(SecurityAccess.isDenied(context, alert));

        // not same user, server channel, not admin -> OK
        long serverId = 999L;
        Guild guild = mock();
        when(guild.getIdLong()).thenReturn(serverId);
        when(member.getGuild()).thenReturn(guild);
        alertOther = alertOther.withServerId(serverId);
        assertTrue(SecurityAccess.isDenied(context, alertOther));

        // not same user, server channel, admin -> KO
        when(member.hasPermission(ADMINISTRATOR)).thenReturn(true);
        assertFalse(SecurityAccess.isDenied(context, alert));

        // not same user, not same server channel, admin -> OK
        assertTrue(SecurityAccess.isDenied(context, alertOther.withServerId(555L)));
        // not same user, not same server channel -> OK
        when(member.hasPermission(ADMINISTRATOR)).thenReturn(false);
        assertTrue(SecurityAccess.isDenied(context, alertOther.withServerId(555L)));
    }

    @Test
    void sameUserOrAdmin() {
        long userId = 123L;
        assertThrows(NullPointerException.class, () -> SecurityAccess.sameUserOrAdmin(null, userId));

        // same user, private channel -> OK
        User user = mock();
        when(user.getIdLong()).thenReturn(userId);
        var context = contextOf(user, null);
        assertTrue(SecurityAccess.sameUserOrAdmin(context, userId));

        // not same user, private channel -> KO
        assertFalse(SecurityAccess.sameUserOrAdmin(context, 765L));

        // same user, server channel -> OK
        Member member = mock();
        context = contextOf(user, member);
        assertTrue(SecurityAccess.sameUserOrAdmin(context, userId));

        // not same user, server channel, not admin -> KO
        assertFalse(SecurityAccess.sameUserOrAdmin(context, 765L));

        // not same user, server channel, admin -> OK
        when(member.hasPermission(ADMINISTRATOR)).thenReturn(true);
        assertTrue(SecurityAccess.sameUserOrAdmin(context, 765L));
    }

    @Test
    void sameUser() {
        long userId = 123L;
        assertThrows(NullPointerException.class, () -> SecurityAccess.sameUser(null, userId));
        User user = mock();
        assertFalse(SecurityAccess.sameUser(user, userId));
        when(user.getIdLong()).thenReturn(userId);
        assertTrue(SecurityAccess.sameUser(user, userId));
        assertFalse(SecurityAccess.sameUser(user, 543L));
    }

    @Test
    void sameServer() {
        long serverId = 123L;
        assertFalse(SecurityAccess.sameServer(null, serverId));
        Member member = mock();
        Guild guild = mock();
        when(member.getGuild()).thenReturn(guild);
        when(guild.getIdLong()).thenReturn(serverId);
        assertTrue(SecurityAccess.sameServer(member, serverId));
        assertFalse(SecurityAccess.sameServer(member, 543L));
    }

    @Test
    void isAdminMember() {
        assertFalse(SecurityAccess.isAdminMember(null));
        Member member = mock();
        assertFalse(SecurityAccess.isAdminMember(member));
        when(member.hasPermission(ADMINISTRATOR)).thenReturn(true);
        assertTrue(SecurityAccess.isAdminMember(member));
    }
}