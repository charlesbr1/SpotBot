package org.sbot;

import org.junit.jupiter.api.Test;
import org.sbot.services.AlertsWatcher;
import org.sbot.services.context.Context;
import org.sbot.services.context.Context.Parameters;
import org.sbot.utils.Dates;
import org.sbot.utils.DatesTest;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import static java.time.temporal.ChronoUnit.HOURS;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.sbot.SpotBot.*;
import static org.sbot.services.context.Context.Parameters.MAX_CHECK_PERIOD;
import static org.sbot.services.context.Context.Parameters.MAX_HOURLY_SYNC_DELTA;
import static org.sbot.utils.Dates.UTC;

class SpotBotTest {

    @Test
    void spotBotThread() {
        assertThrows(NullPointerException.class, () -> SpotBot.spotBotThread(null));
        Context context = mock();
        AlertsWatcher alertsWatcher = mock();
        when(context.alertsWatcher()).thenReturn(alertsWatcher);

        ZonedDateTime date = Dates.parseLocalDateTime(Locale.UK, "12/12/2012-10:00").atZone(UTC);
        when(context.clock()).thenReturn(Clock.fixed(date.toInstant(), UTC));
        Parameters parameters = mock();
        when(context.parameters()).thenReturn(parameters);
        when(parameters.checkPeriodMin()).thenReturn(50);
        when(parameters.hourlySyncDeltaMin()).thenReturn(MAX_HOURLY_SYNC_DELTA);
        assertEquals(15, SpotBot.minutesUntilNextCheck(date, parameters.checkPeriodMin(), parameters.hourlySyncDeltaMin()));

        var lock = new ReentrantLock();
        var check = new AtomicBoolean(false);
        Thread thread = new Thread(() -> {
            lock.lock();
            SpotBot.spotBotThread(context).run();
            check.set(true);
            assertTrue(Thread.currentThread().isInterrupted());
            lock.unlock();
        }, "test");
        thread.start();
        Thread.yield();
        LockSupport.parkNanos(500_000_000); // wait 500 ms to give more opportunities for thread to execute...
        Thread.yield();
        thread.interrupt();
        lock.lock();
        assertTrue(check.get());
        verify(alertsWatcher).checkAlerts(); // this may sometime fails, perfectible test...
    }

    @Test
    void checkAlerts() {
        Context context = mock();
        AlertsWatcher alertsWatcher = mock();
        when(context.alertsWatcher()).thenReturn(alertsWatcher);
        ZonedDateTime date = Dates.parseLocalDateTime(Locale.UK, "12/12/2012-10:00").atZone(UTC);
        when(context.clock()).thenReturn(Clock.fixed(date.toInstant(), UTC));
        Parameters parameters = mock();
        when(context.parameters()).thenReturn(parameters);
        when(parameters.checkPeriodMin()).thenReturn(10);
        when(parameters.hourlySyncDeltaMin()).thenReturn(3);
        assertEquals(3, SpotBot.checkAlerts(context));
        verify(alertsWatcher).checkAlerts();
    }

