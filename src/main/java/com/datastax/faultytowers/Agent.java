package com.datastax.faultytowers;

import java.lang.instrument.Instrumentation;
import java.util.List;

public class Agent {
    /**
     * Entry point for statically loading the agent via -javaagent.
     * @param agentArgs The probability of throwing an exception
     * @param inst
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("Premain called");
        agentmain(agentArgs, inst);
    }

    /**
     * Entry point for dynamically loading the agent via Attach API.
     * @param agentArgs The probability of throwing an exception
     * @param inst The instrumentation object
     */
    public static void agentmain(String agentArgs, Instrumentation inst) {
        System.out.println("Agentmain called");
        double throwProbability = Double.parseDouble(agentArgs);
        try {
            ExceptionThrower thrower = new ExceptionThrower(List.of(), throwProbability);
            inst.addTransformer(thrower);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
