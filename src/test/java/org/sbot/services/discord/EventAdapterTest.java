package org.sbot.services.discord;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.Test;
import org.sbot.commands.SpotBotCommand;
import org.sbot.commands.context.CommandContext;
import org.sbot.services.NotificationsService;
import org.sbot.services.context.Context;
import org.sbot.services.context.Context.DataServices;
import org.sbot.services.dao.AlertsDao;
import org.sbot.services.dao.AlertsDao.SelectionFilter;
import org.sbot.services.dao.NotificationsDao;
import org.sbot.services.dao.UsersDao;

import java.awt.*;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.function.Predicate.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.sbot.entities.alerts.Alert.PRIVATE_MESSAGES;
import static org.sbot.entities.alerts.AlertTest.TEST_CLIENT_TYPE;

class EventAdapterTest {

    @Test
    void constructor() {
        assertThrows(NullPointerException.class, () -> new EventAdapter(null));
        assertDoesNotThrow(() -> new EventAdapter(mock()));
    }

    @Test
    void onGuildLeave() {
        long serverId = 321L;
        GuildLeaveEvent event = mock();
        Guild guild = mock();
        when(guild.getIdLong()).thenReturn(serverId);
        when(event.getGuild()).thenReturn(guild);
        Context context = mock();
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.transactional(any())).thenCallRealMethod();
        when(context.transactional(any(), any(), anyBoolean())).thenCallRealMethod();
        NotificationsService notificationsService = mock();
        when(context.notificationService()).thenReturn(notificationsService);
        EventAdapter adapter = new EventAdapter(context);

        assertThrows(NullPointerException.class, () -> adapter.onGuildLeave(null));
        UsersDao usersDao = mock();
        AlertsDao alertsDao = mock();
        NotificationsDao notificationsDao = mock();
        DataServices dataServices = mock();
        when(dataServices.usersDao()).thenReturn(v -> usersDao);
        when(dataServices.alertsDao()).thenReturn(v -> alertsDao);
        when(dataServices.notificationsDao()).thenReturn(v -> notificationsDao);
        when(context.dataServices()).thenReturn(dataServices);
        Discord discord = mock();
        when(context.discord()).thenReturn(discord);

        when(alertsDao.getUserIdsByServerId(TEST_CLIENT_TYPE, serverId)).thenReturn(List.of());
        adapter.onGuildLeave(event);
        verify(alertsDao).getUserIdsByServerId(TEST_CLIENT_TYPE, serverId);
        verify(alertsDao, never()).updateServerIdOf(any(), anyLong());
        verify(notificationsService, never()).sendNotifications();

