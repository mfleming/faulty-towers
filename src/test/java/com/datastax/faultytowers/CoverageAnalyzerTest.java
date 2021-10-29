package com.datastax.faultytowers;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static junit.framework.TestCase.*;
import static org.junit.Assert.assertNotEquals;

public class CoverageAnalyzerTest {

    private static final String OUTPUTFILE = "writeOutputFile.txt";

    @Before
    public void setUp() throws IOException {
        // Delete contents of file
        new FileOutputStream(OUTPUTFILE).close();
    }

    @AfterClass
    public static void cleanup() {
        File f = new File(OUTPUTFILE);
        f.delete();
    }

    /*
     * A mocked Coverage Analyzer that takes a list of methods and builds a single FileCoverage
     * object with all methods.
     */
    private static class SingleFileCoverageAnalyzer extends CoverageAnalyzer {
        public SingleFileCoverageAnalyzer(List<String> methods) {
            if (methods == null)
                return;

            FileCoverage fileCoverage = new FileCoverage();
            for (String method : methods) {
                fileCoverage.addMethod(method);
            }

            coverageList.add(fileCoverage);
        }
    }

    @Test
    public void allMethodsHaveCoverage() {
        List<String> methods = Arrays.asList("methodOne", "method2", "lastMethod");
        CoverageAnalyzer analyzer = new SingleFileCoverageAnalyzer(methods);

        List<FileCoverage> coverage = analyzer.getAllCoverage();
        List<String> coverageMethods = new ArrayList<>();
        for (FileCoverage fc : coverage) {
            coverageMethods.addAll(fc.getMethods());
        }
        assertEquals(methods, coverageMethods);
    }

    @Test
    public void noCoverageWritesEmptyFile() throws IOException {
        SingleFileCoverageAnalyzer analyzer = new SingleFileCoverageAnalyzer(null);
        analyzer.write(OUTPUTFILE);
        assertOutputFileExists();
    }

    private void assertOutputFileExists() {
        File f = new File(OUTPUTFILE);
        assertTrue(f.exists());
    }

    @Test
    public void coverageDataWritesNonEmptyFile() throws IOException {
        List<String> methods = Arrays.asList("method1", "method2");
        SingleFileCoverageAnalyzer analyzer = new SingleFileCoverageAnalyzer(methods);
        analyzer.write(OUTPUTFILE);
        assertOutputFileExists();
        assertNotEquals(0, new File(OUTPUTFILE).length());
    }

    @Test
    public void canReadAndParseCoverageData() throws IOException {
        String data = "method1";
        String inputFile = "dataFileToParse.dat";

        FileOutputStream outputStream = new FileOutputStream(inputFile);
        outputStream.write(data.getBytes(StandardCharsets.UTF_8));
        outputStream.close();

        CoverageAnalyzer analyzer = new CoverageAnalyzer();
        analyzer.read(inputFile);
        List<String> readMethods = analyzer.getAllCoverage().stream().map(f -> f.getMethods()).flatMap(List::stream).collect(Collectors.toList());
        assertEquals(Arrays.asList(data), readMethods);
    }
}
