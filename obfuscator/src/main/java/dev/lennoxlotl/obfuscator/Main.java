package dev.lennoxlotl.obfuscator;

import dev.lennoxlotl.obfuscator.config.ObfuscatorConfig;
import dev.lennoxlotl.obfuscator.log.TinyLogConfiguration;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        TinyLogConfiguration.configure();
        ObfuscatorConfig config = ObfuscatorConfig.load();
        new NativeObfuscator().process(config);
    }
}
