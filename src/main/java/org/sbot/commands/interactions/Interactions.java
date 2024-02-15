package org.sbot.commands.interactions;

import org.sbot.services.discord.InteractionListener;

import java.util.List;

public interface Interactions {

    // Register new interactions here
    List<InteractionListener> SPOTBOT_INTERACTIONS = List.of(
            new SelectEditInteraction(),
            new ModalEditInteraction());
}
