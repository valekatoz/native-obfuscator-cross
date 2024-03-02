package dev.lennoxlotl.obfuscator.log;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration class for tinylog.
 */
@Getter
public class TinyLogSettings {
    private final Map<String, String> settings = new HashMap<>();

    public TinyLogSettings setWriter(String writerName, String writer) {
        settings.put("writer" + writerName, writer);
        return this;
    }

    public TinyLogSettings setLevel(String writerName, String level) {
        settings.put("writer" + writerName + "." + "level", level);
        return this;
    }

    public TinyLogSettings setEnableWritingThread(boolean enable) {
        settings.put("writingthread", String.valueOf(enable));
        return this;
    }

    public TinyLogSettings setEnableAutoShutdown(boolean autoShutdown) {
        settings.put("autoshutdown", String.valueOf(autoShutdown));
        return this;
    }
}