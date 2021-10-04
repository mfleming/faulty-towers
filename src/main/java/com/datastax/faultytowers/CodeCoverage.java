package com.datastax.faultytowers;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.instr.Instrumenter;
import org.jacoco.core.runtime.IRuntime;
import org.jacoco.core.runtime.RuntimeData;

public class CodeCoverage implements ClassFileTransformer {

    private CoverageBuilder cb;
    private Instrumenter instrumenter;
    private IRuntime runtime;
    private RuntimeData runtimeData;

    public CodeCoverage(IRuntime runtime, RuntimeData data) throws Exception {
        this.runtime = runtime;
        this.runtimeData = data;
        runtime.startup(runtimeData);
        instrumenter = new Instrumenter(runtime);
        //InputStream is = getClassSource(className);
        //instrumenter.instrument(is, className);
        cb = new CoverageBuilder();
    }

    public byte[] transform(final ClassLoader loader, final String classname,
            final Class<?> classBeingRedefined,
            final ProtectionDomain protectionDomain,
            final byte[] classfileBuffer) throws IllegalClassFormatException {

        // We do not support class retransformation:
        if (classBeingRedefined != null) {
            return null;
        }

        // Jacoco filters out some classes here. Should we do the same?
        if (loader == null)
            return null;

        // Skip all surefire code to avoid java.lang.reflect.InvocationTargetException.
        if (classname.contains("org/apache/maven/surefire") || classname.contains("org/junit")
                || classname.contains("org/jacoco"))
            return null;


        try {
            return instrumenter.instrument(classfileBuffer, classname);
        } catch (final Exception ex) {
            System.out.println(ex.getMessage());
            final IllegalClassFormatException wrapper = new IllegalClassFormatException(
                    ex.getMessage());
            wrapper.initCause(ex);
            // Report this, as the exception is ignored by the JVM:
            ex.printStackTrace();
            throw wrapper;
        }
    }
}
