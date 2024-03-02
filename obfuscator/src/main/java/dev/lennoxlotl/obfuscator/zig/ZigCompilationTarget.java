package dev.lennoxlotl.obfuscator.zig;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * All available compilation targets.
 */
@Getter
@RequiredArgsConstructor
public enum ZigCompilationTarget {
    WINDOWS_X86("x86_64-windows-gnu", "windows-x64.dll", "win/x86", "x64-windows"),
    LINUX_X86("x86_64-linux-gnu", "linux-x64.so", "linux/x86", "x64-linux"),
    MACOS_X86("x86_64-macos", "macos-x64.dylib", "macos/x64", "x64-macos"),
    MACOS_AARCH64("aarch64_macos", "macos-aarch64.dylib", "macos/aarch64", "arm64-macos");

    private final String compileTarget;
    private final String libraryName;
    private final String jniHeaderPath;
    private final String targetName;
}
