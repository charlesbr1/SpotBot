package org.sbot.entities;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

public record Message(@NotNull List<EmbedBuilder> embeds,
                      @Nullable Collection<String> mentionRoles,
                      @Nullable Collection<String> mentionUsers,
                      @Nullable List<File> files,
                      @Nullable List<? extends LayoutComponent> component,
                      @Nullable Modal modal,
                      @Nullable Function<MessageEditBuilder, MessageEditData> editMapper) {

    public static Message of(@NotNull EmbedBuilder embedBuilder) {
        return of(List.of(embedBuilder));
    }

    public static Message of(@NotNull List<EmbedBuilder> embedBuilders) {
        return new Message(requireNonNull(embedBuilders), null, null, null, null, null, null);
    }

    public static Message of(@NotNull List<EmbedBuilder> embedBuilders, @NotNull List<String> roles, @NotNull List<String> users) {
        return new Message(embedBuilders, requireNonNull(roles), requireNonNull(users), null, null, null, null);
    }

    public static Message of(@NotNull EmbedBuilder embedBuilder, @NotNull File file) {
        return new Message(List.of(embedBuilder), null, null, List.of(file), null, null, null);
    }

    public static Message of(@NotNull EmbedBuilder embedBuilder, @NotNull LayoutComponent component) {
        return new Message(List.of(embedBuilder), null, null, null, List.of(component), null, null);
    }

    public static Message of(@NotNull Modal modal) {
        return new Message(emptyList(), null, null, null, null, modal, null);
    }

    public static Message of(@NotNull Function<MessageEditBuilder, MessageEditData> editMapper) {
        return new Message(emptyList(), null, null, null, null, null, editMapper);
    }


    public record File(@NotNull byte[] content, @NotNull String name) {
        public static File of(@NotNull byte[] content, @NotNull String name) {
            return new File(requireNonNull(content), requireNonNull(name));
        }
    }
}
