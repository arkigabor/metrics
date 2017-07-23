package com.codahale.metrics.jmx;

import javax.management.ObjectName;

/**
 * A contract for MBean object name factories.
 */
public interface ObjectNameFactory {

    /**
     * Create an MBean object name.
     * @param type the type attribute
     * @param domain the domain
     * @param name the name attribute
     * @return the object name
     */
    ObjectName createName(String type, String domain, String name);
}
