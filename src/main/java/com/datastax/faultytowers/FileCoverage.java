package com.datastax.faultytowers;

import java.util.ArrayList;
import java.util.List;

/**
 * A record of the code coverage data for a single file. Every method in the file will have code
 * coverage data. If lines in the method aren't executed, then the coverage will be 0.
 */
public class FileCoverage {
    List<String> methods;

    public FileCoverage() {
        methods = new ArrayList<>();
    }

    public void addMethod(String methodName) {
        methods.add(methodName);
    }

    /**
     * Return the list of all methods.
     * @return
     */
    List<String> getMethods() {
        return methods;
    }
}
