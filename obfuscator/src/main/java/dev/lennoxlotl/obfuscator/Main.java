package dev.lennoxlotl.obfuscator;

import dev.lennoxlotl.obfuscator.config.ObfuscatorConfig;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

public class Main {
    public static void main(String[] args) throws IOException {
        ObfuscatorConfig config = ObfuscatorConfig.load();

        List<Path> libs = new ArrayList<>();
        if (config.getLibrariesDirectory() != null) {
            Files.walk(config.getLibrariesDirectory().toPath(), FileVisitOption.FOLLOW_LINKS)
                .filter(f -> f.toString().endsWith(".jar") || f.toString().endsWith(".zip"))
                .forEach(libs::add);
        }

        new NativeObfuscator()
            .process(
                config.getInputJar().toPath(),
                config.getOutputJar().toPath(),
                libs,
                null,
                config.getLoaderDirectory(),
                config.getPlatform(),
                config.isAnnotations()
            );
    }
}
