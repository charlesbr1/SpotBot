package org.sbot.commands.interactions;

import org.jetbrains.annotations.NotNull;
import org.sbot.services.discord.InteractionListener;

import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.sbot.utils.ArgumentValidator.requireNotBlank;

public interface Interactions {

    String INTERACTION_ID_SEPARATOR = "#";

    @NotNull
    static String interactionId(@NotNull String componentId, long alertId) {
        return requireNonNull(componentId) + INTERACTION_ID_SEPARATOR + alertId;
    }

    @NotNull
    static String componentIdOf(@NotNull String interactionId) {
        int index = interactionId.indexOf(INTERACTION_ID_SEPARATOR);
        if(index > 0) {
            return requireNotBlank(interactionId.substring(0, index), "componentId").strip();
        }
        throw new IllegalArgumentException("Invalid interactionId : " + interactionId);
    }

    @NotNull
    static String alertIdOf(@NotNull String interactionId) {
        int index = interactionId.indexOf(INTERACTION_ID_SEPARATOR);
        if(index > 0) {
            return requireNotBlank(interactionId.substring(index + 1), "alertId").strip();
        }
        throw new IllegalArgumentException("Invalid interactionId : " + interactionId);
    }

    // Register new interactions here
    List<InteractionListener> SPOTBOT_INTERACTIONS = List.of(
            new SelectEditInteraction(),
            new ModalEditInteraction());
}
