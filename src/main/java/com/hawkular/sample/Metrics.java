/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hawkular.sample;

import static java.util.stream.Collectors.toMap;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.hawkular.metrics.dropwizard.HawkularReporter;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.Timer;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.codahale.metrics.jvm.ThreadStatesGaugeSet;
import com.google.common.collect.Lists;

/**
 * @author Joel Takvorian
 */
class Metrics {

    private MetricRegistry registry;

    Metrics() {
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException e) {
            hostname = "?";
        }
        registry = new MetricRegistry();
        HawkularReporter hawkularReporter = HawkularReporter.builder(registry, "com.hawkular.sample")
                .addRegexTag(Pattern.compile(HashmapBackend.NAME + "\\..*"), "impl", HashmapBackend.NAME)
                .addRegexTag(Pattern.compile(GuavaBackend.NAME + "\\..*"), "impl", GuavaBackend.NAME)
                .addRegexTag(Pattern.compile(EhcacheBackend.NAME + "\\..*"), "impl", EhcacheBackend.NAME)
                .addGlobalTag("hostname", hostname)
                .prefixedWith(hostname + ".")
                .setRegexMetricComposition(Pattern.compile("net\\.sf\\.ehcache"), Lists.newArrayList("mean", "meanrt", "5minrt", "98perc", "count"))
                .setRegexMetricComposition(Pattern.compile(".*"), Lists.newArrayList("mean", "meanrt", "count"))
                .build();
        hawkularReporter.start(1, TimeUnit.SECONDS);

        // Register some JVM metrics
        registry.registerAll(new PrefixedMetricSet("gc", new GarbageCollectorMetricSet()));
        registry.registerAll(new PrefixedMetricSet("memory", new MemoryUsageGaugeSet()));
        registry.registerAll(new PrefixedMetricSet("thread", new ThreadStatesGaugeSet()));
    }

    MetricRegistry registry() {
        return registry;
    }

    String info() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Metric> entry : registry.getMetrics().entrySet()) {
            if (entry.getValue() instanceof Meter) {
                Meter meter = (Meter) entry.getValue();
                sb.append(entry.getKey()).append(": ")
                        .append("count=").append(meter.getCount())
                        .append(", 1minrt=").append(meter.getOneMinuteRate())
                        .append(", 5minrt=").append(meter.getFiveMinuteRate())
                        .append(", meanrt=").append(meter.getMeanRate())
                        .append("\n");
            } else if (entry.getValue() instanceof Timer) {
                Timer timer = (Timer) entry.getValue();
                sb.append(entry.getKey()).append(": ")
                        .append("mean=").append(timer.getSnapshot().getMean())
                        .append(", meanrt=").append(timer.getMeanRate())
                        .append("\n");
            } else if (entry.getValue() instanceof Gauge) {
                Gauge<?> g = (Gauge<?>) entry.getValue();
                sb.append(entry.getKey()).append(": ")
                        .append("value=").append(g.getValue())
                        .append("\n");
            }
        }
        return sb.toString();
    }

    private static class PrefixedMetricSet implements MetricSet {

        private final MetricSet metricSet;
        private final String prefix;

        private PrefixedMetricSet(String prefix, MetricSet metricSet) {
            this.metricSet = metricSet;
            this.prefix = prefix;
        }

        @Override public Map<String, Metric> getMetrics() {
            return metricSet.getMetrics().entrySet().stream()
                    .collect(toMap(
                            e -> prefix + "." + e.getKey(),
                            Map.Entry::getValue
                    ));
        }
    }
}
