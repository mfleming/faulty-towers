package com.datastax.faultytowers;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static junit.framework.TestCase.assertTrue;

public class ExceptionThrowerTestIntegrationTest {

    private static FaultyTowers ft;

    @BeforeClass
    public static void setUp() {
        ft = FaultyTowers.installAgent();
    }

    @AfterClass
    public static void tearDown() {
        FaultyTowers.removeAgent();
    }

    @Test
    public void methodWithoutThrowsDoesntThrow() {
        boolean caughtCheckedException = false;
        try {
            Utils.throwGuardedCheckedException();
        } catch (Utils.CheckedException e) {
            caughtCheckedException = true;
        } finally {
            assertTrue(caughtCheckedException);
        }
    }
}
