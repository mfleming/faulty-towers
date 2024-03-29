package com.datastax.faultytowers;

import com.google.common.annotations.VisibleForTesting;
import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.IOException;
import java.lang.management.ManagementFactory;

/**
 * A Java Agent that injects exceptions into the JVM.
 */
public class FaultyTowers {
    private final String pid;
    private final double throwProbability;

    public FaultyTowers(String pid, double throwProbability) {
        this.pid = pid;
        this.throwProbability = throwProbability;
    }

    public String getPid() {
        return pid;
    }

    public double getThrowProbability() {
        return throwProbability;
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
    private static void installAgent(String pid, double throwProbability) {
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
        FaultyTowers faultyTowers = buildFaultyTowers(args);
        installAgent(faultyTowers.getPid(), faultyTowers.getThrowProbability());

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

    @VisibleForTesting
    /**
     * Build a FaultyTowers object from the command line arguments.
     * @param args The command line arguments.
     * @return A FaultyTowers object. If the command line arguments are invalid, null is returned.
     */
    public static FaultyTowers buildFaultyTowers(String[] args) {
        Options options = new Options();
        options.addOption("p", "prob", true, "Probability of throwing an exception");
        options.addOption("P", "pid", true, "Process ID of target JVM");

        String pid = null;
        double throwProbability = 1.0;
        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine cmd = parser.parse(options, args);
            if (!cmd.hasOption("pid") || cmd.getOptionValue("pid").isEmpty()) {
                System.out.println("Missing --pid (-P) param");
                return null;
            }
            pid = cmd.getOptionValue("pid");

            if (cmd.hasOption("prob"))
                throwProbability = Double.parseDouble(cmd.getOptionValue("prob"));

        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }

        return new FaultyTowers(pid, throwProbability);
    }

}