    @Test
    void getParameters() {
        assertThrows(NullPointerException.class, () -> SpotBot.getParameters(null));
        assertThrows(IllegalArgumentException.class, () -> SpotBot.getParameters(List.of("a")));
        assertThrows(IllegalArgumentException.class, () -> SpotBot.getParameters(List.of("a", "ba")));
        assertThrows(IllegalArgumentException.class, () -> SpotBot.getParameters(List.of("-memory", "ada")));
        assertThrows(IllegalArgumentException.class, () -> SpotBot.getParameters(List.of("-" + DISCORD_BOT_TOKEN_FILE_PROPERTY)));
        assertThrows(IllegalArgumentException.class, () -> SpotBot.getParameters(List.of("-" + DISCORD_BOT_TOKEN_FILE_PROPERTY, "-" + DATABASE_URL_PROPERTY, "url")));
        assertThrows(IllegalArgumentException.class, () -> SpotBot.getParameters(List.of("-" + DATABASE_URL_PROPERTY, "")));
        assertThrows(IllegalArgumentException.class, () -> SpotBot.getParameters(List.of("-" + DISCORD_BOT_TOKEN_FILE_PROPERTY, "")));
        assertThrows(IllegalArgumentException.class, () -> SpotBot.getParameters(List.of("-paramUnexpected", "value")));
        assertThrows(IllegalArgumentException.class, () -> SpotBot.getParameters(List.of("-" + DISCORD_BOT_TOKEN_FILE_PROPERTY, "token file", "-paramUnexpected", "value")));
        assertThrows(IllegalArgumentException.class, () -> SpotBot.getParameters(List.of("-paramUnexpected", "value", "-memory")));
        assertThrows(IllegalArgumentException.class, () -> SpotBot.getParameters(List.of("-memory", "-paramUnexpected", "value")));

        String propDatabaseUrl = appProperties.get(DATABASE_URL_PROPERTY);
        String propDiscordTokenFile = appProperties.get(DISCORD_BOT_TOKEN_FILE_PROPERTY);
        int propCheckPeriodMin = appProperties.getIntOr(ALERTS_CHECK_PERIOD_MINUTES_PROPERTY, 15);
        int propHourlySyncDeltaMin = appProperties.getIntOr(ALERTS_HOURLY_SYNC_DELTA_MINUTES_PROPERTY, 3);

        var parameters = SpotBot.getParameters(emptyList());
        assertNotNull(parameters);
        assertEquals(propDatabaseUrl, parameters.databaseUrl());
        assertEquals(propDiscordTokenFile, parameters.discordTokenFile());
        assertEquals(propCheckPeriodMin, parameters.checkPeriodMin());
        assertEquals(propHourlySyncDeltaMin, parameters.hourlySyncDeltaMin());

        parameters = SpotBot.getParameters(List.of("-memory"));
        assertNotNull(parameters);
        assertNull(parameters.databaseUrl());
        assertEquals(propDiscordTokenFile, parameters.discordTokenFile());
        assertEquals(propCheckPeriodMin, parameters.checkPeriodMin());
        assertEquals(propHourlySyncDeltaMin, parameters.hourlySyncDeltaMin());

        parameters = SpotBot.getParameters(List.of("-" + DISCORD_BOT_TOKEN_FILE_PROPERTY, "testtokenfile", "-memory"));
        assertNotNull(parameters);
        assertNull(parameters.databaseUrl());
        assertEquals("testtokenfile", parameters.discordTokenFile());
        assertEquals(propCheckPeriodMin, parameters.checkPeriodMin());
        assertEquals(propHourlySyncDeltaMin, parameters.hourlySyncDeltaMin());

        assertThrows(IllegalArgumentException.class, () -> SpotBot.getParameters(List.of("-" + DATABASE_URL_PROPERTY, "testurl", "-" + ALERTS_CHECK_PERIOD_MINUTES_PROPERTY, "-2", "-" + DISCORD_BOT_TOKEN_FILE_PROPERTY, "testtokenfile")));
        parameters = SpotBot.getParameters(List.of("-" + DATABASE_URL_PROPERTY, "testurl", "-" + ALERTS_CHECK_PERIOD_MINUTES_PROPERTY, "10", "-" + DISCORD_BOT_TOKEN_FILE_PROPERTY, "testtokenfile"));
        assertNotNull(parameters);
        assertEquals("testurl", parameters.databaseUrl());
        assertEquals("testtokenfile", parameters.discordTokenFile());
        assertEquals(10, parameters.checkPeriodMin());
        assertEquals(propHourlySyncDeltaMin, parameters.hourlySyncDeltaMin());

        parameters = SpotBot.getParameters(List.of("-" + ALERTS_CHECK_PERIOD_MINUTES_PROPERTY, "12", "-" + ALERTS_HOURLY_SYNC_DELTA_MINUTES_PROPERTY, "6", "-" + DISCORD_BOT_TOKEN_FILE_PROPERTY, "testtokenfile"));
        assertNotNull(parameters);
        assertEquals(propDatabaseUrl, parameters.databaseUrl());
        assertEquals("testtokenfile", parameters.discordTokenFile());
        assertEquals(12, parameters.checkPeriodMin());
        assertEquals(6, parameters.hourlySyncDeltaMin());

        assertThrows(IllegalArgumentException.class, () -> SpotBot.getParameters(List.of("-" + ALERTS_CHECK_PERIOD_MINUTES_PROPERTY, "0", "-" + ALERTS_HOURLY_SYNC_DELTA_MINUTES_PROPERTY, "-3", "-" + DISCORD_BOT_TOKEN_FILE_PROPERTY, "testtokenfile")));
        assertThrows(IllegalArgumentException.class, () -> SpotBot.getParameters(List.of("-" + ALERTS_CHECK_PERIOD_MINUTES_PROPERTY, "0", "-" + ALERTS_HOURLY_SYNC_DELTA_MINUTES_PROPERTY, "0", "-" + DISCORD_BOT_TOKEN_FILE_PROPERTY, "testtokenfile")));
        parameters = SpotBot.getParameters(List.of("-" + ALERTS_CHECK_PERIOD_MINUTES_PROPERTY, "1", "-" + ALERTS_HOURLY_SYNC_DELTA_MINUTES_PROPERTY, "0", "-" + DISCORD_BOT_TOKEN_FILE_PROPERTY, "testtokenfile"));
        assertNotNull(parameters);
        assertEquals(propDatabaseUrl, parameters.databaseUrl());
        assertEquals("testtokenfile", parameters.discordTokenFile());
        assertEquals(1, parameters.checkPeriodMin());
        assertEquals(0, parameters.hourlySyncDeltaMin());

        assertThrows(IllegalArgumentException.class, () -> SpotBot.getParameters(List.of("-" + ALERTS_CHECK_PERIOD_MINUTES_PROPERTY, "10", "-" + ALERTS_HOURLY_SYNC_DELTA_MINUTES_PROPERTY, "11", "-" + DISCORD_BOT_TOKEN_FILE_PROPERTY, "testtokenfile")));
        parameters = SpotBot.getParameters(List.of("-" + ALERTS_CHECK_PERIOD_MINUTES_PROPERTY, "" + MAX_CHECK_PERIOD, "-" + ALERTS_HOURLY_SYNC_DELTA_MINUTES_PROPERTY, "" + MAX_HOURLY_SYNC_DELTA));
        assertNotNull(parameters);
        assertEquals(propDatabaseUrl, parameters.databaseUrl());
        assertEquals(propDiscordTokenFile, parameters.discordTokenFile());
        assertEquals(MAX_CHECK_PERIOD, parameters.checkPeriodMin());
        assertEquals(MAX_HOURLY_SYNC_DELTA, parameters.hourlySyncDeltaMin());

        assertThrows(IllegalArgumentException.class, () -> SpotBot.getParameters(List.of("-" + ALERTS_CHECK_PERIOD_MINUTES_PROPERTY, "" + (MAX_CHECK_PERIOD + 1), "-" + ALERTS_HOURLY_SYNC_DELTA_MINUTES_PROPERTY, "" + MAX_HOURLY_SYNC_DELTA)));
        assertThrows(IllegalArgumentException.class, () -> SpotBot.getParameters(List.of("-" + ALERTS_CHECK_PERIOD_MINUTES_PROPERTY, "" + MAX_CHECK_PERIOD, "-" + ALERTS_HOURLY_SYNC_DELTA_MINUTES_PROPERTY, "" + (MAX_HOURLY_SYNC_DELTA + 1))));
    }

