package org.sbot.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.sbot.utils.ArgumentReader;
import org.sbot.utils.PropertiesReader;

import javax.annotation.Nonnull;
import javax.security.auth.login.LoginException;
import javax.swing.text.html.Option;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.Boolean.parseBoolean;
import static org.sbot.utils.PropertiesReader.loadProperties;
import static org.sbot.utils.PropertiesReader.readFile;

public enum Discord {
    ;

    private static final Logger LOGGER = LogManager.getLogger(Discord.class);

    private static final PropertiesReader properties = loadProperties("discord.properties");

    private static final String DISCORD_BOT_TOKEN_FILE = "discord.token";
    private static final String DISCORD_SERVER_ID = properties.get("discord.server.id");
    private static final String DISCORD_SERVER_CHANNEL = properties.get("discord.server.channel");


    // replace discord server by a logger if disabled (unit test env)
    public static final SpotBotChannel spotBotChannel = parseBoolean(properties.get("discord.enabled")) ?
            SpotBotChannel.spotBotChannel(initConnection()) :
            message -> LOGGER.info("Discord disabled, message: {}", message);
    private static final Map<String, CommandListener> commands = new ConcurrentHashMap<>();

    @FunctionalInterface
    public interface SpotBotChannel {
        void sendMessage(String message);

        private static SpotBotChannel spotBotChannel(TextChannel channel) {
            return message -> Discord.sendMessage(channel, message);
        }
    }

    public static void sendMessage(MessageChannel channel, String message) {
        //TODO check doc MessageAction queue()
        channel.sendMessage(message).queue();
        LOGGER.debug("Discord message sent: {}", message);
    }

    private static TextChannel initConnection() {
        try {
            LOGGER.info("Loading discord connection...");
            return loadChannel();
        } catch (LoginException | RuntimeException | InterruptedException e) {
            LOGGER.error("Failed to load discord connection");
            throw e instanceof RuntimeException ? (RuntimeException)e : new IllegalStateException(e);
        }
    }

    private static TextChannel loadChannel() throws LoginException, InterruptedException {
        return getDiscordServer()
                .orElseThrow(() -> new IllegalArgumentException("Discord server " + DISCORD_SERVER_ID + " not found"))
                .getTextChannelsByName(DISCORD_SERVER_CHANNEL, true)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("channel " + DISCORD_SERVER_CHANNEL + " not found on discord server " + DISCORD_SERVER_ID));
    }

    private static Optional<Guild> getDiscordServer() throws LoginException, InterruptedException {
        JDA jda =
                JDABuilder.createDefault(readFile(DISCORD_BOT_TOKEN_FILE, 128))
                        .setActivity(Activity.listening("commands"))
                        .enableIntents(GatewayIntent.GUILD_MESSAGES) // enables explicit access to message.getContentDisplay()
                        .addEventListeners(new EventAdapter())
                .build().awaitReady();

        // test
        jda.updateCommands().addCommands(
                Commands.slash("ping", "Calculate ping of the bot"),
                Commands.slash("ban", "Ban a user from the server")
//                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS)) // only usable with ban permissions
  //                      .setGuildOnly(true) // Ban command only works inside a guild
                        .addOption(OptionType.USER, "user", "The user to ban", true) // required option of type user (target to ban)
                        .addOption(OptionType.STRING, "reason", "The ban reason") // optional reason
        ).queue();
        return Optional.ofNullable(jda.getGuildById(DISCORD_SERVER_ID));
    }

    private static final class EventAdapter extends ListenerAdapter {
        @Override
        public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
            LOGGER.debug("Discord command received: {}", event);
        }
        public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
            LOGGER.debug("Discord message received: {}", event.getMessage().getContentRaw());
            // TODO need to filter on channel ?
            ArgumentReader argumentReader = new ArgumentReader(event.getMessage().getContentRaw().trim());
            try {
                argumentReader.getNextString()
                        .map(commands::get)
                        .ifPresent(listener -> listener.onEvent(argumentReader, event));
            } catch (RuntimeException e) {
                LOGGER.warn("Error while processing discord command: " + event.getMessage().getContentRaw(), e);
                sendMessage(event.getChannel(), "Execution failure with error: " + e.getMessage());
            }
        }
    }

    public static void registerCommands(CommandListener... commandListeners) {
        Optional.ofNullable(commandListeners).stream().flatMap(Arrays::stream)
                .forEach(Discord::registerCommand);
    }

    public static void registerCommand(CommandListener commandListener) {
        LOGGER.info("Registering discord command: {}", commandListener.operator());
        if(null != commands.put(commandListener.operator(), commandListener)) {
            LOGGER.debug("Discord command {} was already registered", commandListener.operator());
        }
    }
}
