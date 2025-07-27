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
    private List<String> includePatterns;
    private List<String> excludePatterns;
    // Default properties
    private File inputJar;
    private File outputJar;
    private File librariesDirectory;
    private String loaderDirectory;
    private Platform platform;
    private boolean annotations;
    private boolean deleteTempDir;
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
            Logger.info("The default config has been generated, configure it to your liking before running the tool again!");
            System.exit(0);
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
        boolean deleteTempDir = result.getBoolean("delete_temp_dir", () -> true);
        String zigExecutable = result.getString("zig.executable", () -> null);
        int zigCompileThreads = (int) result.getLong("zig.threads", () -> 1L);
        TomlArray targets = result.getArray("zig.targets");
        TomlArray includePatternsArray = result.getArray("include_patterns");
        TomlArray excludePatternsArray = result.getArray("exclude_patterns");

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

        List<String> includePatterns = new ArrayList<>();
        if (includePatternsArray != null) {
            int includePatternsSize = includePatternsArray.size();
            for (int i = 0; i < includePatternsSize; i++) {
                includePatterns.add(includePatternsArray.getString(i));
            }
        }

        List<String> excludePatterns = new ArrayList<>();
        if (excludePatternsArray != null) {
            int excludePatternsSize = excludePatternsArray.size();
            for (int i = 0; i < excludePatternsSize; i++) {
                excludePatterns.add(excludePatternsArray.getString(i));
            }
        }

        return new ObfuscatorConfig(
            includePatterns,
            excludePatterns,
            inputDir,
            outputDir,
            librariesDir,
            loaderDirectory,
            enumPlatform,
            annotations,
            deleteTempDir,
            zigExecutable,
            zigCompileThreads,
            compilerTargets);
    }

    /**
     * Checks if a class should be processed for obfuscation based on include/exclude patterns.
     * If include patterns are specified, only classes matching those patterns are processed.
     * Otherwise, all classes except those matching exclude patterns are processed.
     *
     * @param className The internal class name (e.g., "com/example/MyClass")
     * @return true if the class should be processed, false otherwise
     */
    public boolean shouldProcessClass(String className) {
        Logger.debug("Checking class: {}", className);
        Logger.debug("Include patterns: {}", includePatterns);
        Logger.debug("Exclude patterns: {}", excludePatterns);
        
        // If include patterns are specified, only process classes that match
        if (!includePatterns.isEmpty()) {
            boolean includeMatch = includePatterns.stream().anyMatch(pattern -> {
                boolean matches = matchesGlobPattern(className, pattern);
                Logger.debug("Include pattern '{}' vs '{}': {}", pattern, className, matches);
                return matches;
            });
            
            if (includeMatch) {
                // Check if it should be excluded even though it matches include
                boolean excludeMatch = excludePatterns.stream().anyMatch(pattern -> {
                    boolean matches = matchesGlobPattern(className, pattern);
                    Logger.debug("Exclude pattern '{}' vs '{}': {}", pattern, className, matches);
                    return matches;
                });
                
                boolean shouldProcess = !excludeMatch;
                Logger.debug("Class '{}' - Include match: {}, Exclude match: {}, Final result: {}", 
                    className, includeMatch, excludeMatch, shouldProcess);
                return shouldProcess;
            } else {
                Logger.debug("Class '{}' - No include match, skipping", className);
                return false;
            }
        }
        
        // Otherwise, process all classes except those matching exclude patterns
        boolean excludeMatch = excludePatterns.stream().anyMatch(pattern -> {
            boolean matches = matchesGlobPattern(className, pattern);
            Logger.debug("Exclude pattern '{}' vs '{}': {}", pattern, className, matches);
            return matches;
        });
        
        boolean shouldProcess = !excludeMatch;
        Logger.debug("Class '{}' - Exclude match: {}, Final result: {}", className, excludeMatch, shouldProcess);
        return shouldProcess;
    }
    
    /**
     * Matches a class name against a glob pattern.
     *
     * @param className The class name to match
     * @param pattern The glob pattern
     * @return true if the class name matches the pattern
     */
    private boolean matchesGlobPattern(String className, String pattern) {
        // Convert glob pattern to regex
        String regex = pattern
            .replace("**", "DOUBLE_STAR_PLACEHOLDER")
            .replace("*", "[^/]*")
            .replace("DOUBLE_STAR_PLACEHOLDER", ".*")
            .replace("/", "/");
        
        boolean matches = className.matches(regex);
        Logger.debug("Pattern matching: '{}' -> regex '{}' vs '{}' = {}", pattern, regex, className, matches);
        
        return matches;
    }
}
