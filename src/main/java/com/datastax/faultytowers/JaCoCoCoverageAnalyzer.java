package com.datastax.faultytowers;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.List;

import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.IMethodCoverage;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.SessionInfoStore;
import org.jacoco.core.instr.Instrumenter;
import org.jacoco.core.runtime.IRuntime;
import org.jacoco.core.runtime.RuntimeData;

public class JaCoCoCoverageAnalyzer extends CoverageAnalyzer implements ClassFileTransformer {

    private Instrumenter instrumenter;
    private IRuntime runtime;
    private RuntimeData runtimeData;

    public JaCoCoCoverageAnalyzer(IRuntime runtime) throws Exception {
        this.runtime = runtime;
        this.runtimeData = new RuntimeData();
        runtime.startup(runtimeData);
        instrumenter = new Instrumenter(runtime);
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
                || classname.contains("junit/") || classname.contains("org/jacoco"))
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

    @Override
    public Double getCoverage(String className, String methodName) {
        try {
            final ExecutionDataStore executionDataStore = new ExecutionDataStore();
            final SessionInfoStore sessionInfoStore = new SessionInfoStore();
            runtimeData.collect(executionDataStore, sessionInfoStore, false);
            final CoverageBuilder coverageBuilder = new CoverageBuilder();
            final Analyzer analyzer = new Analyzer(executionDataStore, coverageBuilder);

            String absClassName = "/" + className.replace(".", "/") + ".class";
            analyzer.analyzeClass(getClass().getResourceAsStream(absClassName), absClassName);
            final IClassCoverage cc = coverageBuilder.getClasses().stream().findFirst().orElse(null);
            if (cc == null)
                return null;

            cc.getMethodCounter().getCoveredCount();
            for (final IMethodCoverage mc : cc.getMethods()) {
                if (mc.getName().equals(methodName))
                    return mc.getMethodCounter().getCoveredRatio();
            }
            return null;

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public List<FileCoverage> getAllCoverage() {
        return null;
    }
}
