package org.sbot.entities;

import net.dv8tion.jda.api.EmbedBuilder;
import org.junit.jupiter.api.Test;
import org.sbot.entities.Message.File;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MessageTest {

    @Test
    void withRoleUser() {
        Message message = Message.of(new EmbedBuilder());
        assertEquals(List.of("role"), message.withRoleUser("role", null).mentionRoles());
        assertEquals(List.of("123"), message.withRoleUser(null, 123L).mentionUsers());
    }

    @Test
    void file() {
        File file = File.of("name", new byte[] {1});
        assertEquals("File{name='name'}", file.toString());
        assertEquals(file, File.of(file.name(), new byte[0]));
        assertEquals(file.hashCode(), File.of(file.name(), new byte[0]).hashCode());
        assertNotEquals(file, File.of("1" + file.name(), new byte[0]));
    }
}