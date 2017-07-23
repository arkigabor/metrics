package com.codahale.metrics.jmx;

import java.lang.management.ManagementFactory;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckFilter;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.codahale.metrics.health.HealthCheckRegistryListener;
import com.codahale.metrics.DefaultObjectNameFactory;
import com.codahale.metrics.ObjectNameFactory;

/**
 *
 */
public class JmxHealthCheckReporter extends AbstractJmxReporter {
    private final HealthCheckRegistry registry;
    private final MBeanRegistrationSupport mBeanRegistrationSupport;
    private final JmxHealthCheckListener listener;

    private JmxHealthCheckReporter(HealthCheckRegistry registry, HealthCheckFilter filter, MBeanServer mBeanServer,
                                   ObjectNameFactory objectNameFactory, String domain) {
        this.registry = registry;
        this.mBeanRegistrationSupport = new MBeanRegistrationSupport(mBeanServer, objectNameFactory, domain);
        listener = new JmxHealthCheckListener(mBeanRegistrationSupport, filter);
    }

    /**
     * Returns a new {@link Builder} for {@link JmxHealthCheckReporter}.
     *
     * @param registry the registry to report
     * @return a {@link Builder} instance for a {@link JmxHealthCheckReporter}
     */
    public static Builder forRegistry(HealthCheckRegistry registry) {
        return new Builder(registry);
    }

    @Override
    public void start() {
        registry.addListener(listener);
    }

    @Override
    public void stop() {
        registry.removeListener(listener);
        mBeanRegistrationSupport.unregisterAll();
    }

    /**
     * A builder for {@link JmxHealthCheckReporter} instances. Defaults to using the default MBean server and
     * not filtering health checks.
     */
    public static class Builder {
        private final HealthCheckRegistry registry;
        private MBeanServer mBeanServer;
        private ObjectNameFactory objectNameFactory;
        private HealthCheckFilter filter;
        private String domain;

        private Builder(HealthCheckRegistry registry) {
            this.registry = registry;
            this.objectNameFactory = new DefaultObjectNameFactory();
            this.filter = HealthCheckFilter.ALL;
            this.domain = "health";
        }

        /**
         * Register MBeans with the given {@link MBeanServer}.
         *
         * @param mBeanServer an {@link MBeanServer}
         * @return {@code this}
         */
        public Builder registerWith(MBeanServer mBeanServer) {
            if (mBeanServer == null) {
                throw new IllegalArgumentException("mBeanServer was null");
            }
            this.mBeanServer = mBeanServer;
            return this;
        }

        /**
         * Create object names with the given {@link ObjectNameFactory}.
         *
         * @param objectNameFactory an {@link ObjectNameFactory}
         * @return {@code this}
         */
        public Builder createsObjectNamesWith(ObjectNameFactory objectNameFactory) {
            if (objectNameFactory == null) {
                throw new IllegalArgumentException("objectNameFactory was null");
            }
            this.objectNameFactory = objectNameFactory;
            return this;
        }

        /**
         * Only report health checks which match the given filter.
         *
         * @param filter a {@link HealthCheckFilter}
         * @return {@code this}
         */
        public Builder filter(HealthCheckFilter filter) {
            if (filter == null) {
                throw new IllegalArgumentException("filter was null");
            }
            this.filter = filter;
            return this;
        }

        /**
         * Register MBeans under the given domain.
         *
         * @param domain the domain
         * @return {@code this}
         */
        public Builder inDomain(String domain) {
            if (domain == null || domain.isEmpty()) {
                throw new IllegalArgumentException("domain was blank");
            }
            this.domain = domain;
            return this;
        }

        /**
         * Builds a {@link JmxHealthCheckReporter} with the given properties.
         *
         * @return a {@link JmxHealthCheckReporter}
         */
        public JmxHealthCheckReporter build() {
            if (mBeanServer == null) {
                mBeanServer = ManagementFactory.getPlatformMBeanServer();
            }
            return new JmxHealthCheckReporter(registry, filter, mBeanServer, objectNameFactory, domain);
        }
    }

    /**
     *
     */
    public interface JmxHealthCheckMBean {
        String execute();
    }

    private static class JmxHealthCheck implements JmxHealthCheckMBean {
        private final HealthCheck healthCheck;
        private HealthCheck.Result cachedResult;
        // TODO: add proper caching

        private JmxHealthCheck(HealthCheck healthCheck) {
            this.healthCheck = healthCheck;
        }

        public boolean isHealthy() {
            return false;
        }

        @Override
        public String execute() {
            return healthCheck.execute().toString();
        }

        private synchronized HealthCheck.Result getResult() {
            if (cachedResult == null || shouldReload()) {
                cachedResult = healthCheck.execute();
            }
            return cachedResult;
        }

        private boolean shouldReload() {
            return false;
        }
    }

    private static class JmxHealthCheckListener implements HealthCheckRegistryListener {
        private final MBeanRegistrationSupport mBeanRegistrationSupport;
        private final HealthCheckFilter filter;

        JmxHealthCheckListener(MBeanRegistrationSupport mBeanRegistrationSupport, HealthCheckFilter filter) {
            this.mBeanRegistrationSupport = mBeanRegistrationSupport;
            this.filter = filter;
        }

        @Override
        public void onHealthCheckAdded(String name, HealthCheck healthCheck) {
            if (filter.matches(name, healthCheck)) {
                final ObjectName objectName = mBeanRegistrationSupport.createName("healthcheck", name);
                mBeanRegistrationSupport.registerMBean(new JmxHealthCheck(healthCheck), objectName);
            }
        }

        @Override
        public void onHealthCheckRemoved(String name, HealthCheck healthCheck) {
            mBeanRegistrationSupport.unregisterMBean(mBeanRegistrationSupport.createName("healthcheck", name));
        }

    }
}
