package com.datastax.faultytowers;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class FaultyTowersTest
{
    private static FaultyTowers ft;

    @BeforeClass
    public static void setUp() {
        ft = new FaultyTowers();
    }

    @Test
    public void codeWithNoThrowsHasNoFaults() {
        String code = "class Foobar {}";
        InputStream is = new ByteArrayInputStream(code.getBytes(StandardCharsets.UTF_8));
        ft.parse(is);
        assertEquals(0, ft.numFaults());
    }

    @Test
    public void codeWithThrowsHasNonZeroFaults() {
        String code = "class Foobar { void bar() { if (something) throw new RuntimeException(); if (somethingElse) throw new Exception(); } }";
        InputStream is = new ByteArrayInputStream(code.getBytes(StandardCharsets.UTF_8));
        ft.parse(is);
        assertEquals(2, ft.numFaults());
    }
}
