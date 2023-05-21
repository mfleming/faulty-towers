package com.datastax.faultytowers;

import com.google.common.annotations.VisibleForTesting;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * A {@link ClassFileTransformer} that injects a throw statement at the beginning of each method
 * that either has a {@code throws} clause or throws an unchecked exception somewhere in its body.
 *
 * Injection can be controlled by specifying the {@code throwProbability} parameter that gets
 * passed to the {@link ExceptionThrower} constructor.
 *
 * Instead of actually injecting a {@code Opcode.ATHROW} instruction into the body of the method,
 * we inject a call to {@link ExceptionThrower#throwException()} which will throw an exception
 * provided that the {@code THROW_LIMIT} has not been exceeded.
 *
 * If a class has both a throws clause (checked exception) and throws an unchecked exception, the
 * checked exception takes precendence and will be thrown. There's no real reason this needs to be
 * true it's just an assumption to simplify the code.
 */
public class ExceptionThrower implements ClassFileTransformer {
    // Count how many times an exception has been throw for a method
    private static final ConcurrentHashMap<String, AtomicInteger> throwCounter = new ConcurrentHashMap<>();
    private final double throwProbability;

    // By default we want to limit the number of times an exception is thrown to 1. This is to give
    // the app a chance to continue functioning after a failure.
    @VisibleForTesting
    public static long THROW_LIMIT = 1;

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

            InsnList newInstructions = new InsnList();
            newInstructions.add(new LdcInsnNode(node.name + "." + method.name));
            newInstructions.add(new LdcInsnNode(exceptionClassName));
            newInstructions.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "com/datastax/faultytowers/ExceptionThrower",
                    "throwException",
                    "(Ljava/lang/String;Ljava/lang/String;)V",
                    false
                    ));

            method.maxStack += newInstructions.size();
            method.instructions.insertBefore(method.instructions.getFirst(), newInstructions);
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

    private void writeDebugLog(String s) {
        try (FileOutputStream fos = new FileOutputStream("/tmp/faulty.log", true)) {
            String line = System.currentTimeMillis() + " " + s + "\n";
            fos.write(line.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Invokved by the JVM to throw an exception. This method is injected into the bytecode via
     * {@code injectException()}. The {@code exceptionClassName} parameter is the name of the exception to
     * throw. {@code THROW_LIMIT} places a limit on the number of times that an exception is thrown
     * from the same method.
     *
     * NOTE: A current limitation is that this method can only throw exceptions that have a zero or
     * one argument constructor.
     *
     * @param callingMethodName The name of the method that called this method
     * @param exceptionClassName The name of the exception to throw
     */
    @SuppressWarnings("unused")
    public static void throwException(String callingMethodName, String exceptionClassName) throws Throwable {
        Throwable throwable = null;
        String fullyQualifiedClassName = exceptionClassName.replace("/", ".");
        try {
            Class <?> p = Class.forName(fullyQualifiedClassName, false, ClassLoader.getSystemClassLoader());
            if (!Throwable.class.isAssignableFrom(p)) {
                throw new RuntimeException("Class " + fullyQualifiedClassName + " is not a Throwable");
            }

            Constructor<?>[] constructors = p.getConstructors();
            if (constructors.length == 0)
                throw new RuntimeException("Failed to throw " + fullyQualifiedClassName + ": no constructors found");

            // Search through all constructors to find one that takes a single argument
            for (Constructor<?> constructor : constructors) {
                Class<?>[] parameterTypes = constructor.getParameterTypes();
                if (parameterTypes.length == 1) {
                    Class<?> parameterType = parameterTypes[0];
                    if (parameterType.equals(String.class)) {
                        throwable = (Throwable) constructor.newInstance("injected exception");
                    }
                    if (Throwable.class.isAssignableFrom(parameterType)) {
                        throwable = (Throwable) constructor.newInstance(new RuntimeException("injected exception"));
                    }
                    if (parameterType.equals(int.class)) {
                        throwable = (Throwable) constructor.newInstance(1);
                    }
                }
            }

            // Failing that, try to instantiate a no-arg constructor
            if (throwable == null) {
                throwable = (Throwable) p.newInstance();
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to throw " + fullyQualifiedClassName + ": " + e.getMessage());
        }

        long counter = throwCounter.computeIfAbsent(callingMethodName,
                k -> new AtomicInteger(0)).incrementAndGet();
        if (counter > THROW_LIMIT)
            return;

        throw throwable;
    }
}
