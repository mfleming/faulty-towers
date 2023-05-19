package com.datastax.faultytowers;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.tree.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.security.ProtectionDomain;
import java.util.stream.Stream;

/**
 * A {@link ClassFileTransformer} that injects a throw statement at the beginning of each method
 * that either has a {@code throws} clause or throws an unchecked exception.
 *
 * New throw statements are inserted at the beginning of the method.
 *
 * If a class has both a throws clause (checked exception) and throws an unchecked exception, the
 * checked exception takes precendence and will be thrown. There's no real reason this needs to be
 * true it's just an assumption to simplify the code.
 */
public class ExceptionThrower implements ClassFileTransformer {
    private final double throwProbability;

    public ExceptionThrower(double throwProbability) {
        this.throwProbability = throwProbability;
    }

    public byte[] transform(ClassLoader loader, String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classFileBuffer) {
        writeDebugLog("transforming: " + className);
        return injectThrow(classFileBuffer);
    }

    // Do not transform bytecode for JDK classes other transformers.
    private boolean shouldIgnoreClassName(String className) {
        if (Stream.of(
                "java/",
                "sun/",
                "jdk/",
                "org/junit/",
                "org/jacoco/",
                "org/apache/tools",
                "org/slf4j",
                "ch/qos").anyMatch(className::startsWith))
            return true;

        return className.contains("$");
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

        return injectException(node);
    }

    /**
     * Inject a throw statement at the beginning of each method that has a {@code throws} clause.
     * @param node The class to inject the throw statements into.
     * @return The transformed class.
     */
    private byte[] injectException(ClassNode node) {
        int size = node.methods.size();
        node.methods.forEach(method -> {
            // Choose whether to inject an exception with throwProbability
            if (Math.random() > throwProbability)
                return;

            writeDebugLog("Injecting exception for method " + method.name + " in class " + node.name);

            String exceptionClassName;
            if (!method.exceptions.isEmpty())
                exceptionClassName = method.exceptions.get(0);
            else
                exceptionClassName = getThrowInsnUncheckedExceptionClassName(method);

            if (exceptionClassName == null)
                return;

            createThrowInstruction(method, exceptionClassName);
        });
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    /**
     * If a method does not have a {@code throws} clause, then search the instructions to see if it
     * throws an unchecked exception.
     * @param method The method to search
     * @return The name of the first unchecked exception that is thrown, or null if none is found.
     */
    private String getThrowInsnUncheckedExceptionClassName(MethodNode method) {
        assert method.exceptions.isEmpty();

        // Return the first unchecked exception class name that we find
        InsnList instructions = method.instructions;
        for (AbstractInsnNode instruction : instructions.toArray()) {
            if (instruction.getOpcode() == Opcodes.ATHROW) {
                MethodInsnNode methodInsnNode = (MethodInsnNode) instruction.getPrevious();
                return methodInsnNode.owner;
            }
        }

        return null;
    }

    /**
     * Create a throw instruction that throws {@code exceptionClassName}.
     * @param method The method to inject the throw instruction into
     * @param exceptionClassName The name of the exception to throw
     */
    private void createThrowInstruction(MethodNode method, String exceptionClassName) {
        InsnList newInstructions = new InsnList();
        newInstructions.add(new LdcInsnNode(exceptionClassName));

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
    }

    private void writeDebugLog(String s) {
        try (FileOutputStream fos = new FileOutputStream("/tmp/faulty.log", true)) {
            String line = System.currentTimeMillis() + " " + s + "\n";
            fos.write(line.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Invokved by the JVM to throw an exception. This method is injected into the bytecode via {@code injectException()}
     */
    @SuppressWarnings("unused")
    public static Throwable throwException(String className) {
        String fullyQualifiedClassName = className.replace("/", ".");
        try {
            Class <?> p = Class.forName(fullyQualifiedClassName, false, ClassLoader.getSystemClassLoader());
            if (!Throwable.class.isAssignableFrom(p)) {
                return new RuntimeException("foobar");
            }

            Constructor<?>[] constructors = p.getConstructors();
            if (constructors.length == 0)
                return new RuntimeException("Failed to throw " + fullyQualifiedClassName + ": no constructors found");

            // Search through all constructors to find one that takes a single argument
            for (Constructor<?> constructor : constructors) {
                Class<?>[] parameterTypes = constructor.getParameterTypes();
                if (parameterTypes.length == 1) {
                    Class<?> parameterType = parameterTypes[0];
                    if (parameterType.equals(String.class)) {
                        return (Throwable) constructor.newInstance("injected exception");
                    }
                    if (Throwable.class.isAssignableFrom(parameterType)) {
                        return (Throwable) constructor.newInstance(new RuntimeException("injected exception"));
                    }
                    if (parameterType.equals(int.class)) {
                        return (Throwable) constructor.newInstance(1);
                    }
                }
            }

            // Failing that, try to instantiate a no-arg constructor
            return (Throwable) p.newInstance();
        } catch (Exception e) {
            e.printStackTrace();
            return new RuntimeException("Failed to throw " + fullyQualifiedClassName + ": " + e.getMessage());
        }
    }
}
