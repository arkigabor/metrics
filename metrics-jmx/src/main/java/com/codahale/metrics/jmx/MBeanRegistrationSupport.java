package com.codahale.metrics.jmx;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Support class helping to manage MBeans.
 */
public class MBeanRegistrationSupport {
    final Logger LOGGER = LoggerFactory.getLogger(MBeanRegistrationSupport.class);
    private final MBeanServer mBeanServer;
    private final ObjectNameFactory objectNameFactory;
    private final String domain;
    private final Map<ObjectName, ObjectName> registered;

    /**
     * Creates a new {@link MBeanRegistrationSupport}.
     *
     * @param mBeanServer       an {@link MBeanServer}
     * @param objectNameFactory an {@link ObjectNameFactory}
     * @param domain            a domain
     */
    public MBeanRegistrationSupport(MBeanServer mBeanServer, ObjectNameFactory objectNameFactory, String domain) {
        this.mBeanServer = mBeanServer;
        this.objectNameFactory = objectNameFactory;
        this.domain = domain;
        this.registered = new ConcurrentHashMap<>();
    }

    /**
     * Create an {@link ObjectName}.
     *
     * @param type type attribute
     * @param name name attribute
     * @return the created {@link ObjectName}
     */
    public ObjectName createName(String type, String name) {
        return objectNameFactory.createName(type, domain, name);
    }

    /**
     * Register an MBean with the provided name.
     *
     * @param mBean      an MBean
     * @param objectName an {@link ObjectName}
     */
    public void registerMBean(Object mBean, ObjectName objectName) {
        ObjectName registeredName = objectName;
        try {
            ObjectInstance objectInstance = mBeanServer.registerMBean(mBean, objectName);
            if (objectInstance != null) {
                registeredName = objectInstance.getObjectName();
            }
        } catch (InstanceAlreadyExistsException e) {
            LOGGER.debug("Unable to register MBean", e);
        } catch (MBeanRegistrationException | NotCompliantMBeanException e) {
            LOGGER.warn("Unable to register MBean", e);
        }
        registered.put(objectName, registeredName);
    }

    /**
     * Unregister an MBean with the provided name.
     *
     * @param objectName an {@link ObjectName}
     */
    public void unregisterMBean(ObjectName objectName) {
        try {
            mBeanServer.unregisterMBean(registered.getOrDefault(objectName, objectName));
        } catch (InstanceNotFoundException e) {
            LOGGER.warn("Unable to unregister MBean", e);
        } catch (MBeanRegistrationException e) {
            LOGGER.warn("Unable to unregister MBean", e);
        }
    }

    /**
     * Unregister all previously registered MBeans.
     */
    public void unregisterAll() {
        for (ObjectName name : registered.keySet()) {
            unregisterMBean(name);
        }
        registered.clear();
    }
}
