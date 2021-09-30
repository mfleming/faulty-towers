package com.datastax.faultytowers;

import com.github.javaparser.*;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.body.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;

/**
 * FaultyTowers is the main entry point to the application.
 *
 * Depending on which mode we're executing in, we're either collecting code coverage data for a subsequent run
 * or profiling a workload until we have enough samples that we can inject faults without needing to restart the
 * workload.
 */
public class FaultyTowers
{
    private final PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:**.java");
    private final JavaParser jvParser;

    public FaultyTowers() {
        // Set configuration
        ParserConfiguration parseConfig = new ParserConfiguration();
        parseConfig.setCharacterEncoding(StandardCharsets.UTF_8);
        parseConfig.setTabSize(4);
        parseConfig.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_8);

        // Get the parser
        jvParser = new JavaParser(parseConfig);
    }

    // Recursively walk the directory hierarchy and parse all .java files.
    private void parseDirectory(String directory) throws  IOException {
        List<Path> paths = Files.walk(Paths.get(directory))
                .filter(Files::isRegularFile)
                .filter(matcher::matches)
                .collect(Collectors.toList());

       for (Path path : paths)  {
           //System.out.println(path.toAbsolutePath());
           parse(path);
       }
    }

    /**
     * Parse a file.
     *
     * @param path the path of the file to parse.
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
     * Parse a compilation unit and collect all throw statements.
     *
     * @param compilationUnit the compilation unit to parse.
     */
    private void parseCompilationUit(CompilationUnit compilationUnit) {
        List<ClassOrInterfaceDeclaration> classes = compilationUnit.findAll(ClassOrInterfaceDeclaration.class).stream()
                .filter(c -> !c.isInterface())
                .collect(Collectors.toList());

        for (ClassOrInterfaceDeclaration c : classes) {
            List<MethodDeclaration> methods = c.getMethods();
            for (MethodDeclaration method : methods) {
                Optional <BlockStmt> body = method.getBody();

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
                        System.out.println(exceptionName);
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

   public static void main(String[] args) throws IOException {
        String directory = args[0];
        FaultyTowers ft = new FaultyTowers();
        ft.parseDirectory(directory);
    }

    public int numFaults()
    {
        return faults.stream()
                .map(f -> f.exceptions.size())
                .reduce(0, Integer::sum);
    }
}
