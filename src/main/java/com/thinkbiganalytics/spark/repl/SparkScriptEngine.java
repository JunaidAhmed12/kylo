package com.thinkbiganalytics.spark.repl;

import com.google.common.base.Joiner;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.spark.SparkConf;
import org.apache.spark.SparkContext;
import org.apache.spark.repl.SparkIMain;

import java.net.URLClassLoader;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import scala.collection.immutable.List;
import scala.tools.nsc.Settings;
import scala.tools.nsc.interpreter.Results;

/**
 * Evaluates Scala scripts using the Spark REPL interface.
 */
public class SparkScriptEngine extends ScriptEngine {

    private static final Logger LOG = LogManager.getLogger(SparkScriptEngine.class);

    /** Spark configuration */
    @Nonnull
    private final SparkConf conf;

    /** Spark REPL interface */
    @Nullable
    private SparkIMain interpreter;

    /**
     * Constructs a {@code SparkScriptEngine} with the specified Spark configuration.
     *
     * @param conf the Spark configuration
     */
    SparkScriptEngine(@Nonnull final SparkConf conf) {
        this.conf = conf;
    }

    @Override
    public void init() {
        getInterpreter();
    }

    @Nonnull
    @Override
    protected SparkContext createSparkContext() {
        this.conf.set("spark.repl.class.uri", getInterpreter().classServerUri());
        return new SparkContext(this.conf);
    }

    @Override
    protected void execute(@Nonnull final String script) {
        LOG.debug("Executing script:\n{}", script);
        getInterpreter().interpret(script);
    }

    /**
     * Gets the Spark REPL interface to be used.
     *
     * @return the interpreter
     */
    @Nonnull
    private SparkIMain getInterpreter() {
        if (this.interpreter == null) {
            // Determine engine settings
            Settings settings = new Settings();

            if (settings.classpath().isDefault()) {
                String classPath = Joiner.on(':').join(((URLClassLoader) getClass().getClassLoader()).getURLs()) + ":" + System
                        .getProperty("java.class.path");
                settings.classpath().value_$eq(classPath);
            }

            // Initialize engine
            final ClassLoader parentClassLoader = getClass().getClassLoader();
            SparkIMain interpreter = new SparkIMain(settings, getPrintWriter(), false) {
                @Override
                public ClassLoader parentClassLoader() {
                    return parentClassLoader;
                }
            };

            interpreter.setContextClassLoader();
            interpreter.initializeSynchronous();

            // Setup environment
            Results.Result result = interpreter.bind("engine", SparkScriptEngine.class.getName(), this, List.<String>empty());
            if (result instanceof Results.Error$) {
                throw new IllegalStateException("Failed to initialize interpreter");
            }

            this.interpreter = interpreter;
        }
        return this.interpreter;
    }
}