    @Test
    void consumeParameter() {
        assertThrows(NullPointerException.class, () -> SpotBot.consumeParameter(null, "name", "default", Function.identity()));
        assertThrows(NullPointerException.class, () -> SpotBot.consumeParameter(emptyList(), null, "default", Function.identity()));
        assertThrows(NullPointerException.class, () -> SpotBot.consumeParameter(emptyList(), "name", "default", null));

        assertThrows(IllegalArgumentException.class, () -> SpotBot.consumeParameter(new ArrayList<>(List.of("-param", "-param2", "value2")), "param", "default", Function.identity()));
        assertThrows(UnsupportedOperationException.class, () -> SpotBot.consumeParameter(List.of("-param", "value", "-param2", "value2"), "param", "default", Function.identity()));

        assertNull(SpotBot.consumeParameter(emptyList(), "param", null, Function.identity()));

        var args = new ArrayList<>(List.of("-param", "value", "-param2", "value2"));

        assertEquals("value", SpotBot.consumeParameter(args, "param", "default", Function.identity()));
        assertEquals(List.of("-param2", "value2"), args);
        args = new ArrayList<>(List.of("-param", "value", "-param2", "value2"));
        assertEquals("VALUE", SpotBot.consumeParameter(args, "param", "default", String::toUpperCase));
        assertEquals(List.of("-param2", "value2"), args);
        assertEquals("value2", SpotBot.consumeParameter(args, "param2", "default", Function.identity()));
        assertTrue(args.isEmpty());
        args = new ArrayList<>(List.of("-param", "value", "-param2", "value2"));
        assertEquals("VALUE2", SpotBot.consumeParameter(args, "param2", "default", String::toUpperCase));
        assertEquals(List.of("-param", "value"), args);
        assertEquals("default", SpotBot.consumeParameter(args, "paramNotHere", "default", String::toUpperCase));
    }

