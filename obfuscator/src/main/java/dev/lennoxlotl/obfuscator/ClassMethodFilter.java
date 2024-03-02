package dev.lennoxlotl.obfuscator;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class ClassMethodFilter {

    private static final String NATIVE_ANNOTATION_DESC = Type.getDescriptor(NativeObfuscate.class);

    private final boolean useAnnotations;

    public ClassMethodFilter(boolean useAnnotations) {
        this.useAnnotations = useAnnotations;
    }

    public static void cleanAnnotations(ClassNode classNode) {
        if (classNode.visibleAnnotations != null) {
            classNode.visibleAnnotations.removeIf(annotationNode -> annotationNode.desc.equals(NATIVE_ANNOTATION_DESC));
        }
        classNode.methods.stream()
            .filter(methodNode -> methodNode.visibleAnnotations != null)
            .forEach(methodNode -> methodNode.visibleAnnotations.removeIf(annotationNode ->
                annotationNode.desc.equals(NATIVE_ANNOTATION_DESC)));
    }
}
