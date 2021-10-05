package com.datastax.faultytowers;

import java.io.IOException;
import java.lang.instrument.Instrumentation;

import org.jacoco.core.runtime.IRuntime;
import org.jacoco.core.runtime.ModifiedSystemClassRuntime;
import org.jacoco.core.runtime.RuntimeData;

public class Agent {
    private static IRuntime RUNTIME;
    private static volatile RuntimeData DATA;

    public static void premain(String agentArgs, Instrumentation inst) {
    }

    public static RuntimeData getData() {
        return DATA;
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        try {
            RUNTIME = ModifiedSystemClassRuntime.createFor(inst,
                    "java/lang/UnknownError");
            // Needed?
            //runtime.startup(agent.getData());
            DATA = new RuntimeData();
            inst.addTransformer(new CodeCoverage(RUNTIME, DATA));
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
