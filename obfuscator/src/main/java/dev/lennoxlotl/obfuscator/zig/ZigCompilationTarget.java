package dev.lennoxlotl.obfuscator.zig;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * All available compilation targets.
 */
@Getter
@RequiredArgsConstructor
public enum ZigCompilationTarget {
    WINDOWS_X86("x86_64-windows-gnu", "x64-windows.dll", "win/x86", "x64-windows"),
    LINUX_X86("x86_64-linux-gnu", "x64-linux.so", "linux/x86", "x64-linux"),
    MACOS_X86("x86_64-macos", "x64-macos.dylib", "macos/x64", "x64-macos"),
    MACOS_AARCH64("aarch64-macos", "aarch64-macos.dylib", "macos/aarch64", "arm64-macos");

    private final String compileTarget;
    private final String libraryName;
    private final String jniHeaderPath;
    private final String targetName;
}
