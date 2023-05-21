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
        ExceptionThrower.THROW_LIMIT = 10;
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
            assertTrue("Failed to throw CheckedException", caughtCheckedException);
        }
    }

    @Test
    public void checkedExceptionWithArgsThrows() {
        boolean caughtCheckedException = false;
        try {
            Utils.throwGuardedCheckedException("Error");
        } catch (Utils.CheckedException e) {
            caughtCheckedException = true;
        } finally {
            assertTrue("Failed to throw CheckedException", caughtCheckedException);
        }
    }

    @Test
    public void checkedExceptionWithArgsAndCauseThrows() {
        boolean caughtCheckedException = false;
        try {
            Utils.throwGuardedCheckedException("Error", new Exception());
        } catch (Utils.CheckedException e) {
            caughtCheckedException = true;
        } finally {
            assertTrue("Failed to throw CheckedException", caughtCheckedException);
        }
    }

    @Test
    public void uncheckedExceptionThrows() {
        boolean caughtUncheckedException = false;
        try {
            Utils.throwGuardedUncheckException();
        } catch (Utils.UncheckedException e) {
            caughtUncheckedException = true;
        } finally {
            assertTrue("Failed throw UncheckedException", caughtUncheckedException);
        }
    }

    @Test
    public void uncheckedExcpetionWithoutDefaultConstructorThrows() {
        boolean caughtConcreteException = false;
        try {
            // Fight the JVM's dead code elimination
            boolean shouldThrow = false;
            Utils.throwGuardedConcreteException(shouldThrow);
        } catch (Utils.ConcreteException e) {
            caughtConcreteException = true;
        } finally {
            assertTrue("Failed to throw ConcreteException", caughtConcreteException);
        }
    }

    @Test
    public void throwLimitCapsMaxNumberOfThrows() {
        long oldThrowLimit = ExceptionThrower.THROW_LIMIT;
        ExceptionThrower.THROW_LIMIT = 0;
        boolean caughtCheckedException = false;
        try {
            Utils.throwGuardedCheckedException();
        } catch (Utils.CheckedException e) {
            caughtCheckedException = true;
        } finally {
            assertFalse("Failed to throw CheckedException", caughtCheckedException);
        }
        ExceptionThrower.THROW_LIMIT = oldThrowLimit;
    }
}
