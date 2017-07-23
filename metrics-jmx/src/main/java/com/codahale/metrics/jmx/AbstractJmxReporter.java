package com.codahale.metrics.jmx;

import com.codahale.metrics.Reporter;

/**
 *
 */
public abstract class AbstractJmxReporter implements Reporter, AutoCloseable {

    @Override
    public void close() {
        stop();
    }

    /**
     *
     */
    public abstract void start();

    /**
     *
     */
    public abstract void stop();

}
