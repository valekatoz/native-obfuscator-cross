package dev.lennoxlotl.obfuscator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * When a method is annotated with this annotation its contents will be converted into JNI calls.
 * The resulting method's bytecode is removed and replaced with a native method call.
 * <p>
 * Can be applied to a class to obfuscate all the class members.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface NativeObfuscate {
}
