package com.datastax.faultytowers;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

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

    @Test
    public void checkedExceptionWithArgsThrows() {
        boolean caughtCheckedException = false;
        try {
            Utils.throwGuardedCheckedException("Error");
        } catch (Utils.CheckedException e) {
            caughtCheckedException = true;
        } finally {
            assertTrue(caughtCheckedException);
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
            assertTrue(caughtCheckedException);
        }
    }

    @Test
    public void uncheckedExceptionDoesntThrow() {
        boolean caughtUncheckedException = false;
        try {
            Utils.throwGuardedUncheckException();
        } catch (Utils.UncheckedException e) {
            caughtUncheckedException = true;
        } finally {
            assertTrue(caughtUncheckedException);
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
            assertTrue(caughtConcreteException);
        }
    }
}
