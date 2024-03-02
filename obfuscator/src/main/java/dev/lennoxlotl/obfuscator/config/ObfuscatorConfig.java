package dev.lennoxlotl.obfuscator.config;

import dev.lennoxlotl.obfuscator.Platform;
import dev.lennoxlotl.obfuscator.Util;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

import java.io.File;
import java.io.IOException;

/**
 * Configuration file for the obfuscator.
 */
@Getter
@ToString
@AllArgsConstructor
public class ObfuscatorConfig {
    private File inputJar;
    private File outputJar;
    private File librariesDirectory;
    private String loaderDirectory;
    private Platform platform;
    private boolean annotations;

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
            result.errors().forEach(error -> System.err.println(error.toString()));
            throw new IllegalStateException("Invalid configuration file!");
        }

        String input = result.getString("input", () -> "input.jar");
        String output = result.getString("output", () -> "output.jar");
        String libraries = result.getString("libraries", () -> "libraries");
        String platform = result.getString("platform", () -> "hotspot");
        String loaderDirectory = result.getString("loader", () -> "native0");
        boolean annotations = result.getBoolean("annotations", () -> false);

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
            // TODO: Replace with logger call (tinylog)
            System.err.println("[WARNING] Libraries directory is invalid, it will not be imported");
        }

        return new ObfuscatorConfig(inputDir, outputDir, librariesDir, loaderDirectory, enumPlatform, annotations);
    }
}
