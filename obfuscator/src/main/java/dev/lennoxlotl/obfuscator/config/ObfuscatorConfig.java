package dev.lennoxlotl.obfuscator.config;

import dev.lennoxlotl.obfuscator.Platform;
import dev.lennoxlotl.obfuscator.Util;
import dev.lennoxlotl.obfuscator.zig.ZigCompilationTarget;
import dev.lennoxlotl.obfuscator.zig.ZigTargetCompiler;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.tinylog.Logger;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration file for the obfuscator.
 */
@Getter
@ToString
@AllArgsConstructor
public class ObfuscatorConfig {
    // Default properties
    private File inputJar;
    private File outputJar;
    private File librariesDirectory;
    private String loaderDirectory;
    private Platform platform;
    private boolean annotations;
    // Zig properties
    private String zigExecutable;
    private int zigCompileThreads;
    private List<ZigCompilationTarget> zigCompilerTargets;

    /**
     * Loads the obfuscator config from the `config.toml` file in the program working directory.
     * If the file cannot be found the default config will be copied into the working directory.
     *
     * @return The parsed obfuscator config
     */
    public static ObfuscatorConfig load() throws IOException {
        File configFile = new File("config.toml");
        // Copy default config file
        if (!configFile.exists()) {
            Util.copyResource("config.toml", new File("").toPath());
        }

        TomlParseResult result = Toml.parse(configFile.toPath());

        // We should abort execution if the config file has errors
        if (!result.errors().isEmpty()) {
            // TODO: Replace with logger call (tinylog)
            result.errors().forEach(error -> Logger.error(error.toString()));
            throw new IllegalStateException("Invalid configuration file!");
        }

        String input = result.getString("input", () -> "input.jar");
        String output = result.getString("output", () -> "output.jar");
        String libraries = result.getString("libraries", () -> "libraries");
        String platform = result.getString("platform", () -> "hotspot");
        String loaderDirectory = result.getString("loader", () -> "native0");
        boolean annotations = result.getBoolean("annotations", () -> false);
        String zigExecutable = result.getString("zig.executable", () -> null);
        int zigCompileThreads = (int) result.getLong("zig.threads", () -> 1L);
        TomlArray targets = result.getArray("zig.targets");

        File inputDir = new File(input);
        File outputDir = new File(output);
        File librariesDir = new File(libraries);
        Platform enumPlatform = Platform.valueOf(platform.toUpperCase());

        // Input file must exist and cannot be a directory
        if (!inputDir.exists() || inputDir.isDirectory()) {
            throw new IllegalStateException("Input file does not exist or is a directory");
        }

        // If libraries directory doesn't exist or is a file we can set it to null, the obfuscator will then ignore it
        if (!librariesDir.exists() || librariesDir.isFile()) {
            librariesDir = null;
            Logger.warn("Libraries directory is invalid, it will not be imported");
        }

        if (targets == null || targets.isEmpty()) {
            throw new IllegalStateException("Please provide at least one compiler target!");
        }

        List<ZigCompilationTarget> compilerTargets = new ArrayList<>();
        int targetsSize = targets.size();
        for (int i = 0; i < targetsSize; i++) {
            compilerTargets.add(ZigCompilationTarget.valueOf(targets.getString(i).toUpperCase()));
        }

        return new ObfuscatorConfig(inputDir,
            outputDir,
            librariesDir,
            loaderDirectory,
            enumPlatform,
            annotations,
            zigExecutable,
            zigCompileThreads,
            compilerTargets);
    }
}
