package dev.lennoxlotl.obfuscator;

import dev.lennoxlotl.obfuscator.bytecode.PreprocessorRunner;
import dev.lennoxlotl.obfuscator.config.ObfuscatorConfig;
import dev.lennoxlotl.obfuscator.source.ClassSourceBuilder;
import dev.lennoxlotl.obfuscator.source.MainSourceBuilder;
import dev.lennoxlotl.obfuscator.source.StringPool;
import dev.lennoxlotl.obfuscator.zig.ZigCompiler;
import lombok.Getter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.tinylog.Logger;
import ru.gravit.launchserver.asm.ClassMetadataReader;
import ru.gravit.launchserver.asm.SafeClassWriter;

import java.io.*;
import java.nio.file.FileSystem;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Getter
public class NativeObfuscator {
    private final Snippets snippets;
    private final StringPool stringPool;
    private final MethodProcessor methodProcessor;

    private final NodeCache<String> cachedStrings;
    private final NodeCache<String> cachedClasses;
    private final NodeCache<CachedMethodInfo> cachedMethods;
    private final NodeCache<CachedFieldInfo> cachedFields;

    private HiddenMethodsPool hiddenMethodsPool;

    private int currentClassId;
    private String nativeDir;

    public NativeObfuscator() {
        stringPool = new StringPool();
        snippets = new Snippets(stringPool);
        cachedStrings = new NodeCache<>("(cstrings[%d])");
        cachedClasses = new NodeCache<>("(cclasses[%d])");
        cachedMethods = new NodeCache<>("(cmethods[%d])");
        cachedFields = new NodeCache<>("(cfields[%d])");
        methodProcessor = new MethodProcessor(this);
    }

