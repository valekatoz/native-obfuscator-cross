package dev.lennoxlotl.obfuscator.log;

import org.tinylog.Level;
import org.tinylog.core.LogEntry;
import org.tinylog.core.LogEntryValue;
import org.tinylog.writers.AbstractFormatPatternWriter;

import java.io.PrintStream;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;

public class ObfuscatorConsoleWriter extends AbstractFormatPatternWriter {
    private final boolean swingConsole;

    public ObfuscatorConsoleWriter(Map<String, String> properties) {
        this(properties, false);
    }

    protected ObfuscatorConsoleWriter(Map<String, String> properties, boolean swingConsole) {
        super(properties);
        this.swingConsole = swingConsole;
    }

    @Override
    public Collection<LogEntryValue> getRequiredLogEntryValues() {
        return EnumSet.of(LogEntryValue.LEVEL, LogEntryValue.MESSAGE);
    }

    @Override
    public void write(LogEntry logEntry) {
        if (logEntry.getTag() != null)
            return;

        final String message = logEntry.getMessage();
        final StringBuilder builder = new StringBuilder();

        switch (logEntry.getLevel()) {
            case DEBUG -> builder.append("[DEBUG] ").append(message);

            case WARN -> builder.append(swingConsole ? "[WARNING] " : "\u001B[33m[WARNING]\u001B[0m ")
                .append(message).append("\u001B[0m");

            case ERROR -> builder.append(swingConsole ? "[ERROR] " : "\u001B[31m[ERROR]\u001B[0m ")
                .append(message).append("\u001B[0m");

            default -> builder.append(swingConsole ? "[INFO] " : "\u001B[32m[INFO]\u001B[0m ")
                .append(message).append("\u001B[0m");
        }

        builder.append("\n");
        this.getPrintStream(logEntry.getLevel()).print(builder);
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }

    private PrintStream getPrintStream(Level level) {
        if (level == Level.ERROR) {
            return System.err;
        } else {
            return System.out;
        }
    }
}
