package org.sbot.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.Compression;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sbot.utils.ArgumentReader;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.sbot.utils.PropertiesReader.readFile;

public final class Discord {

    private static final Logger LOGGER = LogManager.getLogger(Discord.class);


    private static final String DISCORD_BOT_TOKEN_FILE = "discord.token";


    private final JDA jda;
    private final String discordBotChannel;
    public final SpotBotChannel spotBotChannel;
    private final Map<String, DiscordCommand> commands = new ConcurrentHashMap<>();

    public Discord(String discordServerId, String discordBotChannel) {
        jda = loadDiscordConnection();
        this.discordBotChannel = discordBotChannel;
        var channel = loadDiscordChannel(loadDiscordServer(discordServerId));
        spotBotChannel = message -> Discord.sendMessage(channel, message);
    }

    @FunctionalInterface
    public interface SpotBotChannel {
        void sendMessage(String message);
    }

    private static void sendMessage(TextChannel channel, String message) {
        channel.sendMessage(message).queue();
        LOGGER.debug("Discord message sent: {}", message);
    }

    private JDA loadDiscordConnection() {
        try {
            LOGGER.info("Loading discord connection...");
            return JDABuilder.createLight(readFile(DISCORD_BOT_TOKEN_FILE),
                            GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                    .setActivity(Activity.watching("prices"))
                    .addEventListeners(new EventAdapter())
                    .setCompression(Compression.ZLIB)
                    .setAutoReconnect(true)
                    .build()
                    .awaitReady();
        } catch (Exception e) {
            LOGGER.error("Unable to establish discord connection");
            throw new IllegalStateException(e);
        }
    }

    private Guild loadDiscordServer(String discordServerId) {
        LOGGER.info("Connection to discord server {}...", discordServerId);
        return Optional.ofNullable(jda.getGuildById(discordServerId))
                .orElseThrow(() -> new IllegalStateException("Failed to load discord server " + discordServerId));
    }

    private TextChannel loadDiscordChannel(Guild discordServer) {
        return discordServer.getTextChannelsByName(discordBotChannel, true)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("channel " + discordBotChannel + " not found"));
    }


    public void registerCommands(DiscordCommand... discordCommands) {
        synchronized (commands) {
            commands.clear();
            var commandDescriptions = Optional.ofNullable(discordCommands).stream().flatMap(Arrays::stream)
                    .filter(this::registerCommand)
                    .map(this::getOptions).toList();
            // this call replace previous content, that's why commands was clear
            jda.updateCommands().addCommands(commandDescriptions).queue();
        }
    }

    public boolean registerCommand(DiscordCommand discordCommand) {
        LOGGER.info("Registering discord command: {}", discordCommand.name());
        if(null != commands.put(discordCommand.name(), discordCommand)) {
            LOGGER.debug("Discord command {} was already registered", discordCommand.name());
            return false;
        }
        return true;
    }

    public CommandData getOptions(DiscordCommand discordCommand) {
        return Commands.slash(discordCommand.name(), discordCommand.description())
//                .setDefaultPermissions(...)
                .addOptions(discordCommand.options())
                .setGuildOnly(true);
    }

    private final class EventAdapter extends ListenerAdapter {

        @Override
        public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
            LOGGER.debug("Discord slash command received: {}", event);
//            if (discordBotChannel.equalsIgnoreCase(event.getChannel().getName())) {
                Optional.ofNullable(commands.get(event.getName()))
                        .ifPresent(listener -> listener.onEvent(event));
//            }
        }

        @Override
        public void onMessageReceived(MessageReceivedEvent event) {
            LOGGER.debug("Discord message received: {}", event.getMessage().getContentRaw());
            if (discordBotChannel.equalsIgnoreCase(event.getChannel().getName()) || event.isFromType(ChannelType.PRIVATE)) {
                ArgumentReader argumentReader = new ArgumentReader(event.getMessage().getContentRaw().trim());
                try {
                    argumentReader.getNextString()
                            .filter(command -> command.startsWith("!"))
                            .map(command -> command.replaceFirst("!", ""))
                            .map(commands::get)
                            .ifPresent(listener -> listener.onEvent(argumentReader, event));
                } catch (RuntimeException e) {
                    LOGGER.warn("Error while processing discord command: " + event.getMessage().getContentRaw(), e);
                    sendMessage(event.getChannel().asTextChannel(), "Execution failure with error: " + e.getMessage());
                }
            }
        }
    }
}
