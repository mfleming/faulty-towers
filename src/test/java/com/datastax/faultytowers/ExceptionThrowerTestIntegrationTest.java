package com.datastax.faultytowers;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExceptionThrowerTestIntegrationTest {

    private static final double THROW_PROBABILITY = 1.0;

    @BeforeClass
    public static void setUp() {
        System.out.println("Setting up");
        FaultyTowers.installAgent(THROW_PROBABILITY);
    }

    @AfterClass
    public static void tearDown() {
        FaultyTowers.removeAgent();
    }

    @Test
    public void checkedExceptionThrows() {
        boolean caughtCheckedException = false;
        try {
            Utils.throwGuardedCheckedException();
        } catch (Utils.CheckedException e) {
            caughtCheckedException = true;
        } finally {
            assertTrue(caughtCheckedException);
        }
    }

    // In the future we probably want to also inject and throw unchecked exceptions
    @Test
    public void uncheckedExceptionDoesntThrow() {
        boolean caughtUncheckedException = false;
        try {
            Utils.throwGuardedUncheckException();
        } catch (Utils.UncheckedException e) {
            caughtUncheckedException = true;
        } finally {
            assertFalse(caughtUncheckedException);
        }
    }
}
