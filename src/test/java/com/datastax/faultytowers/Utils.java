package com.datastax.faultytowers;

public class Utils {
    public static void spinnerMethod(int count) {
        for (int i = 0; i < count; i++) {
            ;
        }
    }

    public static class CheckedException extends Exception {}

    public static void throwGuardedCheckedException() throws CheckedException {
        if (false)
            throw new CheckedException();
    }
}
