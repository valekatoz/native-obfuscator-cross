package dev.lennoxlotl.obfuscator.special;

import dev.lennoxlotl.obfuscator.MethodContext;

public interface SpecialMethodProcessor {
    String preProcess(MethodContext context);
    void postProcess(MethodContext context);
}
