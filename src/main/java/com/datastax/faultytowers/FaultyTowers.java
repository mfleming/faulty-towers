package com.datastax.faultytowers;

import com.github.javaparser.*;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.body.*;
import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;

/**
 * <p>FaultyTowers is the main entry point to the application.</p>
 *
 * <p>Depending on which mode we're executing in, we're either collecting code coverage data for a
 * subsequent run or profiling a workload until we have enough samples that we can inject faults
 * without needing to restart the workload.</p>
 *
 * <p><b>offline-mode:</b> This is used when injecting faults into short-running tests, e.g. JUnit tests.
 * This mode uses 2 phases. First, we collect coverage data for the JUnit test which we write to
 * a file once the JUnit tests have finished. In the second step, the file is read, the JUnit tests
 * are executed again, and faults are injected.</p>
 *
 * <p><b>online-mode:</b> This mode is used when injecting faults into long-running tests, e.g. performance
 * workloads. Here we don't write out intermediate coverage data to a file. Instead, we profile
 * the workload at runtime and then inject faults.</p>
 */
public class FaultyTowers {
    private final PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:**.java");
    private final JavaParser jvParser;

    private final CoverageAnalyzer coverageAnalyzer;

    public FaultyTowers(CoverageAnalyzer coverageAnalyzer) {
        this.coverageAnalyzer = coverageAnalyzer;
        // Set configuration
        ParserConfiguration parseConfig = new ParserConfiguration();
        parseConfig.setCharacterEncoding(StandardCharsets.UTF_8);
        parseConfig.setTabSize(4);
        parseConfig.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_8);

        // Get the parser
        jvParser = new JavaParser(parseConfig);
    }

    // Recursively walk the directory hierarchy and parse all .java files.
    private void parseDirectory(String directory) throws IOException {
        List<Path> paths = Files.walk(Paths.get(directory))
                .filter(Files::isRegularFile)
                .filter(matcher::matches)
                .collect(Collectors.toList());

        for (Path path : paths) {
            //System.out.println(path.toAbsolutePath());
            parse(path);
        }
    }

    /**
     * Parse a file and record all fault locations.
     *
     * <p>Currently "faults" are limited to throw statements only.</p>
     *
     * @param path The path of the file to parse.
     * @throws IOException
     */
    public void parse(Path path) throws IOException {
        ParseResult<CompilationUnit> result = jvParser.parse(path);
        if (!result.isSuccessful()) {
            System.out.print(String.format("Parsing %s failed:", path.toAbsolutePath()));
            List<Problem> problems = result.getProblems();

            for (Problem problem : problems) {
                System.out.println(problem.getVerboseMessage());
            }
            return;
        }
        parse0(result);
    }

    @VisibleForTesting
    // This method is only used for unit tests.
    public void parse(InputStream is) {
        ParseResult<CompilationUnit> result = jvParser.parse(is);
        if (!result.isSuccessful())
            return;

        parse0(result);
    }

    private void parse0(ParseResult<CompilationUnit> result) {
        if (!result.getResult().isPresent())
            return;

        CompilationUnit compilationUnit = result.getResult().get();
        parseCompilationUit(compilationUnit);
    }

    /**
     * Install the Java Agent into the currently running JVM and return the FaultyTowers object.
     *
     * @return The FaultyTowers object installed by the agent or {@code null} on failure.
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

        try {
            VirtualMachine vm = VirtualMachine.attach(pid);
            // TODO this is hardcoded. Need to fix.
            vm.loadAgent("target/faulty-towers-1.0-SNAPSHOT.jar");
            FaultyTowers ft = (FaultyTowers) Class.forName(Agent.class.getName(), true, ClassLoader.getSystemClassLoader())
                    .getMethod("getFaultyTowers")
                    .invoke(null);
            return ft;

        } catch (AttachNotSupportedException e) {
            System.out.println("Attach not supported");
            e.printStackTrace();
        } catch (IOException e) {
            System.out.println("IOException e");
            e.printStackTrace();
        } catch (AgentLoadException e) {
            e.printStackTrace();
        } catch (AgentInitializationException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
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

    /**
     * Return the code coverage statistic for the specified method.
     *
     * <p>Return the percentage of time that {@code methodName} in class {@code className}
     * was executed between the last call to {@link #installAgent()} and now.</p>
     *
     * @param className  The name of the class containing {@code methodName}.
     * @param methodName The name of the method.
     * @return The percentage of time spent in the specified method.
     */
    public double getCoverage(String className, String methodName) {
        return coverageAnalyzer.getCoverage(className, methodName);
    }

    /**
     * Write the recorded coverage data to {@code outputFile}.
     *
     * @param outputFile The name of the file to write to.
     * @throws IOException
     */
    public void writeCoverage(String outputFile) throws IOException {
        byte[] data = {};
        Path file = Paths.get(outputFile);
        Files.write(file, data);
    }

    private static class FaultLocation {
        String method;
        List<String> exceptions;

        public FaultLocation(String method, List<String> exceptions) {
            this.method = method;
            this.exceptions = exceptions;
        }
    }

    private List<FaultLocation> faults = new ArrayList<>();

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

                faults.add(new FaultLocation(method.getName().toString(), exceptionsThrown));
                //System.out.println("Method: " + method.getName() + " at " + method.getBegin().get() + " throws " + exceptionsThrown);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        String directory = args[0];
        // Parse a diretory only.
        FaultyTowers ft = new FaultyTowers(null);
        ft.parseDirectory(directory);
    }

    /**
     * Returns the number of faults we've discovered so far from all {@link #parse(Path)} calls.
     *
     * @return The total number of faults identified so far.
     */
    public int numFaults() {
        return faults.stream()
                .map(f -> f.exceptions.size())
                .reduce(0, Integer::sum);
    }
}
