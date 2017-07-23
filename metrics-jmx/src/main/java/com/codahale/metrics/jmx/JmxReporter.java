package com.codahale.metrics.jmx;

import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metered;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricRegistryListener;
import com.codahale.metrics.Timer;

/**
 * A reporter which listens for new metrics and exposes them as namespaced MBeans.
 */
public class JmxReporter extends AbstractJmxReporter {
    private final MetricRegistry registry;
    private final MBeanRegistrationSupport mBeanRegistrationSupport;
    private final JmxListener listener;

    private JmxReporter(MBeanServer mBeanServer,
                        String domain,
                        MetricRegistry registry,
                        MetricFilter filter,
                        MetricTimeUnits timeUnits,
                        ObjectNameFactory objectNameFactory) {
        this.registry = registry;
        this.mBeanRegistrationSupport = new MBeanRegistrationSupport(mBeanServer, objectNameFactory, domain);
        this.listener = new JmxListener(mBeanRegistrationSupport, filter, timeUnits);
    }

    /**
     * Returns a new {@link Builder} for {@link JmxReporter}.
     *
     * @param registry the registry to report
     * @return a {@link Builder} instance for a {@link JmxReporter}
     */
    public static Builder forRegistry(MetricRegistry registry) {
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
     * A builder for {@link JmxReporter} instances. Defaults to using the default MBean server and
     * not filtering metrics.
     */
    public static class Builder {
        private final MetricRegistry registry;
        private MBeanServer mBeanServer;
        private TimeUnit rateUnit;
        private TimeUnit durationUnit;
        private ObjectNameFactory objectNameFactory;
        private MetricFilter filter = MetricFilter.ALL;
        private String domain;
        private Map<String, TimeUnit> specificDurationUnits;
        private Map<String, TimeUnit> specificRateUnits;

        private Builder(MetricRegistry registry) {
            this.registry = registry;
            this.rateUnit = TimeUnit.SECONDS;
            this.durationUnit = TimeUnit.MILLISECONDS;
            this.domain = "metrics";
            this.objectNameFactory = new DefaultObjectNameFactory();
            this.specificDurationUnits = Collections.emptyMap();
            this.specificRateUnits = Collections.emptyMap();
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
         * Convert rates to the given time unit.
         *
         * @param rateUnit a unit of time
         * @return {@code this}
         */
        public Builder convertRatesTo(TimeUnit rateUnit) {
            if (rateUnit == null) {
                throw new IllegalArgumentException("rateUnit was null");
            }
            this.rateUnit = rateUnit;
            return this;
        }

        /**
         * Create object names with the given {@link ObjectNameFactory}.
         *
         * @param onFactory an {@link ObjectNameFactory}
         * @return {@code this}
         */
        public Builder createsObjectNamesWith(ObjectNameFactory onFactory) {
            if (onFactory == null) {
                throw new IllegalArgumentException("null objectNameFactory");
            }
            this.objectNameFactory = onFactory;
            return this;
        }

        /**
         * Convert durations to the given time unit.
         *
         * @param durationUnit a unit of time
         * @return {@code this}
         */
        public Builder convertDurationsTo(TimeUnit durationUnit) {
            if (durationUnit == null) {
                throw new IllegalArgumentException("durationUnit was null");
            }
            this.durationUnit = durationUnit;
            return this;
        }

        /**
         * Only report metrics which match the given filter.
         *
         * @param filter a {@link MetricFilter}
         * @return {@code this}
         */
        public Builder filter(MetricFilter filter) {
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
         * Use specific {@link TimeUnit}s for the duration of the metrics with these names.
         *
         * @param specificDurationUnits a map of metric names and specific {@link TimeUnit}s
         * @return {@code this}
         */
        public Builder specificDurationUnits(Map<String, TimeUnit> specificDurationUnits) {
            if (specificDurationUnits == null) {
                throw new IllegalArgumentException("specificDurationUnits was null");
            }
            this.specificDurationUnits = Collections.unmodifiableMap(specificDurationUnits);
            return this;
        }


        /**
         * Use specific {@link TimeUnit}s for the rate of the metrics with these names.
         *
         * @param specificRateUnits a map of metric names and specific {@link TimeUnit}s
         * @return {@code this}
         */
        public Builder specificRateUnits(Map<String, TimeUnit> specificRateUnits) {
            if (specificRateUnits == null) {
                throw new IllegalArgumentException("specificRateUnits was null");
            }
            this.specificRateUnits = Collections.unmodifiableMap(specificRateUnits);
            return this;
        }

        /**
         * Builds a {@link JmxReporter} with the given properties.
         *
         * @return a {@link JmxReporter}
         */
        public JmxReporter build() {
            final MetricTimeUnits timeUnits = new MetricTimeUnits(rateUnit, durationUnit, specificRateUnits, specificDurationUnits);
            if (mBeanServer == null) {
                mBeanServer = ManagementFactory.getPlatformMBeanServer();
            }
            return new JmxReporter(mBeanServer, domain, registry, filter, timeUnits, objectNameFactory);
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(JmxReporter.class);

    @SuppressWarnings("UnusedDeclaration")
    public interface MetricMBean {
        ObjectName objectName();
    }

    private abstract static class AbstractBean implements MetricMBean {
        private final ObjectName objectName;

        AbstractBean(ObjectName objectName) {
            this.objectName = objectName;
        }

        @Override
        public ObjectName objectName() {
            return objectName;
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    public interface JmxGaugeMBean extends MetricMBean {
        Object getValue();
    }

    private static class JmxGauge extends AbstractBean implements JmxGaugeMBean {
        private final Gauge<?> metric;

        private JmxGauge(Gauge<?> metric, ObjectName objectName) {
            super(objectName);
            this.metric = metric;
        }

        @Override
        public Object getValue() {
            return metric.getValue();
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    public interface JmxCounterMBean extends MetricMBean {
        long getCount();
    }

    private static class JmxCounter extends AbstractBean implements JmxCounterMBean {
        private final Counter metric;

        private JmxCounter(Counter metric, ObjectName objectName) {
            super(objectName);
            this.metric = metric;
        }

        @Override
        public long getCount() {
            return metric.getCount();
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    public interface JmxHistogramMBean extends MetricMBean {
        long getCount();

        long getMin();

        long getMax();

        double getMean();

        double getStdDev();

        double get50thPercentile();

        double get75thPercentile();

        double get95thPercentile();

        double get98thPercentile();

        double get99thPercentile();

        double get999thPercentile();

        long[] values();

        long getSnapshotSize();
    }

    private static class JmxHistogram implements JmxHistogramMBean {
        private final ObjectName objectName;
        private final Histogram metric;

        private JmxHistogram(Histogram metric, ObjectName objectName) {
            this.metric = metric;
            this.objectName = objectName;
        }

        @Override
        public ObjectName objectName() {
            return objectName;
        }

        @Override
        public double get50thPercentile() {
            return metric.getSnapshot().getMedian();
        }

        @Override
        public long getCount() {
            return metric.getCount();
        }

        @Override
        public long getMin() {
            return metric.getSnapshot().getMin();
        }

        @Override
        public long getMax() {
            return metric.getSnapshot().getMax();
        }

        @Override
        public double getMean() {
            return metric.getSnapshot().getMean();
        }

        @Override
        public double getStdDev() {
            return metric.getSnapshot().getStdDev();
        }

        @Override
        public double get75thPercentile() {
            return metric.getSnapshot().get75thPercentile();
        }

        @Override
        public double get95thPercentile() {
            return metric.getSnapshot().get95thPercentile();
        }

        @Override
        public double get98thPercentile() {
            return metric.getSnapshot().get98thPercentile();
        }

        @Override
        public double get99thPercentile() {
            return metric.getSnapshot().get99thPercentile();
        }

        @Override
        public double get999thPercentile() {
            return metric.getSnapshot().get999thPercentile();
        }

        @Override
        public long[] values() {
            return metric.getSnapshot().getValues();
        }

        @Override
        public long getSnapshotSize() {
            return metric.getSnapshot().size();
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    public interface JmxMeterMBean extends MetricMBean {
        long getCount();

        double getMeanRate();

        double getOneMinuteRate();

        double getFiveMinuteRate();

        double getFifteenMinuteRate();

        String getRateUnit();
    }

    private static class JmxMeter extends AbstractBean implements JmxMeterMBean {
        private final Metered metric;
        private final double rateFactor;
        private final String rateUnit;

        private JmxMeter(Metered metric, ObjectName objectName, TimeUnit rateUnit) {
            super(objectName);
            this.metric = metric;
            this.rateFactor = rateUnit.toSeconds(1);
            this.rateUnit = ("events/" + calculateRateUnit(rateUnit)).intern();
        }

        @Override
        public long getCount() {
            return metric.getCount();
        }

        @Override
        public double getMeanRate() {
            return metric.getMeanRate() * rateFactor;
        }

        @Override
        public double getOneMinuteRate() {
            return metric.getOneMinuteRate() * rateFactor;
        }

        @Override
        public double getFiveMinuteRate() {
            return metric.getFiveMinuteRate() * rateFactor;
        }

        @Override
        public double getFifteenMinuteRate() {
            return metric.getFifteenMinuteRate() * rateFactor;
        }

        @Override
        public String getRateUnit() {
            return rateUnit;
        }

        private String calculateRateUnit(TimeUnit unit) {
            final String s = unit.toString().toLowerCase(Locale.US);
            return s.substring(0, s.length() - 1);
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    public interface JmxTimerMBean extends JmxMeterMBean {
        double getMin();

        double getMax();

        double getMean();

        double getStdDev();

        double get50thPercentile();

        double get75thPercentile();

        double get95thPercentile();

        double get98thPercentile();

        double get99thPercentile();

        double get999thPercentile();

        long[] values();

        String getDurationUnit();
    }

    static class JmxTimer extends JmxMeter implements JmxTimerMBean {
        private final Timer metric;
        private final double durationFactor;
        private final String durationUnit;

        private JmxTimer(Timer metric,
                         ObjectName objectName,
                         TimeUnit rateUnit,
                         TimeUnit durationUnit) {
            super(metric, objectName, rateUnit);
            this.metric = metric;
            this.durationFactor = 1.0 / durationUnit.toNanos(1);
            this.durationUnit = durationUnit.toString().toLowerCase(Locale.US);
        }

        @Override
        public double get50thPercentile() {
            return metric.getSnapshot().getMedian() * durationFactor;
        }

        @Override
        public double getMin() {
            return metric.getSnapshot().getMin() * durationFactor;
        }

        @Override
        public double getMax() {
            return metric.getSnapshot().getMax() * durationFactor;
        }

        @Override
        public double getMean() {
            return metric.getSnapshot().getMean() * durationFactor;
        }

        @Override
        public double getStdDev() {
            return metric.getSnapshot().getStdDev() * durationFactor;
        }

        @Override
        public double get75thPercentile() {
            return metric.getSnapshot().get75thPercentile() * durationFactor;
        }

        @Override
        public double get95thPercentile() {
            return metric.getSnapshot().get95thPercentile() * durationFactor;
        }

        @Override
        public double get98thPercentile() {
            return metric.getSnapshot().get98thPercentile() * durationFactor;
        }

        @Override
        public double get99thPercentile() {
            return metric.getSnapshot().get99thPercentile() * durationFactor;
        }

        @Override
        public double get999thPercentile() {
            return metric.getSnapshot().get999thPercentile() * durationFactor;
        }

        @Override
        public long[] values() {
            return metric.getSnapshot().getValues();
        }

        @Override
        public String getDurationUnit() {
            return durationUnit;
        }
    }

    private static class JmxListener implements MetricRegistryListener {
        private final MBeanRegistrationSupport mBeanRegistrationSupport;
        private final MetricFilter filter;
        private final MetricTimeUnits timeUnits;

        private JmxListener(MBeanRegistrationSupport mBeanRegistrationSupport, MetricFilter filter,
                            MetricTimeUnits timeUnits) {
            this.mBeanRegistrationSupport = mBeanRegistrationSupport;
            this.filter = filter;
            this.timeUnits = timeUnits;
        }

        @Override
        public void onGaugeAdded(String name, Gauge<?> gauge) {
            if (filter.matches(name, gauge)) {
                final ObjectName objectName = mBeanRegistrationSupport.createName("gauges", name);
                mBeanRegistrationSupport.registerMBean(new JmxGauge(gauge, objectName), objectName);
            }
        }

        @Override
        public void onGaugeRemoved(String name) {
            mBeanRegistrationSupport.unregisterMBean(mBeanRegistrationSupport.createName("gauges", name));
        }

        @Override
        public void onCounterAdded(String name, Counter counter) {
            if (filter.matches(name, counter)) {
                final ObjectName objectName = mBeanRegistrationSupport.createName("counters", name);
                mBeanRegistrationSupport.registerMBean(new JmxCounter(counter, objectName), objectName);
            }
        }

        @Override
        public void onCounterRemoved(String name) {
            mBeanRegistrationSupport.unregisterMBean(mBeanRegistrationSupport.createName("counters", name));
        }

        @Override
        public void onHistogramAdded(String name, Histogram histogram) {
            if (filter.matches(name, histogram)) {
                final ObjectName objectName = mBeanRegistrationSupport.createName("histograms", name);
                mBeanRegistrationSupport.registerMBean(new JmxHistogram(histogram, objectName), objectName);
            }
        }

        @Override
        public void onHistogramRemoved(String name) {
            mBeanRegistrationSupport.unregisterMBean(mBeanRegistrationSupport.createName("histograms", name));
        }

        @Override
        public void onMeterAdded(String name, Meter meter) {
            if (filter.matches(name, meter)) {
                final ObjectName objectName = mBeanRegistrationSupport.createName("meters", name);
                mBeanRegistrationSupport.registerMBean(new JmxMeter(meter, objectName, timeUnits.rateFor(name)), objectName);
            }
        }

        @Override
        public void onMeterRemoved(String name) {
            mBeanRegistrationSupport.unregisterMBean(mBeanRegistrationSupport.createName("meters", name));
        }

        @Override
        public void onTimerAdded(String name, Timer timer) {
            if (filter.matches(name, timer)) {
                final ObjectName objectName = mBeanRegistrationSupport.createName("timers", name);
                mBeanRegistrationSupport.registerMBean(new JmxTimer(timer, objectName, timeUnits.rateFor(name), timeUnits.durationFor(name)), objectName);
            }
        }

        @Override
        public void onTimerRemoved(String name) {
            mBeanRegistrationSupport.unregisterMBean(mBeanRegistrationSupport.createName("timers", name));
        }

    }

    private static class MetricTimeUnits {
        private final TimeUnit defaultRate;
        private final TimeUnit defaultDuration;
        private final Map<String, TimeUnit> rateOverrides;
        private final Map<String, TimeUnit> durationOverrides;

        MetricTimeUnits(TimeUnit defaultRate,
                        TimeUnit defaultDuration,
                        Map<String, TimeUnit> rateOverrides,
                        Map<String, TimeUnit> durationOverrides) {
            this.defaultRate = defaultRate;
            this.defaultDuration = defaultDuration;
            this.rateOverrides = rateOverrides;
            this.durationOverrides = durationOverrides;
        }

        public TimeUnit durationFor(String name) {
            return durationOverrides.getOrDefault(name, defaultDuration);
        }

        public TimeUnit rateFor(String name) {
            return rateOverrides.getOrDefault(name, defaultRate);
        }
    }

}
