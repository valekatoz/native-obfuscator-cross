package dev.lennoxlotl.obfuscator.zig;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The main interface to compile files with zig.
 */
public class ZigCompiler {

    /**
     * Compiles the transpiled source-code for all given targets.
     *
     * @return The list of compiled library files
     */
    public static List<Path> compileWithZig(String compilerPath,
                                            Path outputDir,
                                            Path cppDir,
                                            int threads,
                                            boolean hotspot,
                                            List<ZigCompilationTarget> targets) throws Exception {
        List<Path> compiledLibraries = new ArrayList<>();
        for (ZigCompilationTarget target : targets) {
            Path library = ZigTargetCompiler.builder()
                .compilerPath(compilerPath)
                .outputDir(outputDir)
                .cppDir(cppDir)
                .threads(threads)
                .target(target)
                .hotspot(hotspot)
                .build()
                .compile();
            compiledLibraries.add(library);
        }
        return compiledLibraries;
    }
}
