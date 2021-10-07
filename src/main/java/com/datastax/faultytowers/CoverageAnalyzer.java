package com.datastax.faultytowers;

public interface CoverageAnalyzer {

    /**
     * Return the percentage of time spent in {@code methodName} from {@code className}.
     * @param className The name of the class containing {@code methodName}.
     * @param methodName The method name.
     * @return The percentage of time spent in {@code methodName}.
     */
    Double getCoverage(String className, String methodName);
}
