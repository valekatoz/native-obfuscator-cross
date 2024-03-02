package dev.lennoxlotl.obfuscator.zig;

import dev.lennoxlotl.obfuscator.Util;
import lombok.Builder;
import me.tongfei.progressbar.ProgressBar;
import org.tinylog.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Target specific zig compiler runner.
 */
@Builder
public class ZigTargetCompiler {
    private static final String[] JNI_HEADER_FILES = {
        "classfile_constants.h",
        "jawt.h",
        "jawt_md.h",
        "jdwpTransport.h",
        "jni.h",
        "jni_md.h",
        "jvmti.h",
        "jvmticmlr.h",
    };

    private final String compilerPath;
    private final Path outputDir;
    private final Path cppDir;
    private final int threads;
    private final ZigCompilationTarget target;
    private final boolean hotspot;

    private final List<String> objectFiles = new ArrayList<>();
    private final List<Future<Void>> compilationTasks = new ArrayList<>();

    /**
     * Compiles the source files with zig.
     *
     * @return The path to the compiled binary
     */
    public Path compile() throws Exception {
        boolean threadingRequired = threads > 1;
        ExecutorService executorService = threadingRequired ? Executors.newFixedThreadPool(threads) : null;
        Path compilationDir = outputDir.resolve("zig_temp_" + target.name().toLowerCase());
        Files.createDirectories(compilationDir);
        Path targetCompilationDir = compilationDir.resolve(target.getTargetName()).toAbsolutePath();
        Files.createDirectories(targetCompilationDir);
        Path jniDir = extractJniHeaders(compilationDir);

        Stream<Path> fileStream = Files.walk(this.cppDir);
        List<Path> files = fileStream
            .filter(path -> path.toString().endsWith(".cpp"))
            .toList();
        fileStream.close();

        String platformPrefix = "\u001B[93m[zig/" + target.name().toLowerCase() + "] \u001B[0m";
        String progressPrefix = "\u001B[32m[INFO] " + platformPrefix;
        try (ProgressBar progressBar = Util.buildProgressbar(progressPrefix, files.size())) {
            for (Path path : files) {
                String fullCppPath = path.toAbsolutePath().toString();
                String fileName = path.getFileName().toString();
                String objectFileName = targetCompilationDir.resolve(fileName + ".o")
                    .toAbsolutePath()
                    .toString();

                objectFiles.add(objectFileName);

                Runnable callable = () -> {
                    try {
                        runProcess(
                            List.of(this.compilerPath,
                                "c++",
                                "-target", target.getCompileTarget(),
                                "-shared",
                                "-I" + jniDir.toAbsolutePath(),
                                "-O3",
                                "-s",
                                "-c",
                                "-o", objectFileName,
                                "-D",
                                hotspot ? "USE_HOTSPOT" : "USE_STANDARD",
                                fullCppPath
                            ),
                            outputDir.toFile()
                        );

                        if (progressBar != null) {
                            progressBar.step();
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                };

                if (threadingRequired) {
                    //noinspection unchecked
                    compilationTasks.add((Future<Void>) executorService.submit(callable));
                } else {
                    callable.run();
                }
            }

            if (threadingRequired) {
                // Wait for all compiler tasks to finish
                for (Future<Void> future : compilationTasks) {
                    future.get();
                }
                executorService.shutdown();
            }
        }

        Path targetDir = targetCompilationDir.resolve("target");
        Files.createDirectories(targetDir);
        String outputFile = targetDir.resolve(target.getLibraryName()).toString();

        Logger.info(platformPrefix + "Linking {}...", target.getLibraryName());
        List<String> linkCommand = new ArrayList<>(
            List.of(
                this.compilerPath,
                "c++",
                "-target",
                target.getCompileTarget(),
                "-shared",
                "-O3",
                "-s",
                "-o",
                outputFile
            )
        );
        linkCommand.addAll(objectFiles);
        runProcess(linkCommand, outputDir.toFile());

        Files.copy(
            targetDir.resolve(target.getLibraryName()),
            compilationDir.resolve(target.getLibraryName()),
            StandardCopyOption.REPLACE_EXISTING
        );

        Logger.info(platformPrefix + "Native library compiled to {}", compilationDir.resolve(target.getLibraryName()));
        return targetDir.resolve(target.getLibraryName());
    }

    /**
     * Runs a process and automatically aborts if exit code is not zero.
     *
     * @param arguments The arguments
     * @param directory The working directory
     */
    private void runProcess(List<String> arguments, File directory) throws IOException, InterruptedException {
        String platformPrefix = "\u001B[93m[zig/" + target.name().toLowerCase() + "] \u001B[0m";
        Process process = new ProcessBuilder()
            .command(arguments)
            .directory(directory)
            .start();

        int exitCode = process.waitFor();
        if (exitCode != 0 && exitCode != 130) {
            String errorOutput = new String(process.getErrorStream().readAllBytes());
            String output = new String(process.getInputStream().readAllBytes());
            Logger.error(platformPrefix + errorOutput);
            Logger.info(platformPrefix + output);
            throw new RuntimeException("Zig process failed with exit code " + exitCode);
        }
    }

    /**
     * Extracts the jni headers from the jar resources.
     *
     * @return The path to the jni headers in the file system
     */
    private Path extractJniHeaders(Path compilationDirectory) throws IOException {
        Path jniHeaders = compilationDirectory.resolve("jni_headers");
        Files.createDirectories(jniHeaders);

        for (String jniHeaderFile : JNI_HEADER_FILES) {
            Util.copyResource("zig/" + target.getJniHeaderPath() + "/" + jniHeaderFile, jniHeaders);
        }

        return jniHeaders;
    }
}
