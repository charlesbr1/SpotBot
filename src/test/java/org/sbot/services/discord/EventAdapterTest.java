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
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sbot.commands.RangeCommand;
import org.sbot.commands.SpotBotCommand;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.settings.Settings;
import org.sbot.services.NotificationsService;
import org.sbot.services.SettingsService;
import org.sbot.services.context.Context;
import org.sbot.services.context.Context.DataServices;
import org.sbot.services.dao.AlertsDao;
import org.sbot.services.dao.AlertsDao.SelectionFilter;
import org.sbot.services.dao.NotificationsDao;
import org.sbot.services.dao.UserSettingsDao;

import java.awt.Color;
import java.time.Clock;
import java.util.List;
import java.util.stream.Stream;

import static java.util.function.Predicate.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.sbot.entities.alerts.Alert.PRIVATE_MESSAGES;
import static org.sbot.entities.alerts.AlertTest.TEST_CLIENT_TYPE;
import static org.sbot.entities.alerts.ClientType.DISCORD;
import static org.sbot.entities.notifications.RecipientType.DISCORD_USER;
import static org.sbot.entities.settings.ServerSettings.DEFAULT_BOT_CHANNEL;
import static org.sbot.entities.settings.ServerSettings.PRIVATE_SERVER;
import static org.sbot.entities.settings.UserSettings.NO_ID;
import static org.sbot.entities.settings.UserSettings.NO_USER;
import static org.sbot.services.discord.EventAdapter.ERROR_REPLY_DELETE_DELAY_SECONDS;
import static org.sbot.utils.ArgumentValidator.requireOneItem;

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
        UserSettingsDao userSettingsDao = mock();
        AlertsDao alertsDao = mock();
        NotificationsDao notificationsDao = mock();
        DataServices dataServices = mock();
        when(dataServices.userSettingsDao()).thenReturn(v -> userSettingsDao);
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
        UserSettingsDao userSettingsDao = mock();
        AlertsDao alertsDao = mock();
        NotificationsDao notificationsDao = mock();
        DataServices dataServices = mock();
        when(dataServices.userSettingsDao()).thenReturn(v -> userSettingsDao);
        when(dataServices.alertsDao()).thenReturn(v -> alertsDao);
        when(dataServices.notificationsDao()).thenReturn(v -> notificationsDao);
        when(context.dataServices()).thenReturn(dataServices);
        Discord discord = mock();
        when(context.discord()).thenReturn(discord);

        when(user.isBot()).thenReturn(true);
        adapter.onGuildBan(event);
        verify(alertsDao, never()).updateServerIdOf(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId, null), PRIVATE_MESSAGES);
        verify(notificationsService, never()).sendNotifications();

        when(user.isBot()).thenReturn(false);
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
        UserSettingsDao userSettingsDao = mock();
        AlertsDao alertsDao = mock();
        NotificationsDao notificationsDao = mock();
        DataServices dataServices = mock();
        when(dataServices.userSettingsDao()).thenReturn(v -> userSettingsDao);
        when(dataServices.alertsDao()).thenReturn(v -> alertsDao);
        when(dataServices.notificationsDao()).thenReturn(v -> notificationsDao);
        when(context.dataServices()).thenReturn(dataServices);
        Discord discord = mock();
        when(context.discord()).thenReturn(discord);

        when(user.isBot()).thenReturn(true);
        adapter.onGuildMemberRemove(event);
        verify(alertsDao, never()).updateServerIdOf(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId, null), PRIVATE_MESSAGES);
        verify(notificationsService, never()).sendNotifications();

        when(user.isBot()).thenReturn(false);
        adapter.onGuildMemberRemove(event);
        verify(alertsDao).updateServerIdOf(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId, null), PRIVATE_MESSAGES);
        verify(notificationsService, never()).sendNotifications();

        when(alertsDao.updateServerIdOf(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId, null), PRIVATE_MESSAGES)).thenReturn(1L);
        adapter.onGuildMemberRemove(event);
        verify(alertsDao, times(2)).updateServerIdOf(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId, null), PRIVATE_MESSAGES);
        verify(notificationsService).sendNotifications();
    }

    @Test
    void onGuildMemberJoin() {
        String userId = "123";
        GuildMemberJoinEvent event = mock();
        User user = mock();
        when(user.getId()).thenReturn(userId);
        when(event.getUser()).thenReturn(user);
        Context context = mock();
        when(context.transactional(any())).thenCallRealMethod();
        when(context.transactional(any(), any(), anyBoolean())).thenCallRealMethod();
        NotificationsService notificationsService = mock();
        when(context.notificationService()).thenReturn(notificationsService);
        EventAdapter adapter = new EventAdapter(context);

        assertThrows(NullPointerException.class, () -> adapter.onGuildMemberJoin(null));
        NotificationsDao notificationsDao = mock();
        DataServices dataServices = mock();
        when(dataServices.notificationsDao()).thenReturn(v -> notificationsDao);
        when(context.dataServices()).thenReturn(dataServices);

        when(user.isBot()).thenReturn(true);
        adapter.onGuildMemberJoin(event);
        verify(notificationsDao, never()).unblockStatusOfRecipient(any(), any());
        verify(notificationsService, never()).sendNotifications();

        when(user.isBot()).thenReturn(false);
        adapter.onGuildMemberJoin(event);
        verify(notificationsDao).unblockStatusOfRecipient(DISCORD_USER, userId);
        verify(notificationsService, never()).sendNotifications();

        when(notificationsDao.unblockStatusOfRecipient(DISCORD_USER, userId)).thenReturn(1L);
        adapter.onGuildMemberJoin(event);
        verify(notificationsDao, times(2)).unblockStatusOfRecipient(DISCORD_USER, userId);
        verify(notificationsService).sendNotifications();
    }

    @Test
    void onSlashCommandInteraction() {
        Context context = mock();
        Clock clock = Clock.systemUTC();
        when(context.clock()).thenReturn(clock);
        when(context.transactional(any())).thenCallRealMethod();
        when(context.transactional(any(), any(), anyBoolean())).thenCallRealMethod();
        SettingsService settingsService = mock();
        when(context.settingsService()).thenReturn(settingsService);
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
        verify(event, never()).replyEmbeds(any(), eq(new MessageEmbed[0]));
        verify(settingsService, never()).setupSettings(any(), anyLong(), anyLong(), any());

        when(user.isBot()).thenReturn(false);
        MessageChannelUnion channel = mock();
        when(channel.getType()).thenReturn(ChannelType.PRIVATE);
        when(event.getChannel()).thenReturn(channel);
        when(user.getIdLong()).thenReturn(123L);
        when(user.getAsMention()).thenReturn("userMention");
        when(event.getUserLocale()).thenReturn(DiscordLocale.CROATIAN);
        when(event.deferReply(anyBoolean())).thenReturn(mock());

        when(settingsService.setupSettings(DISCORD, 123L, NO_ID, DiscordLocale.CROATIAN.toLocale()))
                .thenReturn(new Settings(NO_USER, PRIVATE_SERVER));
        adapter.onSlashCommandInteraction(event);
        verify(event, times(1)).replyEmbeds(any(), eq(new MessageEmbed[0]));
        verify(event).deferReply(true);
        verify(settingsService).setupSettings(DISCORD, 123L, NO_ID, DiscordLocale.CROATIAN.toLocale());
    }

    @Test
    void onMessageReceived() {
        Context context = mock();
        Clock clock = Clock.systemUTC();
        when(context.clock()).thenReturn(clock);
        SettingsService settingsService = mock();
        when(context.settingsService()).thenReturn(settingsService);
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
        when(user.getIdLong()).thenReturn(123L);

        assertThrows(NullPointerException.class, () -> adapter.onMessageReceived(null));

        when(user.isBot()).thenReturn(true);
        adapter.onMessageReceived(event);
        verify(settingsService, never()).accessSettings(DISCORD, 123L, NO_ID);
        verify(event, never()).getMessage();
        verify(message, never()).replyEmbeds(any(), eq(new MessageEmbed[0]));

        when(settingsService.accessSettings(DISCORD, 123L, NO_ID))
                .thenReturn(new Settings(NO_USER, PRIVATE_SERVER));
        when(user.isBot()).thenReturn(false);
        adapter.onMessageReceived(event);
        verify(settingsService).accessSettings(DISCORD, 123L, NO_ID);
        verify(event, times(2)).getMessage();
        verify(message, never()).replyEmbeds(any(), eq(new MessageEmbed[0]));

        when(message.getContentRaw()).thenThrow(IllegalArgumentException.class);
        when(user.getAsMention()).thenReturn("userMention");
        adapter.onMessageReceived(event);
        verify(settingsService).accessSettings(DISCORD, 123L, NO_ID); // no more calls
        verify(event, times(3)).getMessage();
        verify(message, never()).replyEmbeds(any(), eq(new MessageEmbed[0]));

        when(message.replyEmbeds(any(), eq(new MessageEmbed[0]))).thenReturn(mock());
        when(settingsService.accessSettings(DISCORD, 123L, NO_ID))
                .thenThrow(IllegalArgumentException.class);
        doReturn("command").when(message).getContentRaw();
        adapter.onMessageReceived(event);
        verify(settingsService, times(2)).accessSettings(DISCORD, 123L, NO_ID);
        verify(event, times(5)).getMessage();
        verify(message).replyEmbeds(any(), eq(new MessageEmbed[0]));

        doReturn(new Settings(NO_USER, PRIVATE_SERVER)).when(settingsService).accessSettings(DISCORD, 123L, NO_ID);
        when(channel.getType()).thenReturn(ChannelType.UNKNOWN);
        Discord discord = mock();
        when(context.discord()).thenReturn(discord);
        when(discord.spotBotUserMention()).thenReturn("<@123>");
        when(message.getContentRaw()).thenReturn("<@123> command");
        verify(channel, never()).getName();
        adapter.onMessageReceived(event);
        verify(channel).getName();

        when(message.getContentRaw()).thenReturn("<@123> setup");
        adapter.onMessageReceived(event);
        verify(channel).getName(); // no more calls
    }

    @Test
    void processCommand() {
        CommandContext context = mock();
        Discord discord = mock();
        when(context.discord()).thenReturn(discord);
        CommandListener listener = mock();
        when(discord.getCommandListener(any())).thenReturn(listener);

        EventAdapter adapter = new EventAdapter(context);
        assertThrows(NullPointerException.class, () -> adapter.processCommand(null));

        adapter.processCommand(context);
        verify(listener).onCommand(context);
        verify(context, never()).reply(any(org.sbot.entities.Message.class), anyInt());

        when(discord.getCommandListener(any())).thenReturn(null);
        adapter.processCommand(context);
        verify(context).reply(any(org.sbot.entities.Message.class), anyInt());
    }

    @Test
    void onError() {
        CommandContext command = mock();
        assertThrows(NullPointerException.class, () -> EventAdapter.onError(null, mock(), mock()));
        assertThrows(NullPointerException.class, () -> EventAdapter.onError(mock(), null, mock()));

        when(command.isStringReader()).thenReturn(true);
        when(command.serverId()).thenReturn(PRIVATE_MESSAGES);
        ArgumentCaptor<org.sbot.entities.Message> argumentCaptor = ArgumentCaptor.forClass(org.sbot.entities.Message.class);
        EventAdapter.onError(command, new IllegalArgumentException("error message"), null);

        verify(command).reply(argumentCaptor.capture(), eq(ERROR_REPLY_DELETE_DELAY_SECONDS));
        var message = argumentCaptor.getValue();
        assertNotNull(message);
        assertNotNull(message.embeds());
        assertEquals(1, message.embeds().size());
        var description = requireOneItem(message.embeds()).getDescriptionBuilder().toString();
        assertEquals("error message", description);

        when(command.serverId()).thenReturn(123L);
        EventAdapter.onError(command, new IllegalArgumentException("error message"), null);

        verify(command, times(2)).reply(argumentCaptor.capture(), eq(ERROR_REPLY_DELETE_DELAY_SECONDS));
        message = argumentCaptor.getValue();
        description = requireOneItem(message.embeds()).getDescriptionBuilder().toString();
        assertEquals("<@0> error message", description);

        when(command.isStringReader()).thenReturn(false);
        EventAdapter.onError(command, new IllegalArgumentException("error message"), null);
        verify(command, times(3)).reply(argumentCaptor.capture(), eq(ERROR_REPLY_DELETE_DELAY_SECONDS));
        message = argumentCaptor.getValue();
        description = requireOneItem(message.embeds()).getDescriptionBuilder().toString();
        assertEquals("error message", description);

        when(command.isStringReader()).thenReturn(true);
        EventAdapter.onError(command, new IllegalArgumentException("error message"), new RangeCommand());
        verify(command, times(4)).reply(argumentCaptor.capture(), eq(ERROR_REPLY_DELETE_DELAY_SECONDS));
        message = argumentCaptor.getValue();
        description = requireOneItem(message.embeds()).getDescriptionBuilder().toString();
        assertTrue(description.startsWith("<@0> error message"));
        assertTrue(description.contains("parameters"));

        EventAdapter.onError(command, new UnsupportedOperationException("error message"), new RangeCommand());
        verify(command, times(5)).reply(argumentCaptor.capture(), eq(ERROR_REPLY_DELETE_DELAY_SECONDS));
        message = argumentCaptor.getValue();
        description = requireOneItem(message.embeds()).getDescriptionBuilder().toString();
        assertEquals("<@0> I don't know this command : null", description);

        EventAdapter.onError(command, new IllegalStateException("error message"), new RangeCommand());
        verify(command, times(6)).reply(argumentCaptor.capture(), eq(ERROR_REPLY_DELETE_DELAY_SECONDS));
        message = argumentCaptor.getValue();
        description = requireOneItem(message.embeds()).getDescriptionBuilder().toString();
        assertEquals("<@0> Something went wrong !\n\nerror message", description);
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
        assertThrows(NullPointerException.class, () -> EventAdapter.acceptCommand(null, "channel"));
        assertThrows(NullPointerException.class, () -> EventAdapter.acceptCommand(mock(), null));
        MessageChannel channel = mock();
        when(channel.getType()).thenReturn(ChannelType.PRIVATE);
        when(channel.getName()).thenReturn(DEFAULT_BOT_CHANNEL);

        assertTrue(EventAdapter.acceptCommand(channel, DEFAULT_BOT_CHANNEL));
        assertTrue(EventAdapter.acceptCommand(channel, "bad"));
        when(channel.getType()).thenReturn(ChannelType.UNKNOWN);
        assertTrue(EventAdapter.acceptCommand(channel, DEFAULT_BOT_CHANNEL));
        assertFalse(EventAdapter.acceptCommand(channel, "bad"));
        when(channel.getName()).thenReturn("bad channel");
        assertFalse(EventAdapter.acceptCommand(channel, DEFAULT_BOT_CHANNEL));
    }

    @Test
    void isPrivateMessage() {
        Stream.of(ChannelType.values()).filter(not(ChannelType.PRIVATE::equals))
                .forEach(type -> assertFalse(EventAdapter.isPrivateMessage(type)));
        assertTrue(EventAdapter.isPrivateMessage(ChannelType.PRIVATE));
    }

    @Test
    void isSpotBotChannel() {
        assertThrows(NullPointerException.class, () -> EventAdapter.isSpotBotChannel(null, DEFAULT_BOT_CHANNEL));
        assertThrows(NullPointerException.class, () -> EventAdapter.isSpotBotChannel(mock(), null));
        MessageChannel channel = mock();
        assertFalse(EventAdapter.isSpotBotChannel(channel, DEFAULT_BOT_CHANNEL));
        when(channel.getName()).thenReturn(DEFAULT_BOT_CHANNEL);
        assertTrue(EventAdapter.isSpotBotChannel(channel, DEFAULT_BOT_CHANNEL));
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
        when(user.getIdLong()).thenReturn(123L);
        when(user.getAsMention()).thenReturn("userMention");
        when(event.getUser()).thenReturn(user);
        CommandContext commandContext = mock();
        Context context = mock();
        Clock clock = Clock.systemUTC();
        when(context.clock()).thenReturn(clock);
        when(context.transactional(any())).thenCallRealMethod();
        when(context.transactional(any(), any(), anyBoolean())).thenCallRealMethod();
        Discord discord = mock();
        when(context.discord()).thenReturn(discord);
        SettingsService settingsService = mock();
        when(context.settingsService()).thenReturn(settingsService);
        EventAdapter adapter = new EventAdapter(context);
        ReplyCallbackAction replyCallbackAction = mock();
        when(event.replyEmbeds(any(), eq(new MessageEmbed[0]))).thenReturn(replyCallbackAction);
        when(event.getUserLocale()).thenReturn(DiscordLocale.CROATIAN);
        when(settingsService.setupSettings(DISCORD, 123L, NO_ID, DiscordLocale.CROATIAN.toLocale()))
                .thenReturn(new Settings(NO_USER, PRIVATE_SERVER));

        adapter.processInteraction(event, user, v -> commandContext);
        verify(settingsService).setupSettings(DISCORD, 123L, NO_ID, DiscordLocale.CROATIAN.toLocale());
        verify(discord).getInteractionListener(any());
        verify(event).replyEmbeds(any(), eq(new MessageEmbed[0]));
    }
}