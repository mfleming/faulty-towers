package com.datastax.faultytowers;

import org.junit.Test;

import static org.junit.Assert.*;

public class CodeCoverageTestIntegrationTest {
    private static FaultyTowers ft;

    @Test
    public void singleMethodHas100pctCoverage() {
        ft = new FaultyTowers();
        ft.installAgent();
        Utils.spinnerMethod(1000);
        double coverage = ft.getCoverage(Utils.class.getName(), "spinnerMethod");
        assertEquals(1.0, coverage, 0);
        ft.removeAgent();
    }
}
