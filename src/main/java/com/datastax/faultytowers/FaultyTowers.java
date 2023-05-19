package com.datastax.faultytowers;

import com.google.common.annotations.VisibleForTesting;
import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.List;

/**
 * <p>FaultyTowers is the main entry point to the application.</p>
 *
 * <p>Depending on which mode we're executing in, we're either collecting code coverage data for a
 * subsequent run or profiling a workload until we have enough samples that we can inject faults
 * without needing to restart the workload.</p>
 *
 * <p><b>offline-mode:</b> This is used when injecting faults into short-running tests, e.g. JUnit tests.
 * This mode uses 2 phases. First, we collect coverage data for the JUnit test which we writeCoverageData to
 * a file once the JUnit tests have finished. In the second step, the file is read, the JUnit tests
 * are executed again, and faults are injected.</p>
 *
 * <p><b>online-mode:</b> This mode is used when injecting faults into long-running tests, e.g. performance
 * workloads. Here we don't writeCoverageData out intermediate coverage data to a file. Instead, we profile
 * the workload at runtime and then inject faults.</p>
 */
public class FaultyTowers {
    public FaultyTowers() {

    }

    /**
     * Install the Java Agent into the current JVM and return the FaultyTowers object.
     */
    @VisibleForTesting
    public static void installAgent(double throwProbability) {
        String pid = null;
        String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
        int processIdIndex = runtimeName.indexOf('@');
        if (processIdIndex == -1) {
            throw new IllegalStateException("Cannot extract process id from runtime management bean");
        } else {
            pid = runtimeName.substring(0, processIdIndex);
        }
        installAgent(pid, throwProbability);
    }

    /**
     * Install the Java Agent into the JVM specified by pid and return the FaultyTowers object.
     *
     * @param pid The target JVM process id.
     */
    public static void installAgent(String pid, double throwProbability) {
        assert pid != null;

        try {
            System.out.println("Attaching to " + pid);
            VirtualMachine vm = VirtualMachine.attach(pid);
            String agentPath = System.getProperty("user.dir") + "/target/faulty-towers-1.0-SNAPSHOT.jar";

            vm.loadAgent(agentPath, String.valueOf(throwProbability));
            System.out.println("Agent loaded");
//            return (FaultyTowers) Class.forName(Agent.class.getName(), true, ClassLoader.getSystemClassLoader())
//                    .getMethod("getFaultyTowers")
//                    .invoke(null);

        } catch (AttachNotSupportedException e) {
            System.out.println("Attach not supported");
            e.printStackTrace();
        } catch (AgentLoadException |AgentInitializationException | IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Remove the Java Agent from the target VM.
     */
    public static void removeAgent() {
        // TODO
    }

    public static void main(String[] args) {
        if (args.length != 5) {
            System.out.println("got " + args.length + " args");
            System.out.println("Usage: java -jar faulty-towers.jar <-p probability> -P <pid>");
            System.exit(1);
        }

        // Parse --prob (-p) for the probability of throwing an exception.
        double throwProbability = 0.0;
        String pid = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--prob") || args[i].equals("-p")) {
                throwProbability = Double.parseDouble(args[i+1]);
            }

            if (args[i].equals("--pid") || args[i].equals("-P")) {
                pid = args[i+1];
            }
        }

        if (pid == null) {
            System.out.println("No --pid (-P) specified");
            System.exit(1);
        }

        installAgent(pid, throwProbability);

        // Wait for Ctrl-C
        System.out.println("Press Ctrl-C to exit");
        try {
            // noinspection InfiniteLoopStatement
            while (true) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            // Ignore
        }
        FaultyTowers.removeAgent();
    }
}
