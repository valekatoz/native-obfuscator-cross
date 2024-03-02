package dev.lennoxlotl.obfuscator.log;

import org.tinylog.configuration.Configuration;

/**
 * Loads the custom tinylog configuration.
 * <p>
 * We want to use ANSI colors, so we cannot use config files.
 */
public class TinyLogConfiguration {
    public static void configure() {
        TinyLogSettings settings = new TinyLogSettings();

        settings.setWriter("console", ObfuscatorConsoleWriter.class.getName())
            .setLevel("console", "info")
            .setEnableAutoShutdown(true)
            .setEnableWritingThread(true);

        Configuration.replace(settings.getSettings());
    }
}
