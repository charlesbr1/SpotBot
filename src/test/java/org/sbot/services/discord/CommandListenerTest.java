package org.sbot.services.discord;

import org.junit.jupiter.api.Test;
import org.sbot.commands.DeleteCommand;
import org.sbot.commands.SpotBotCommand;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandListenerTest {

    @Test
    void optionsDescription() {
        assertEquals("\n*parameter :*\n\n" +
                "- **selection** (_string, optional_) which help to show : 'doc' or 'commands', default to 'doc' if omitted",
                CommandListener.optionsDescription(new SpotBotCommand().options(), true));
        assertEquals("*parameter :*\n\n" +
                "- **selection** (_string, optional_) which help to show : 'doc' or 'commands', default to 'doc' if omitted",
                CommandListener.optionsDescription(new SpotBotCommand().options(), false));

        assertEquals("\n**id** : _delete an alert by id_\n" +
                        "\n" +
                        "- **alert_id** (_integer_) id of the alert to delete\n" +
                        "\n" +
                        "\n" +
                        "**filter** : _delete all your alerts or filtered by pair or ticker or type_\n" +
                        "\n" +
                        "- **ticker_pair** (_string_) a pair or a ticker to filter the alerts to delete (can be 'all')\n" +
                        "- **type** (_string, optional_) type of alert to delete (range, trend or remainder)\n" +
                        "- **owner** (_user, optional_) for admin only, an user whose alerts will be deleted",
                CommandListener.optionsDescription(new DeleteCommand().options(), true));

        assertEquals("> _delete an alert by id_\n" +
                        "\n" +
                        "- **alert_id** (_integer_) id of the alert to delete\n" +
                        "\n" +
                        "> _delete all your alerts or filtered by pair or ticker or type_\n" +
                        "\n" +
                        "- **ticker_pair** (_string_) a pair or a ticker to filter the alerts to delete (can be 'all')\n" +
                        "- **type** (_string, optional_) type of alert to delete (range, trend or remainder)\n" +
                        "- **owner** (_user, optional_) for admin only, an user whose alerts will be deleted",
                CommandListener.optionsDescription(new DeleteCommand().options(), false));
    }
}