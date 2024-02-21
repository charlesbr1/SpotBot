package org.sbot.commands;

import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.Message;
import org.sbot.entities.User;
import org.sbot.utils.ArgumentValidator;

import java.awt.Color;
import java.util.Locale;
import java.util.stream.Stream;

import static net.dv8tion.jda.api.interactions.commands.OptionType.STRING;
import static net.dv8tion.jda.api.interactions.commands.build.OptionData.MAX_CHOICES;
import static org.sbot.entities.User.DEFAULT_LOCALE;

public class LocaleCommand extends CommandAdapter {

    private static final String NAME = "locale";
    static final String DESCRIPTION = "update your locale to change the language";
    private static final int RESPONSE_TTL_SECONDS = 10;

    private static final SlashCommandData options = Commands.slash(NAME, DESCRIPTION).addOptions(
            option(STRING, "locale", "the discord locale", false)
                    .addChoices(Stream.of(DiscordLocale.values()).limit(MAX_CHOICES)
                            .map(DiscordLocale::getLocale).map(e -> new Choice(e, e)).toList()));

    public LocaleCommand() {
        super(NAME, DESCRIPTION, options, RESPONSE_TTL_SECONDS);
    }

    @Override
    public void onCommand(@NotNull CommandContext context) {
        var locale = context.args.getString("locale")
                .map(ArgumentValidator::requireSupportedLocale)
                .map(Locale::forLanguageTag).orElse(null);
        LOGGER.debug("locale command - locale : {}", locale);
        context.noMoreArgs().reply(locale(context, locale), responseTtlSeconds);
    }

    private Message locale(@NotNull CommandContext context, @Nullable Locale locale) {
        if(null == locale) {
            return Message.of(embedBuilder(NAME, Color.green,
                    "Your current locale is : " + context.transactional(txCtx -> txCtx.usersDao()
                            .getUser(context.user.getIdLong())
                            .map(User::locale).orElse(DEFAULT_LOCALE)
                            .toLanguageTag())));
        }
        context.transaction(txCtx -> txCtx.usersDao().updateLocale(context.user.getIdLong(), locale));
        return Message.of(embedBuilder(NAME, Color.green, "Locale set to " + locale.toLanguageTag()));
    }
}