    public void process(ObfuscatorConfig config) throws IOException {
        Path workingDir = new File("temp-" + System.currentTimeMillis()).toPath();
        Path inputJarPath = config.getInputJar().toPath();
        List<Path> inputLibs = new ArrayList<>();
        if (config.getLibrariesDirectory() != null) {
            Files.walk(config.getLibrariesDirectory().toPath(), FileVisitOption.FOLLOW_LINKS)
                .filter(f -> f.toString().endsWith(".jar") || f.toString().endsWith(".zip"))
                .forEach(inputLibs::add);
        }

        if (Files.exists(workingDir) && Files.isSameFile(inputJarPath.toRealPath().getParent(), workingDir.toRealPath())) {
            throw new RuntimeException("Input jar can't be in the same directory as output directory");
        }

        List<Path> libs = new ArrayList<>(inputLibs);
        libs.add(inputJarPath);
        ClassMethodFilter classMethodFilter = new ClassMethodFilter(config.isAnnotations());
        ClassMetadataReader metadataReader = new ClassMetadataReader(libs.stream().map(x -> {
            try {
                return new JarFile(x.toFile());
            } catch (IOException ex) {
                return null;
            }
        }).collect(Collectors.toList()));

        Path cppDir = workingDir.resolve("cpp");
        Path cppOutput = cppDir.resolve("output");
        Files.createDirectories(cppOutput);

        Util.copyResource("sources/native_jvm.cpp", cppDir);
        Util.copyResource("sources/native_jvm.hpp", cppDir);
        Util.copyResource("sources/native_jvm_output.hpp", cppDir);
        Util.copyResource("sources/string_pool.hpp", cppDir);

        MainSourceBuilder mainSourceBuilder = new MainSourceBuilder();

        File jarFile = inputJarPath.toAbsolutePath().toFile();
        Path tempJarFile = workingDir.resolve(jarFile.getName());
        try (JarFile jar = new JarFile(jarFile);
             ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(tempJarFile))) {

            Logger.info("Processing {}...", jarFile);

            nativeDir = config.getLoaderDirectory();
            if (jar.stream().anyMatch(x -> x.getName().equals(nativeDir) ||
                x.getName().startsWith(nativeDir + "/"))) {
                Logger.warn("Directory '{}' already exists in input jar file", nativeDir);
            }

            if (jar.stream().anyMatch(x -> x.getName().equals(nativeDir) ||
                x.getName().startsWith(nativeDir + "/"))) {
                Logger.warn("Directory '{}' already exists in input jar file", nativeDir);
            }

            hiddenMethodsPool = new HiddenMethodsPool(nativeDir + "/hidden");

            Integer[] classIndexReference = new Integer[]{0};

            jar.stream().forEach(entry -> {
                if (entry.getName().equals(JarFile.MANIFEST_NAME)) return;

                try {
                    if (!entry.getName().endsWith(".class")) {
                        Util.writeEntry(jar, out, entry);
                        return;
                    }

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    try (InputStream in = jar.getInputStream(entry)) {
                        Util.transfer(in, baos);
                    }
                    byte[] src = baos.toByteArray();

                    if (Util.byteArrayToInt(Arrays.copyOfRange(src, 0, 4)) != 0xCAFEBABE) {
                        Util.writeEntry(out, entry.getName(), src);
                        return;
                    }

                    StringBuilder nativeMethods = new StringBuilder();
                    List<HiddenCppMethod> hiddenMethods = new ArrayList<>();

                    ClassReader classReader = new ClassReader(src);
                    ClassNode rawClassNode = new ClassNode(Opcodes.ASM7);
                    classReader.accept(rawClassNode, 0);

                    if (rawClassNode.methods.stream().noneMatch(node -> MethodProcessor.shouldProcess(rawClassNode, node, config.isAnnotations()))) {
                        Util.writeEntry(out, entry.getName(), src);
                        return;
                    }

                    Logger.info("Preprocessing {}", rawClassNode.name);

                    rawClassNode.methods.stream()
                        .filter(node -> MethodProcessor.shouldProcess(rawClassNode, node, config.isAnnotations()))
                        .forEach(methodNode -> PreprocessorRunner.preprocess(rawClassNode, methodNode, config.getPlatform()));

                    ClassWriter preprocessorClassWriter = new SafeClassWriter(metadataReader, Opcodes.ASM7 | ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
                    rawClassNode.accept(preprocessorClassWriter);
                    classReader = new ClassReader(preprocessorClassWriter.toByteArray());
                    ClassNode classNode = new ClassNode(Opcodes.ASM7);
                    classReader.accept(classNode, 0);

                    Logger.info("Processing {}", classNode.name);

                    if (classNode.methods.stream().noneMatch(x -> x.name.equals("<clinit>"))) {
                        classNode.methods.add(new MethodNode(Opcodes.ASM7, Opcodes.ACC_STATIC,
                            "<clinit>", "()V", null, new String[0]));
                    }

                    cachedStrings.clear();
                    cachedClasses.clear();
                    cachedMethods.clear();
                    cachedFields.clear();

                    try (ClassSourceBuilder cppBuilder =
                             new ClassSourceBuilder(cppOutput, classNode.name, classIndexReference[0]++, stringPool)) {
                        StringBuilder instructions = new StringBuilder();

                        for (int i = 0; i < classNode.methods.size(); i++) {
                            MethodNode method = classNode.methods.get(i);

                            if (!MethodProcessor.shouldProcess(classNode, method, config.isAnnotations())) {
                                continue;
                            }

                            MethodContext context = new MethodContext(this, method, i, classNode, currentClassId);
                            methodProcessor.processMethod(context);
                            instructions.append(context.output.toString().replace("\n", "\n    "));

                            nativeMethods.append(context.nativeMethods);

                            if (context.proxyMethod != null) {
                                hiddenMethods.add(new HiddenCppMethod(context.proxyMethod, context.cppNativeMethodName));
                            }

                            if ((classNode.access & Opcodes.ACC_INTERFACE) > 0) {
                                method.access &= ~Opcodes.ACC_NATIVE;
                            }
                        }

                        if (config.isAnnotations()) {
                            ClassMethodFilter.cleanAnnotations(classNode);
                        }

                        classNode.version = rawClassNode.version;
                        ClassWriter classWriter = new SafeClassWriter(metadataReader,
                            Opcodes.ASM7 | ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
                        classNode.accept(classWriter);
                        Util.writeEntry(out, entry.getName(), classWriter.toByteArray());

                        cppBuilder.addHeader(cachedStrings.size(), cachedClasses.size(), cachedMethods.size(), cachedFields.size());
                        cppBuilder.addInstructions(instructions.toString());
                        cppBuilder.registerMethods(cachedStrings, cachedClasses, nativeMethods.toString(), hiddenMethods);

                        mainSourceBuilder.addHeader(cppBuilder.getHppFilename());
                        mainSourceBuilder.registerClassMethods(currentClassId, cppBuilder.getFilename());
                    }

                    currentClassId++;
                } catch (IOException ex) {
                    Logger.error("Error while processing {}", entry.getName(), ex);
                }
            });

            if (config.getPlatform() == Platform.ANDROID) {
                for (ClassNode hiddenClass : hiddenMethodsPool.getClasses()) {
                    ClassWriter classWriter = new SafeClassWriter(metadataReader, Opcodes.ASM7 | ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
                    hiddenClass.accept(classWriter);
                    Util.writeEntry(out, hiddenClass.name + ".class", classWriter.toByteArray());
                }
            } else {
                for (ClassNode hiddenClass : hiddenMethodsPool.getClasses()) {
                    String hiddenClassFileName = "data_" + Util.escapeCppNameString(hiddenClass.name.replace('/', '_'));

                    mainSourceBuilder.addHeader(hiddenClassFileName + ".hpp");
                    mainSourceBuilder.registerDefine(stringPool.get(hiddenClass.name), hiddenClassFileName);

                    ClassWriter classWriter = new SafeClassWriter(metadataReader, Opcodes.ASM7 | ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
                    hiddenClass.accept(classWriter);
                    byte[] rawData = classWriter.toByteArray();
                    List<Byte> data = new ArrayList<>(rawData.length);
                    for (byte b : rawData) {
                        data.add(b);
                    }

                    try (BufferedWriter hppWriter = Files.newBufferedWriter(cppOutput.resolve(hiddenClassFileName + ".hpp"))) {
                        hppWriter.append("#include \"../native_jvm.hpp\"\n\n");
                        hppWriter.append("#ifndef ").append(hiddenClassFileName.toUpperCase()).append("_HPP_GUARD\n\n");
                        hppWriter.append("#define ").append(hiddenClassFileName.toUpperCase()).append("_HPP_GUARD\n\n");
                        hppWriter.append("namespace native_jvm::data::__ngen_").append(hiddenClassFileName).append(" {\n");
                        hppWriter.append("    const jbyte* get_class_data();\n");
                        hppWriter.append("    const jsize get_class_data_length();\n");
                        hppWriter.append("}\n\n");
                        hppWriter.append("#endif\n");
                    }

                    try (BufferedWriter cppWriter = Files.newBufferedWriter(cppOutput.resolve(hiddenClassFileName + ".cpp"))) {
                        cppWriter.append("#include \"").append(hiddenClassFileName).append(".hpp\"\n\n");
                        cppWriter.append("namespace native_jvm::data::__ngen_").append(hiddenClassFileName).append(" {\n");
                        cppWriter.append("    static const jbyte class_data[").append(String.valueOf(data.size())).append("] = { ");
                        cppWriter.append(data.stream().map(String::valueOf).collect(Collectors.joining(", ")));
                        cppWriter.append("};\n");
                        cppWriter.append("    static const jsize class_data_length = ").append(String.valueOf(data.size())).append(";\n\n");
                        cppWriter.append("    const jbyte* get_class_data() { return class_data; }\n");
                        cppWriter.append("    const jsize get_class_data_length() { return class_data_length; }\n");
                        cppWriter.append("}\n");
                    }
                }
            }

            String loaderClassName = nativeDir + "/Loader";

            ClassReader loaderClassReader = new ClassReader(Objects.requireNonNull(NativeObfuscator.class
                .getResourceAsStream("loader/Loader.class")));
            ClassNode loaderClass = new ClassNode(Opcodes.ASM7);
            loaderClassReader.accept(loaderClass, 0);
            loaderClass.sourceFile = "synthetic";

            ClassNode resultLoaderClass = new ClassNode(Opcodes.ASM7);
            String originalLoaderClassName = loaderClass.name;
            loaderClass.accept(new ClassRemapper(resultLoaderClass, new Remapper() {
                @Override
                public String map(String internalName) {
                    return internalName.equals(originalLoaderClassName) ? loaderClassName : internalName;
                }
            }));
            resultLoaderClass.version = Opcodes.V1_8;

            ClassWriter classWriter = new SafeClassWriter(metadataReader, Opcodes.ASM7 | ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            resultLoaderClass.accept(classWriter);
            Util.writeEntry(out, loaderClassName + ".class", classWriter.toByteArray());

            Logger.info("Jar file ready!");
            Manifest mf = jar.getManifest();
            if (mf != null) {
                out.putNextEntry(new ZipEntry(JarFile.MANIFEST_NAME));
                mf.write(out);
            }
            out.closeEntry();
            metadataReader.close();
        }

        Files.writeString(cppDir.resolve("string_pool.cpp"), stringPool.build());
        Files.writeString(cppDir.resolve("native_jvm_output.cpp"), mainSourceBuilder.build(nativeDir, currentClassId));

        // Compile the source-code with Zig
        try {
            List<Path> paths = ZigCompiler.compileWithZig(config.getZigExecutable(),
                workingDir,
                cppDir,
                config.getZigCompileThreads(),
                true,
                config.getZigCompilerTargets());

            // Copy the compiled libraries into the jarfile
            try (FileSystem fileSystem = FileSystems.newFileSystem(tempJarFile)) {
                for (Path path : paths) {
                    Logger.info("Copying {} to {}", path.getFileName().toString(), nativeDir + "/" + path.getFileName());
                    Path zipPath = fileSystem.getPath(nativeDir + "/" + path.getFileName().toString());
                    Files.copy(path, zipPath);
                }
            }

            Files.copy(tempJarFile, config.getOutputJar().toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (config.isDeleteTempDir()) {
            Util.deleteDirectory(workingDir);
        }
    }
}