        when(alertsDao.getUserIdsByServerId(TEST_CLIENT_TYPE, serverId)).thenReturn(List.of(123L));
        when(alertsDao.updateServerIdOf(SelectionFilter.ofServer(TEST_CLIENT_TYPE, serverId, null), PRIVATE_MESSAGES)).thenReturn(1L);
        adapter.onGuildLeave(event);
        verify(alertsDao, times(2)).getUserIdsByServerId(TEST_CLIENT_TYPE, serverId);
        verify(alertsDao).updateServerIdOf(SelectionFilter.ofServer(TEST_CLIENT_TYPE, serverId, null), PRIVATE_MESSAGES);
        verify(notificationsService).sendNotifications();
    }

    @Test
    void onGuildBan() {
        long serverId = 321L;
        long userId = 123L;
        GuildBanEvent event = mock();
        Guild guild = mock();
        when(guild.getIdLong()).thenReturn(serverId);
        when(event.getGuild()).thenReturn(guild);
        User user = mock();
        when(user.getIdLong()).thenReturn(userId);
        when(event.getUser()).thenReturn(user);
        Context context = mock();
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.transactional(any())).thenCallRealMethod();
        when(context.transactional(any(), any(), anyBoolean())).thenCallRealMethod();
        NotificationsService notificationsService = mock();
        when(context.notificationService()).thenReturn(notificationsService);
        EventAdapter adapter = new EventAdapter(context);

        assertThrows(NullPointerException.class, () -> adapter.onGuildBan(null));
        UsersDao usersDao = mock();
        AlertsDao alertsDao = mock();
        NotificationsDao notificationsDao = mock();
        DataServices dataServices = mock();
        when(dataServices.usersDao()).thenReturn(v -> usersDao);
        when(dataServices.alertsDao()).thenReturn(v -> alertsDao);
        when(dataServices.notificationsDao()).thenReturn(v -> notificationsDao);
        when(context.dataServices()).thenReturn(dataServices);
        Discord discord = mock();
        when(context.discord()).thenReturn(discord);

        adapter.onGuildBan(event);
        verify(alertsDao).updateServerIdOf(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId, null), PRIVATE_MESSAGES);
        verify(notificationsService, never()).sendNotifications();

        when(alertsDao.updateServerIdOf(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId, null), PRIVATE_MESSAGES)).thenReturn(1L);
        adapter.onGuildBan(event);
        verify(alertsDao, times(2)).updateServerIdOf(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId, null), PRIVATE_MESSAGES);
        verify(notificationsService).sendNotifications();
    }

    @Test
    void onGuildMemberRemove() {
        long serverId = 321L;
        long userId = 123L;
        GuildMemberRemoveEvent event = mock();
        Guild guild = mock();
        when(guild.getIdLong()).thenReturn(serverId);
        when(event.getGuild()).thenReturn(guild);
        User user = mock();
        when(user.getIdLong()).thenReturn(userId);
        when(event.getUser()).thenReturn(user);
        Context context = mock();
        when(context.clock()).thenReturn(Clock.systemUTC());
        when(context.transactional(any())).thenCallRealMethod();
        when(context.transactional(any(), any(), anyBoolean())).thenCallRealMethod();
        NotificationsService notificationsService = mock();
        when(context.notificationService()).thenReturn(notificationsService);
        EventAdapter adapter = new EventAdapter(context);

        assertThrows(NullPointerException.class, () -> adapter.onGuildMemberRemove(null));
        UsersDao usersDao = mock();
        AlertsDao alertsDao = mock();
        NotificationsDao notificationsDao = mock();
        DataServices dataServices = mock();
        when(dataServices.usersDao()).thenReturn(v -> usersDao);
        when(dataServices.alertsDao()).thenReturn(v -> alertsDao);
        when(dataServices.notificationsDao()).thenReturn(v -> notificationsDao);
        when(context.dataServices()).thenReturn(dataServices);
        Discord discord = mock();
        when(context.discord()).thenReturn(discord);

        adapter.onGuildMemberRemove(event);
        verify(alertsDao).updateServerIdOf(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId, null), PRIVATE_MESSAGES);
        verify(notificationsService, never()).sendNotifications();

        when(alertsDao.updateServerIdOf(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId, null), PRIVATE_MESSAGES)).thenReturn(1L);
        adapter.onGuildMemberRemove(event);
        verify(alertsDao, times(2)).updateServerIdOf(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId, null), PRIVATE_MESSAGES);
        verify(notificationsService).sendNotifications();
    }

    @Test
    void onSlashCommandInteraction() {
        Context context = mock();
        when(context.transactional(any())).thenCallRealMethod();
        when(context.transactional(any(), any(), anyBoolean())).thenCallRealMethod();
        EventAdapter adapter = new EventAdapter(context);
        SlashCommandInteractionEvent event = mock();
        User user = mock();
        when(event.getUser()).thenReturn(user);
        ReplyCallbackAction replyCallbackAction = mock();
        when(event.replyEmbeds(any(), eq(new MessageEmbed[0]))).thenReturn(replyCallbackAction);
        when(replyCallbackAction.setEphemeral(anyBoolean())).thenReturn(replyCallbackAction);

        assertThrows(NullPointerException.class, () -> adapter.onSlashCommandInteraction(null));

        when(user.isBot()).thenReturn(true);
        adapter.onSlashCommandInteraction(event);
        verify(event).replyEmbeds(any(), eq(new MessageEmbed[0]));

        when(user.isBot()).thenReturn(false);
        MessageChannelUnion channel = mock();
        when(channel.getType()).thenReturn(ChannelType.PRIVATE);
        when(event.getChannel()).thenReturn(channel);
        UsersDao usersDao = mock();
        DataServices dataServices = mock();
        when(dataServices.usersDao()).thenReturn(v -> usersDao);
        when(context.dataServices()).thenReturn(dataServices);
        when(user.getIdLong()).thenReturn(123L);
        when(user.getAsMention()).thenReturn("userMention");
        when(event.getUserLocale()).thenReturn(DiscordLocale.CROATIAN);
        when(event.deferReply(anyBoolean())).thenReturn(mock());

        adapter.onSlashCommandInteraction(event);
        verify(event, times(2)).replyEmbeds(any(), eq(new MessageEmbed[0]));
        verify(usersDao).setupUser(anyLong(), any(), any());
    }

    @Test
    void onMessageReceived() {
        Context context = mock();
        when(context.transactional(any())).thenCallRealMethod();
        when(context.transactional(any(), any(), anyBoolean())).thenCallRealMethod();
        EventAdapter adapter = new EventAdapter(context);
        MessageReceivedEvent event = mock();
        User user = mock();
        when(event.getAuthor()).thenReturn(user);
        MessageChannelUnion channel = mock();
        when(channel.getType()).thenReturn(ChannelType.PRIVATE);
        when(event.getChannel()).thenReturn(channel);
        Message message = mock();
        when(event.getMessage()).thenReturn(message);
        when(message.getContentRaw()).thenReturn("command");

        UsersDao usersDao = mock();
        DataServices dataServices = mock();
        when(dataServices.usersDao()).thenReturn(v -> usersDao);
        when(context.dataServices()).thenReturn(dataServices);
        when(user.getIdLong()).thenReturn(123L);

        assertThrows(NullPointerException.class, () -> adapter.onMessageReceived(null));

        adapter.onMessageReceived(event);
        verify(usersDao).accessUser(anyLong(), any());
        verify(event, times(3)).getMessage();
        verify(message, never()).replyEmbeds(any(), eq(new MessageEmbed[0]));

        when(message.getContentRaw()).thenThrow(IllegalArgumentException.class);
        when(user.getAsMention()).thenReturn("userMention");
        when(message.replyEmbeds(any(), eq(new MessageEmbed[0]))).thenReturn(mock());
        adapter.onMessageReceived(event);
        verify(message).replyEmbeds(any(), eq(new MessageEmbed[0]));
    }

    @Test
    void processCommand() throws NoSuchFieldException, IllegalAccessException {
        CommandContext context = mock();
        Discord discord = mock();
        when(context.discord()).thenReturn(discord);
        CommandListener listener = mock();
        when(discord.getCommandListener(any())).thenReturn(listener);

        EventAdapter adapter = new EventAdapter(context);
        adapter.processCommand(context);
        verify(listener).onCommand(context);
        verify(context, never()).reply(any(org.sbot.entities.Message.class), anyInt());

        when(discord.getCommandListener(any())).thenReturn(null);
        var field = CommandContext.class.getDeclaredField("user");
        field.setAccessible(true);
        field.set(context, mock(User.class));
        adapter.processCommand(context);
        verify(context).reply(any(org.sbot.entities.Message.class), anyInt());
    }

    @Test
    void commandArguments() {
        assertEquals("\n\n" +
                "**spotbot** *parameter :*\n" +
                "\n" +
                "- **selection** (_string, optional_) which help to show : 'doc' or 'commands', default to 'doc' if omitted",
                EventAdapter.commandArguments(new SpotBotCommand()));
    }

    @Test
    void errorEmbed() {
        assertThrows(NullPointerException.class, () -> EventAdapter.errorEmbed(null, "test"));
        var embed = EventAdapter.errorEmbed("userMention", "error");
        assertNotNull(embed);
        assertEquals(Color.red, embed.getColor());
    }

    @Test
    void acceptCommand() {
        assertThrows(NullPointerException.class, () -> EventAdapter.acceptCommand(null, mock()));
        assertThrows(NullPointerException.class, () -> EventAdapter.acceptCommand(mock(), null));
        User user = mock();
        when(user.isBot()).thenReturn(false);
        MessageChannel channel = mock();
        when(channel.getType()).thenReturn(ChannelType.PRIVATE);
        when(channel.getName()).thenReturn(Discord.DISCORD_BOT_CHANNEL);

        assertTrue(EventAdapter.acceptCommand(user, channel));
        when(user.isBot()).thenReturn(true);
        assertFalse(EventAdapter.acceptCommand(user, channel));
        when(user.isBot()).thenReturn(false);
        assertTrue(EventAdapter.acceptCommand(user, channel));
        when(channel.getType()).thenReturn(ChannelType.UNKNOWN);
        assertTrue(EventAdapter.acceptCommand(user, channel));
        when(channel.getName()).thenReturn("bad channel");
        assertFalse(EventAdapter.acceptCommand(user, channel));
    }

    @Test
    void isPrivateMessage() {
        Stream.of(ChannelType.values()).filter(not(ChannelType.PRIVATE::equals))
                .forEach(type -> assertFalse(EventAdapter.isPrivateMessage(type)));
        assertTrue(EventAdapter.isPrivateMessage(ChannelType.PRIVATE));
    }

    @Test
    void isSpotBotChannel() {
        assertThrows(NullPointerException.class, () -> EventAdapter.isSpotBotChannel(null));
        MessageChannel channel = mock();
        assertFalse(EventAdapter.isSpotBotChannel(channel));
        when(channel.getName()).thenReturn(Discord.DISCORD_BOT_CHANNEL);
        assertTrue(EventAdapter.isSpotBotChannel(channel));
    }

    @Test
    void removeStartingMentions() {
        String test = "test";
        assertEquals(test, EventAdapter.removeStartingMentions(test));
        assertEquals(test, EventAdapter.removeStartingMentions("<@123>test"));
        assertEquals(test, EventAdapter.removeStartingMentions("<@123> test"));
        assertEquals(test, EventAdapter.removeStartingMentions("<@321> <@123> test"));
        assertEquals(test, EventAdapter.removeStartingMentions("<@321><@456> <@123> test"));
        assertEquals("test<@123>", EventAdapter.removeStartingMentions("test<@123>"));
    }

    @Test
    void onStringSelectInteraction() {
        Context context = mock();
        EventAdapter adapter = new EventAdapter(context);
        assertThrows(NullPointerException.class, () -> adapter.onStringSelectInteraction(null));
    }

    @Test
    void onModalInteraction() {
        Context context = mock();
        EventAdapter adapter = new EventAdapter(context);
        assertThrows(NullPointerException.class, () -> adapter.onModalInteraction(null));
    }

    @Test
    void processInteraction() {
        IReplyCallback event = mock();
        User user = mock();
        when(user.getAsMention()).thenReturn("userMention");
        when(event.getUser()).thenReturn(user);
        CommandContext commandContext = mock();
        Context context = mock();
        when(context.transactional(any())).thenCallRealMethod();
        when(context.transactional(any(), any(), anyBoolean())).thenCallRealMethod();
        Discord discord = mock();
        when(context.discord()).thenReturn(discord);
        EventAdapter adapter = new EventAdapter(context);
        UsersDao usersDao = mock();
        DataServices dataServices = mock();
        when(dataServices.usersDao()).thenReturn(v -> usersDao);
        when(context.dataServices()).thenReturn(dataServices);
        ReplyCallbackAction replyCallbackAction = mock();
        when(event.replyEmbeds(any(), eq(new MessageEmbed[0]))).thenReturn(replyCallbackAction);
        when(usersDao.getUser(anyLong())).thenReturn(Optional.of(mock()));

        adapter.processInteraction(event, user, v -> commandContext);
        verify(usersDao).getUser(anyLong());
        verify(discord).getInteractionListener(any());
        verify(event).replyEmbeds(any(), eq(new MessageEmbed[0]));
    }
}