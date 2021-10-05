package com.datastax.faultytowers;

import org.junit.AfterClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static junit.framework.TestCase.assertTrue;

public class CodeCoverageTest {

    private static final String OUTPUTFILE = "writeOutputFile.txt";

    @AfterClass
    public static void cleanup() {
        File f = new File(OUTPUTFILE);
        f.delete();
    }

    @Test
    public void noCoverageWritesEmptyFile() throws IOException {
        FaultyTowers ft = new FaultyTowers();
        ft.writeCoverage(OUTPUTFILE);
        File f = new File(OUTPUTFILE);
        assertTrue(f.exists());
    }
}
