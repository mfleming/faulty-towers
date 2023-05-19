package com.datastax.faultytowers;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.google.common.annotations.VisibleForTesting;
import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * <p>FaultyTowers is the main entry point to the application.</p>
 *
 * <p>Depending on which mode we're executing in, we're either collecting code coverage data for a
 * subsequent run or profiling a workload until we have enough samples that we can inject faults
 * without needing to restart the workload.</p>
 *
 * <p><b>offline-mode:</b> This is used when injecting faults into short-running tests, e.g. JUnit tests.
 * This mode uses 2 phases. First, we collect coverage data for the JUnit test which we writeCoverageData to
 * a file once the JUnit tests have finished. In the second step, the file is read, the JUnit tests
 * are executed again, and faults are injected.</p>
 *
 * <p><b>online-mode:</b> This mode is used when injecting faults into long-running tests, e.g. performance
 * workloads. Here we don't writeCoverageData out intermediate coverage data to a file. Instead, we profile
 * the workload at runtime and then inject faults.</p>
 */
public class FaultyTowers {
    public FaultyTowers() {

    }

    @VisibleForTesting
    /**
     * Install the Java Agent into the current JVM and return the FaultyTowers object.
     */
    public static FaultyTowers installAgent() {
        String pid;
        String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
        int processIdIndex = runtimeName.indexOf('@');
        if (processIdIndex == -1) {
            throw new IllegalStateException("Cannot extract process id from runtime management bean");
        } else {
            pid = runtimeName.substring(0, processIdIndex);
        }
        return installAgent(pid);
    }

    /**
     * Install the Java Agent into the JVM specified by pid and return the FaultyTowers object.
     *
     * @return The FaultyTowers object installed by the agent or {@code null} on failure.
     * @param pid The target JVM process id.
     */
    public static FaultyTowers installAgent(String pid) {
       try {
           System.out.println("Attaching to " + pid);
            VirtualMachine vm = VirtualMachine.attach(pid);
            String agentPath = System.getProperty("user.dir") + "/target/faulty-towers-1.0-SNAPSHOT.jar";
            vm.loadAgent(agentPath);
            System.out.println("Agent loaded");
            return (FaultyTowers) Class.forName(Agent.class.getName(), true, ClassLoader.getSystemClassLoader())
                    .getMethod("getFaultyTowers")
                    .invoke(null);

        } catch (AttachNotSupportedException e) {
            System.out.println("Attach not supported");
            e.printStackTrace();
        } catch (IOException e) {
            System.out.println("IOException e");
            e.printStackTrace();
        } catch (AgentLoadException |AgentInitializationException | ClassNotFoundException |
                InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Remove the Java Agent from the target VM.
     */
    public static void removeAgent() {
        // TODO
    }

    private static class FaultLocation {
        String method;
        List<String> exceptions;

        public FaultLocation(String method, List<String> exceptions) {
            this.method = method;
            this.exceptions = exceptions;
        }
    }

    private List<FaultLocation> faultLocations = new ArrayList<>();

    /**
     * Parse a compilation unit and record all throw statements.
     *
     * @param compilationUnit The compilation unit to parse.
     */
    private void parseCompilationUit(CompilationUnit compilationUnit) {
        List<ClassOrInterfaceDeclaration> classes = compilationUnit.findAll(ClassOrInterfaceDeclaration.class).stream()
                .filter(c -> !c.isInterface())
                .collect(Collectors.toList());

        for (ClassOrInterfaceDeclaration c : classes) {
            List<MethodDeclaration> methods = c.getMethods();
            for (MethodDeclaration method : methods) {
                Optional<BlockStmt> body = method.getBody();

                // Empty body
                if (!body.isPresent())
                    continue;

                // Search for the throw statements.
                List<ThrowStmt> throwStatements = body.get().findAll(ThrowStmt.class);
                if (throwStatements.size() == 0)
                    continue;

                // Storing name of exceptions thrown into this list.
                List<String> exceptionsThrown = new ArrayList<>();

                for (ThrowStmt stmt : throwStatements) {
                    // Convert the throw expression to object creation expression and get the type.
                    Expression expr = stmt.getExpression();
                    if (expr.isObjectCreationExpr()) {
                        String exceptionName = expr.asObjectCreationExpr().getType().toString();
                        if (!exceptionsThrown.contains(exceptionName))
                            exceptionsThrown.add(exceptionName);
                    }
                    // TODO We need to do the extra work to figure out the exception class when the code doesn't
                    // do a simple "throw new RuntimeException();".
//                    else {
//                        System.out.println("Expr is " + expr);
//                    }
                }

                // See TODO above.
                if (exceptionsThrown.isEmpty())
                    continue;

                faultLocations.add(new FaultLocation(method.getName().toString(), exceptionsThrown));
                //System.out.println("Method: " + method.getName() + " at " + method.getBegin().get() + " throws " + exceptionsThrown);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        // Verify that we have arguments
        if (args.length != 1) {
            System.out.println("Usage: java -jar faulty-towers.jar <pid>");
            System.exit(1);
        }

        FaultyTowers ft = installAgent(args[0]);
        if (ft == null) {
            System.out.println("Failed to install agent");
            System.exit(1);
        }

        // Wait for Ctrl-C
        System.out.println("Press Ctrl-C to exit");
        try {
            while (true) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            // Ignore
        }
        FaultyTowers.removeAgent();
    }
}