    @Test
    void help() {
        assertTrue(SpotBot.help(true).contains(SpotBot.VERSION));
        assertFalse(SpotBot.help(false).contains(SpotBot.VERSION));
        assertTrue(SpotBot.help(true).contains(SpotBot.DATABASE_URL_PROPERTY));
        assertTrue(SpotBot.help(false).contains(SpotBot.DATABASE_URL_PROPERTY));
        assertTrue(SpotBot.help(true).contains(SpotBot.DISCORD_BOT_TOKEN_FILE_PROPERTY));
        assertTrue(SpotBot.help(false).contains(SpotBot.DISCORD_BOT_TOKEN_FILE_PROPERTY));
        assertTrue(SpotBot.help(true).contains(SpotBot.ALERTS_CHECK_PERIOD_MINUTES_PROPERTY));
        assertTrue(SpotBot.help(false).contains(SpotBot.ALERTS_CHECK_PERIOD_MINUTES_PROPERTY));
        assertTrue(SpotBot.help(true).contains(SpotBot.ALERTS_HOURLY_SYNC_DELTA_MINUTES_PROPERTY));
        assertTrue(SpotBot.help(false).contains(SpotBot.ALERTS_HOURLY_SYNC_DELTA_MINUTES_PROPERTY));
    }

    @Test
    void schedulingPlan() {
        assertThrows(NullPointerException.class, () -> SpotBot.schedulingPlan(null, 1, 1));

        //new hour
        ZonedDateTime date = Dates.parseLocalDateTime(Locale.UK, "12/12/2012-10:00").atZone(UTC);
        assertEquals(0, date.getMinute());
        assertTrue(SpotBot.schedulingPlan(date, 1, 0).toString().startsWith("First hour : now, 10h01, 10h02, 10h03, 10h04, 10h05, 10h06, 10h07"));
        assertTrue(SpotBot.schedulingPlan(date, 1, 0).toString().contains("10h59. Following hour : 11h00, 11h01, 11h02"));
        assertTrue(SpotBot.schedulingPlan(date, 1, 0).toString().endsWith("11h58, 11h59"));

        assertTrue(SpotBot.schedulingPlan(date, 1, 1).toString().startsWith("First hour : now, 10h01, 10h02, 10h03, 10h04, 10h05, 10h06, 10h07"));
        assertTrue(SpotBot.schedulingPlan(date, 1, 1).toString().contains("10h59. Following hour : 11h01, 11h02, 11h03"));
        assertTrue(SpotBot.schedulingPlan(date, 1, 1).toString().endsWith("11h58, 11h59"));

        assertTrue(SpotBot.schedulingPlan(date, 10, 1).toString().startsWith("First hour : now, 10h01, 10h11, 10h21, 10h31"));
        assertTrue(SpotBot.schedulingPlan(date, 10, 1).toString().contains("10h51. Following hour : 11h01, 11h11"));
        assertTrue(SpotBot.schedulingPlan(date, 10, 1).toString().endsWith("11h51"));

        assertTrue(SpotBot.schedulingPlan(date, 10, 7).toString().startsWith("First hour : now, 10h07, 10h17, 10h27"));
        assertTrue(SpotBot.schedulingPlan(date, 10, 7).toString().contains("10h57. Following hour : 11h07, 11h17"));
        assertTrue(SpotBot.schedulingPlan(date, 10, 7).toString().endsWith("11h57"));

        //new hour after hourlySyncDeltaMin, wait until checkPeriodMin or next hour + hourlySyncDeltaMin, whichever comes first
        var hourlySyncDeltaMin = 7;
        date = date.plusMinutes(hourlySyncDeltaMin);
        assertTrue(SpotBot.schedulingPlan(date, 1, hourlySyncDeltaMin).toString().startsWith("First hour : now, 10h08, 10h09, 10h10, 10h11"));
        assertTrue(SpotBot.schedulingPlan(date, 1, hourlySyncDeltaMin).toString().contains("10h59. Following hour : 11h07, 11h08"));
        assertTrue(SpotBot.schedulingPlan(date, 1, hourlySyncDeltaMin).toString().endsWith("11h58, 11h59"));

        assertTrue(SpotBot.schedulingPlan(date, 10, hourlySyncDeltaMin).toString().startsWith("First hour : now, 10h17, 10h27"));
        assertTrue(SpotBot.schedulingPlan(date, 10, hourlySyncDeltaMin).toString().contains("10h57. Following hour : 11h07, 11h17"));
        assertTrue(SpotBot.schedulingPlan(date, 10, hourlySyncDeltaMin).toString().endsWith("11h57"));

        assertEquals("First hour : now, 10h57. Following hour : 11h07, 11h57", SpotBot.schedulingPlan(date, 50, hourlySyncDeltaMin).toString());
        assertEquals("First hour : now. Following hour : 11h07", SpotBot.schedulingPlan(date, 60, hourlySyncDeltaMin).toString());
        assertEquals("First hour : now. Following hour : 11h07", SpotBot.schedulingPlan(date, 69, hourlySyncDeltaMin).toString());
        assertEquals("First hour : now. Following hour : 11h07", SpotBot.schedulingPlan(date, 266, hourlySyncDeltaMin).toString());

        hourlySyncDeltaMin = 3;
        assertEquals("First hour : now. Following hour : 11h03, 11h59", SpotBot.schedulingPlan(date, 56, hourlySyncDeltaMin).toString());
        assertEquals("First hour : now. Following hour : 11h03", SpotBot.schedulingPlan(date, 166, hourlySyncDeltaMin).toString());

        date = date.plusMinutes(30 - 7);
        assertEquals(30, date.getMinute());
        assertTrue(SpotBot.schedulingPlan(date, 1, hourlySyncDeltaMin).toString().startsWith("First hour : now, 10h31, 10h32"));
        assertTrue(SpotBot.schedulingPlan(date, 1, hourlySyncDeltaMin).toString().contains("10h59. Following hour : 11h03, 11h04"));
        assertTrue(SpotBot.schedulingPlan(date, 1, hourlySyncDeltaMin).toString().endsWith("11h58, 11h59"));

        assertTrue(SpotBot.schedulingPlan(date, 10, hourlySyncDeltaMin).toString().startsWith("First hour : now, 10h40, 10h50"));
        assertTrue(SpotBot.schedulingPlan(date, 10, hourlySyncDeltaMin).toString().contains("10h50. Following hour : 11h03, 11h13"));
        assertTrue(SpotBot.schedulingPlan(date, 10, hourlySyncDeltaMin).toString().endsWith("11h53"));

        assertEquals("First hour : now. Following hour : 11h03, 11h33", SpotBot.schedulingPlan(date, 30, hourlySyncDeltaMin).toString());
        assertEquals("First hour : now. Following hour : 11h03, 11h36", SpotBot.schedulingPlan(date, 33, hourlySyncDeltaMin).toString());
        assertEquals("First hour : now. Following hour : 11h03", SpotBot.schedulingPlan(date, 334, hourlySyncDeltaMin).toString());

        date = date.plusMinutes(19);
        assertEquals(7 * 7, date.getMinute());
        assertTrue(SpotBot.schedulingPlan(date, 1, hourlySyncDeltaMin).toString().startsWith("First hour : now, 10h50, 10h51"));
        assertTrue(SpotBot.schedulingPlan(date, 1, hourlySyncDeltaMin).toString().contains("10h59. Following hour : 11h03, 11h04"));
        assertTrue(SpotBot.schedulingPlan(date, 1, hourlySyncDeltaMin).toString().endsWith("11h58, 11h59"));

        assertTrue(SpotBot.schedulingPlan(date, 10, hourlySyncDeltaMin).toString().startsWith("First hour : now, 10h59. Following hour : 11h03, 11h13"));
        assertTrue(SpotBot.schedulingPlan(date, 10, hourlySyncDeltaMin).toString().endsWith("11h43, 11h53"));

        assertTrue(SpotBot.schedulingPlan(date, 14, hourlySyncDeltaMin).toString().startsWith("First hour : now. Following hour : 11h03, 11h17"));
        assertTrue(SpotBot.schedulingPlan(date, 14, hourlySyncDeltaMin).toString().endsWith("11h45, 11h59"));

        assertEquals("First hour : now. Following hour : 11h03, 11h36", SpotBot.schedulingPlan(date, 33, hourlySyncDeltaMin).toString());

        date = date.plusMinutes(7);
        assertEquals(56, date.getMinute());
        assertTrue(SpotBot.schedulingPlan(date, 1, hourlySyncDeltaMin).toString().startsWith("First hour : now, 10h57, 10h58, 10h59. Following hour : 11h03, 11h04"));
        assertTrue(SpotBot.schedulingPlan(date, 1, hourlySyncDeltaMin).toString().endsWith("11h59"));

        assertTrue(SpotBot.schedulingPlan(date, 4, hourlySyncDeltaMin).toString().startsWith("First hour : now. Following hour : 11h03, 11h07"));
        assertTrue(SpotBot.schedulingPlan(date, 4, hourlySyncDeltaMin).toString().endsWith("11h59"));

        assertTrue(SpotBot.schedulingPlan(date, 7, hourlySyncDeltaMin).toString().startsWith("First hour : now. Following hour : 11h03, 11h10"));
        assertTrue(SpotBot.schedulingPlan(date, 7, hourlySyncDeltaMin).toString().endsWith("11h59"));

        assertTrue(SpotBot.schedulingPlan(date, 14, hourlySyncDeltaMin).toString().startsWith("First hour : now. Following hour : 11h03, 11h17"));
        assertTrue(SpotBot.schedulingPlan(date, 14, hourlySyncDeltaMin).toString().endsWith("11h59"));

        assertEquals("First hour : now. Following hour : 11h03, 11h36", SpotBot.schedulingPlan(date, 33, hourlySyncDeltaMin).toString());
    }

