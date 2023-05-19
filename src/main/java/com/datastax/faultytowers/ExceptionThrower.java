package com.datastax.faultytowers;

import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.tree.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.nio.charset.StandardCharsets;
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
 * <p>
 *     We also want to inject some randomness into the throwing process because we don't want to just hit
 *     the same exceptions every single time the app is run.
 * </p>
 * <p>
 *     TODO: It would be nice in the future to be able to delay the injection of methods, e.g. to only trigger
 *     injection 30seconds after the app has started so that it has time to finish initisation which is generally
 *     less interesting than the main runtime phase.
 * </p>
 *
 */
public class ExceptionThrower implements ClassFileTransformer {
    private final double throwProbability;

    public ExceptionThrower(List<String> methods, double throwProbability) {
        this.throwProbability = throwProbability;
    }
    public byte[] transform(ClassLoader loader, String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classFileBuffer) {
        writeDebugLog("transforming: " + className);
//        System.out.println("transforming: " + className);
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
                "ch/qos",
                "org/apache/cassandra/ServerTestUtils").anyMatch(className::startsWith))
            return true;

        return className.contains("$");

        // TODO remove me. Default to only injecting exceptions for class in org.apache.cassandra
        // If we had filtering parameters this would be much cleaner.
//        if (className.startsWith("org/apache/cassandra"))
//            return false;
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
     * @param node
     * @return
     */
    private byte[] injectException(ClassNode node) {
//        System.out.println("injecting exception for class " + node.name);
        node.methods.forEach(method -> {
            List<String> exceptions = method.exceptions;
            if (exceptions.isEmpty())
                return;

            // TODO this is a hack to avoid throwing in junit test methods
            if (method.name.equals("setupClass"))
                return;

            // Choose whether to inject an exception with throwProbability
            if (Math.random() > throwProbability)
                return;

//            System.out.println("exceptions:" );
//            exceptions.forEach(System.out::println);
            writeDebugLog("Injecting exception for method " + method.name + " in class " + node.name);

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

            // Insert invocation of uncheckedexception

        });
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private void writeDebugLog(String s) {
        try (FileOutputStream fos = new FileOutputStream("/tmp/faulty.log", true)) {
            String line = System.currentTimeMillis() + " " + s + "\n";
            fos.write(line.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unused")
    public static Throwable throwException(String className) {
        String fullyQualifiedClassName = className.replace("/", ".");
        try {
            Class <?> p = Class.forName(fullyQualifiedClassName, false, ClassLoader.getSystemClassLoader());
            if (!Throwable.class.isAssignableFrom(p)) {
                return new RuntimeException("foobar");
            }

            // Check whether exception constructor takes an argument
            try {
                p.getConstructor(String.class);
                return (Throwable) p.getConstructor(String.class).newInstance("injected exception");
            } catch (NoSuchMethodException e) {
                // No argument constructor
            }
            return (Throwable) p.newInstance();
        } catch (Exception e) {
            e.printStackTrace();
            return new RuntimeException("Failed to throw " + fullyQualifiedClassName + ": " + e.getMessage());
        }
    }
}
