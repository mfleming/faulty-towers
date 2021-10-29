package com.datastax.faultytowers;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.tree.*;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.stream.Stream;

/**
 * Update the bytecode of a method to throw exceptions. There are multiple places where we might
 * want to do this:
 * <p><ol>
 *     <li>Immediately on entering a method with a {@code throws} clause</li>
 *     <li>At a {@code throw} site that is guarded by some condition</li>
 * </ol></p>
 */
public class ExceptionThrower implements ClassFileTransformer {
    private final List<String> methods;
    public ExceptionThrower(List<String> methods) {
        this.methods = methods;
    }
    public byte[] transform(ClassLoader loader, String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classFileBuffer) throws IllegalClassFormatException {
        return injectThrow(classFileBuffer);
    }

    // Do not transform bytecode for JDK classes other transformers.
    private boolean shouldIgnoreClassName(String className) {
        if (Stream.of(
                "java/",
                "sun/",
                "jdk/",
                "org/junit/",
                "org/jacoco/").anyMatch(s -> className.startsWith(s)))
            return true;

        if (className.contains("$"))
            return true;

        return false;
    }

    /**
     * Given a {@code method}, inject a throw statement with the first exception specified in the
     * {@code throws} clause. All subsequent executions of this method will trigger the injected
     * throw statement.
     */
    private byte[] injectThrow(byte[] classFileBuffer) {
        ClassNode node = new ClassNode();
        new ClassReader(classFileBuffer).accept(node, 0);

        if (shouldIgnoreClassName(node.name))
            return classFileBuffer;

        node.methods.stream().forEach(method -> {
            List<String> exceptions = method.exceptions;
            if (exceptions.isEmpty())
                return;

            InsnList newInstructions = new InsnList();
            // Remember, we only throw the first exception in the throws specification.
            String firstException = exceptions.get(0);
            newInstructions.add(new LdcInsnNode(firstException));

            boolean isInterface = false;
            newInstructions.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "com/datastax/faultytowers/ExceptionThrower",
                    "throwException",
                    "(Ljava/lang/String;)Ljava/lang/Throwable;",
                    isInterface
                    ));

            newInstructions.add(new InsnNode(Opcodes.ATHROW));
            newInstructions.add(new FrameNode(
                    Opcodes.F_APPEND,
                    0, new Object[] {},
                    0, new Object[] {}
            ));
            method.maxStack += newInstructions.size();
            method.instructions.insertBefore(method.instructions.getFirst(), newInstructions);
        });
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    public static Throwable throwException(String className) {
        String fullyQualifiedClassName = className.replace("/", ".");
        try {
            Class <?> p = Class.forName(fullyQualifiedClassName, false, ClassLoader.getSystemClassLoader());
            if (Throwable.class.isAssignableFrom(p)) {
                return (Throwable) p.newInstance();
            } else {
                return new RuntimeException("foobar");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new RuntimeException("Failed to throw " + fullyQualifiedClassName + ": " + e.getMessage());
        }
    }
}
