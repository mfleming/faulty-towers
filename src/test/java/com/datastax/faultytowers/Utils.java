package com.datastax.faultytowers;

/**
 * <p>Utility methods for testing. These methods will not throw exceptions when invoked because the
 * throw is guarded by a condition that is always false. This is to ensure that the agent is
 * correctly injecting the throw statements.</p>
 */
public class Utils {
    //
    // Checked exceptions
    //
    public static class CheckedException extends Exception {
        public CheckedException() {
            super();
        }

        public CheckedException(String message) {
            super(message);
        }

        public CheckedException(String error, Throwable cause) {
            super(error, cause);
        }
    }

    public static void spinnerMethod(int count) {
        for (int i = 0; i < count; i++) {
            ;
        }
    }

    public static void throwGuardedCheckedException(String error) throws CheckedException {
        if (false)
            throw new CheckedException(error);
    }

    public static void throwGuardedCheckedException(String error, Throwable cause) throws CheckedException {
        if (false)
            throw new CheckedException(error, cause);
    }



    public static void throwGuardedCheckedException() throws CheckedException {
        // noinspection ConstantConditions
        if (false)
            throw new CheckedException();
    }

    //
    // Unchecked exceptions
    //
    public static class UncheckedException extends RuntimeException {}

    public static void throwGuardedUncheckException() throws UncheckedException {
        // noinspection ConstantConditions
        if (false)
            throw new UncheckedException();
    }

    static abstract class AbstractException extends RuntimeException {
        public AbstractException(int code) {
            super();
        }
    }

     public static class ConcreteException extends AbstractException {
        public ConcreteException(int code) {
            super(code);
        }
    }

    public static void throwGuardedConcreteException(boolean shouldThrow) {
        // Trick the JVM's dead code elinimation
        if (shouldThrow)
            throw new ConcreteException(2);
    }
}