    @Test
    void minutesUntilNextCheck() {
        assertThrows(NullPointerException.class, () -> SpotBot.minutesUntilNextCheck(null, 1, 1));

        //new hour, wait until hourlySyncDeltaMin
        ZonedDateTime now = DatesTest.nowUtc().truncatedTo(HOURS);
        assertEquals(0, now.getMinute());
        assertEquals(1, SpotBot.minutesUntilNextCheck(now, 1, 1));
        assertEquals(1, SpotBot.minutesUntilNextCheck(now, 10, 1));
        assertEquals(2, SpotBot.minutesUntilNextCheck(now, 10, 2));
        assertEquals(7, SpotBot.minutesUntilNextCheck(now, 10, 7));
        assertEquals(11, SpotBot.minutesUntilNextCheck(now, 10, 11));

        //new hour after hourlySyncDeltaMin, wait until checkPeriodMin or next hour + hourlySyncDeltaMin, whichever comes first
        var hourlySyncDeltaMin = 7;
        now = now.plusMinutes(hourlySyncDeltaMin);
        assertEquals(1, SpotBot.minutesUntilNextCheck(now, 1, hourlySyncDeltaMin));
        assertEquals(10, SpotBot.minutesUntilNextCheck(now, 10, hourlySyncDeltaMin));
        assertEquals(50, SpotBot.minutesUntilNextCheck(now, 50, hourlySyncDeltaMin));
        assertEquals(60, SpotBot.minutesUntilNextCheck(now, 60, hourlySyncDeltaMin));
        assertEquals(60, SpotBot.minutesUntilNextCheck(now, 61, hourlySyncDeltaMin));
        assertEquals(60, SpotBot.minutesUntilNextCheck(now, 62, hourlySyncDeltaMin));
        assertEquals(60, SpotBot.minutesUntilNextCheck(now, 69, hourlySyncDeltaMin));
        assertEquals(60, SpotBot.minutesUntilNextCheck(now, 266, hourlySyncDeltaMin));

        hourlySyncDeltaMin = 3;
        assertEquals(56, SpotBot.minutesUntilNextCheck(now, 166, hourlySyncDeltaMin));
        now = now.plusMinutes(30 - 7);
        assertEquals(30, now.getMinute());
        assertEquals(1, SpotBot.minutesUntilNextCheck(now, 1, hourlySyncDeltaMin));
        assertEquals(10, SpotBot.minutesUntilNextCheck(now, 10, hourlySyncDeltaMin));
        assertEquals(30 + hourlySyncDeltaMin, SpotBot.minutesUntilNextCheck(now, 30, hourlySyncDeltaMin));
        assertEquals(33, SpotBot.minutesUntilNextCheck(now, 33, hourlySyncDeltaMin));
        assertEquals(33, SpotBot.minutesUntilNextCheck(now, 34, hourlySyncDeltaMin));
        assertEquals(33, SpotBot.minutesUntilNextCheck(now, 334, hourlySyncDeltaMin));

        now = now.plusMinutes(19);
        assertEquals(7 * 7, now.getMinute());
        assertEquals(1, SpotBot.minutesUntilNextCheck(now, 1, hourlySyncDeltaMin));
        assertEquals(10, SpotBot.minutesUntilNextCheck(now, 10, hourlySyncDeltaMin));
        assertEquals(14, SpotBot.minutesUntilNextCheck(now, 14, hourlySyncDeltaMin));
        assertEquals(14, SpotBot.minutesUntilNextCheck(now, 33, hourlySyncDeltaMin));

        now = now.plusMinutes(7);
        assertEquals(56, now.getMinute());
        assertEquals(1, SpotBot.minutesUntilNextCheck(now, 1, hourlySyncDeltaMin));
        assertEquals(4 + hourlySyncDeltaMin, SpotBot.minutesUntilNextCheck(now, 4, hourlySyncDeltaMin));
        assertEquals(7, SpotBot.minutesUntilNextCheck(now, 7, hourlySyncDeltaMin));
        assertEquals(7, SpotBot.minutesUntilNextCheck(now, 14, hourlySyncDeltaMin));
        assertEquals(7, SpotBot.minutesUntilNextCheck(now, 33, hourlySyncDeltaMin));
    }
}