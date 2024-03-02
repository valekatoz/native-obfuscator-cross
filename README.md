# native-obfuscator-cross

**DISCLAIMER:** This project is based off of https://github.com/radioegor146/native-obfuscator

Java .class to .cpp converter for use with JNI with cross compilation support (compile for all targets on one machine)

Currently fully supports only Java 8. Java 9+ and Android support is entirely experimental

Also, this tool does not particularly obfuscate your code; it just transpiles it to native. Remember to use protectors
like VMProtect, Themida, or obfuscator-llvm (in case of clang usage)

## Prerequisites

- Java 17
    - You can obfuscate Java 8 compiled class-files without problems, the obfuscator itself runs on Java 17 and
      is therefore taken for granted
- Zig (C++ Compiler)
    - It is not recommended to download Zig using package managers as it tends to be really outdated in these

Running the obfuscator on Windows **is untested**, you might only be able to obfuscate files while being on a Unix based operating system. Running
the produced jar-file will work on Windows just fine as long as the compilation target is set in the config.

## Config
Below you can find the default config, it is generated on first launch

```toml
# The path of the to obfuscate jar
input = "input.jar"
# The target path for the output jar
output = "output.jar"
# The folder which contains all libraries your program might reference
libraries = "libraries"
# The class file path to use for the loader class
loader = "dev/lennoxlotl/obfuscator"
# The jvm platform to transpile for
# Available are:
#   - hotspot (recommended)
#   - std_java
#   - android
platform = "hotspot"
# Whether to use annotations to filter classes or not
annotations = false
# Deletes the working directory if enabled
delete_temp_dir = true

# Zig compiler settings
[zig]
# The path to the executable of the compiler
executable = ""
# The amount of threads to use
threads = 32
# The targets to compile the transpiled code for
# Available are:
#   - windows_x86
#   - linux_x86
#   - macos_x86
#   - macos_aarch64
targets = [
    "windows_x86",
    "linux_x86"
]
```

## Roadmap
- [x] Config file instead of cli
- [x] Compilation using Zig
- [ ] VMProtect Support (optional marker placement with annotations)