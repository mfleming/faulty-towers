package com.datastax.faultytowers;

/**
 * <p>Utility methods for testing. These methods will not throw exceptions when invoked because the
 * throw is guarded by a condition that is always false. This is to ensure that the agent is
 * correctly injecting the throw statements.</p>
 */
public class Utils {
    public static void spinnerMethod(int count) {
        for (int i = 0; i < count; i++) {
            ;
        }
    }

    public static class UncheckedException extends RuntimeException {}

    public static void throwGuardedUncheckException() {
        if (false)
            throw new UncheckedException();
    }

    public static class CheckedException extends Exception {}

    public static void throwGuardedCheckedException() throws CheckedException {
        if (false)
            throw new CheckedException();
    }
}
