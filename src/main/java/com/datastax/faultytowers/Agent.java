package com.datastax.faultytowers;

import java.io.IOException;
import java.lang.instrument.Instrumentation;

import org.jacoco.core.runtime.IRuntime;
import org.jacoco.core.runtime.ModifiedSystemClassRuntime;
import org.jacoco.core.runtime.RuntimeData;

public class Agent {
    private static IRuntime runtime;
    private static volatile RuntimeData runtimeData;
    private static FaultyTowers faultyTowers;

    public static void premain(String agentArgs, Instrumentation inst) {
    }

    public static FaultyTowers getFaultyTowers() {
        return faultyTowers;
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        try {
            runtime = ModifiedSystemClassRuntime.createFor(inst,
                    "java/lang/UnknownError");
            // Needed?
            //runtime.startup(agent.getData());
            JaCoCoCoverageAnalyzer codeCoverage = new JaCoCoCoverageAnalyzer(runtime);
            faultyTowers = new FaultyTowers(codeCoverage);
            inst.addTransformer(codeCoverage);
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
