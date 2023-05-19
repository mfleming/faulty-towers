package com.datastax.faultytowers;

import com.google.common.annotations.VisibleForTesting;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class CoverageAnalyzer {
    protected List<FileCoverage> coverageList = new ArrayList<>();

    /**
     * Return the percentage of time spent in {@code methodName} from {@code className}.
     * @param className The name of the class containing {@code methodName}.
     * @param methodName The method name.
     * @return The percentage of time spent in {@code methodName}.
     */
    public Double getCoverage(String className, String methodName) {
        return null;
    }

    /**
     * Return a list of FileCoverage objects.
     * @return The list of FileCoverage objects.
     */
    public List<FileCoverage> getAllCoverage() {
        return coverageList;
    }

    public void writeCoverageFile(String filename) throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(filename)) {
            writeCoverageData(outputStream);
        }
    }

    /**
     * Write all coverage data to {@code filename}.
     *
     * <p>The format of the file is that each line contains:</p>
     * <p>method name: line: coverage</p>
     * <p>Each line is separated by a newline character.</p>
     *
     * @param outputStream
     * @throws IOException
     */
    @VisibleForTesting
    public void writeCoverageData(FileOutputStream outputStream) throws IOException {
        for (FileCoverage fc : coverageList) {
            for (String method : fc.getMethods()) {
                String dataString = method + "\n";
                byte[] data = dataString.getBytes(StandardCharsets.UTF_8);
                outputStream.write(data);
            }
        }
    }

    public void read(String inputFile) throws IOException {
        FileInputStream inputStream = new FileInputStream(inputFile);
        byte[] data = inputStream.readAllBytes();
        String dataString = new String(data);

        // TODO this doesn't work for multiple files
        String[] records = dataString.split("\n");
        FileCoverage fileCoverage = new FileCoverage();

        for (String s : records) {
            fileCoverage.addMethod(s);
        }
        coverageList.add(fileCoverage);
    }
}
