package com.datastax.faultytowers;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

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

    private static class MockCoverageAnalyzer implements CoverageAnalyzer {
        @Override
        public Double getCoverage(String className, String methodName) {
            return null;
        }
    }

    @Test
    public void noCoverageWritesEmptyFile() throws IOException {
        FaultyTowers ft = new FaultyTowers(new MockCoverageAnalyzer());
        ft.writeCoverage(OUTPUTFILE);
        assertOutputFileExists();
    }

    private void assertOutputFileExists() {
        File f = new File(OUTPUTFILE);
        assertTrue(f.exists());
    }

    @Test
    public void coverageDataWritesNonEmptyFile() throws IOException {
        FaultyTowers ft = new FaultyTowers(new MockCoverageAnalyzer());
        ft.writeCoverage(OUTPUTFILE);
        assertOutputFileExists();
        assertNotEquals(0, new File(OUTPUTFILE).length());
    }
}
