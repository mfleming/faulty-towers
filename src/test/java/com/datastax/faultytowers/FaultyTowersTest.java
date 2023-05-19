package com.datastax.faultytowers;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class FaultyTowersTest {
    private static FaultyTowers ft;

    @BeforeClass
    public static void setUp() {
        ft = new FaultyTowers(null);
    }

    @Test
    public void codeWithNoThrowsHasNoFaults() {
        String code = "class Foobar {}";
        InputStream is = new ByteArrayInputStream(code.getBytes(StandardCharsets.UTF_8));
        ft.parse(is);
        assertEquals(0, ft.numFaultLocations());
    }

    @Test
    public void codeWithThrowsHasNonZeroFaults() {
        String code = " class Foobar {\n" +
                      "  void bar() {\n" +
                      "     if (something)\n" +
                      "         throw new RuntimeException();\n" +
                      "     if (somethingElse)\n" +
                      "         throw new Exception();\n" +
                      " }\n" +
                    "}\n";
        InputStream is = new ByteArrayInputStream(code.getBytes(StandardCharsets.UTF_8));
        ft.parse(is);
        assertEquals(2, ft.numFaultLocations());
    }
}
