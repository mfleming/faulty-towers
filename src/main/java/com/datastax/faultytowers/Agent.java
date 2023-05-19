package com.datastax.faultytowers;

import java.lang.instrument.Instrumentation;
import java.util.List;

public class Agent {
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
        double throwProbability = Double.parseDouble(agentArgs);
        System.out.println("Agentmain called");
        try {
            ExceptionThrower thrower = new ExceptionThrower(List.of(), throwProbability);
            faultyTowers = new FaultyTowers();
            inst.addTransformer(thrower);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
