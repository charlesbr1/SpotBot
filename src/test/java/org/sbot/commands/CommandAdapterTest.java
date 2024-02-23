package org.sbot.commands;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommandAdapterTest {

    static void assertExceptionContains(@NotNull Class<? extends  Exception> type, @NotNull String message, @NotNull Runnable runnable) {
        try {
            runnable.run();
            fail("Expected exception " + type.getName() + " to be thrown with message : " + message);
        } catch (Exception e) {
            if(type.isAssignableFrom(e.getClass())) {
                assertTrue(e.getMessage().contains(message), "Exception " + type.getName() + " thrown, expected message : " + message + ", actual : " + e.getMessage());
            } else {
                fail("Expected exception " + type.getName() + " to be thrown with message : " + message + ", but got exception " + e);
            }
        }
    }

    @Test
    void name() {
    }

    @Test
    void description() {
    }

    @Test
    void options() {
    }

    @Test
    void option() {
    }

    @Test
    void securedAlertAccess() {
    }

    @Test
    void isPrivateChannel() {
    }

    @Test
    void embedBuilder() {
    }

    @Test
    void testEmbedBuilder() {
    }

    @Test
    void saveAlert() {
    }

    @Test
    void userSetupNeeded() {
    }

    @Test
    void alertMessageTips() {
    }

    @Test
    void sendUpdateNotification() {
    }
}