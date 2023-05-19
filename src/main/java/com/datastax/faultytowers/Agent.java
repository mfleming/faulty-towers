package com.datastax.faultytowers;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.List;

import org.jacoco.core.runtime.IRuntime;
import org.jacoco.core.runtime.ModifiedSystemClassRuntime;
import org.jacoco.core.runtime.RuntimeData;

public class Agent {
    private static IRuntime runtime;
    private static volatile RuntimeData runtimeData;
    private static FaultyTowers faultyTowers;

    /**
     * Entry point for statically loading the agent via -javaagent.
     * @param agentArgs
     * @param inst
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("Premain called");
        agentmain(agentArgs, inst);
    }

    /**
     * Entry point for dynamically loading the agent via Attach API.
     * @param agentArgs
     * @param inst
     */
    public static void agentmain(String agentArgs, Instrumentation inst) {
        System.out.println("Agentmain called");
        try {
            // This is all commented out because of classpath issues when running with Cassandra. Namely
            // that cassandra can't find the java-parser classes.
//            initCodeCoverage(inst);

            ExceptionThrower thrower = new ExceptionThrower(List.of());
            inst.addTransformer(thrower);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static FaultyTowers getFaultyTowers() {
        return faultyTowers;
    }

    private static void initCodeCoverage(Instrumentation inst) throws Exception {
        runtime = ModifiedSystemClassRuntime.createFor(inst,
                "java/lang/UnknownError");
//             Needed?
//            runtime.startup(agent.getData());
        JaCoCoCoverageAnalyzer codeCoverage = new JaCoCoCoverageAnalyzer(runtime);
        faultyTowers = new FaultyTowers(null);
        inst.addTransformer(codeCoverage);
    }
}
