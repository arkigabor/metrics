package com.codahale.metrics.jmx;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link ObjectNameFactory} creating names using only <b>name</b> besides domain resulting in a flat object structure.
 */
public class DefaultObjectNameFactory implements ObjectNameFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultObjectNameFactory.class);
    private static final String NAME = "name";

    @Override
    public ObjectName createName(String type, String domain, String name) {
        ObjectName result;
        try {
            result = new ObjectName(domain, NAME, name);
            if (result.isPattern()) {
                result = new ObjectName(domain, NAME, ObjectName.quote(name));
            }
        } catch (MalformedObjectNameException e) {
            try {
                result = new ObjectName(domain, NAME, ObjectName.quote(name));
            } catch (MalformedObjectNameException e1) {
                LOGGER.warn("Unable to create name with type={} domain={} name={}", type, domain, name, e1);
                throw new RuntimeException(e1);
            }
        }
        return result;
    }

}
