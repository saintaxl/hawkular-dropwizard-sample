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
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.hawkular.metrics.dropwizard.HawkularReporter;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.ehcache.InstrumentedEhcache;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.codahale.metrics.jvm.ThreadStatesGaugeSet;
import com.google.common.collect.Lists;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;

/**
 * @author Joel Takvorian
 */
class Benchmark {

    private final MetricRegistry registry;
    private final Ehcache ehcache;

    private Benchmark(MetricRegistry registry, Ehcache cache) {
        this.registry = registry;
        ehcache = InstrumentedEhcache.instrument(registry, cache);
    }

    private static MetricRegistry setupRegistry() {
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException e) {
            hostname = "?";
        }
        MetricRegistry registry = new MetricRegistry();
        HawkularReporter hawkularReporter = HawkularReporter.builder(registry, "com.hawkular.sample")
                .addRegexTag(Pattern.compile(GuavaBackend.NAME + "\\..*"), "impl", GuavaBackend.NAME)
                .addRegexTag(Pattern.compile(EhcacheBackend.NAME + "\\..*"), "impl", EhcacheBackend.NAME)
                .addGlobalTag("hostname", hostname)
                .prefixedWith(hostname + ".")
                .setRegexMetricComposition(Pattern.compile("net\\.sf\\.ehcache"), Lists
                        .newArrayList("mean", "meanrt", "5minrt", "98perc", "count"))
                .setRegexMetricComposition(Pattern.compile(".*"), Lists.newArrayList("mean", "meanrt", "count"))
                .build();
        hawkularReporter.start(1, TimeUnit.SECONDS);

        // Register some JVM metrics
        registry.registerAll(new PrefixedMetricSet("gc", new GarbageCollectorMetricSet()));
        registry.registerAll(new PrefixedMetricSet("memory", new MemoryUsageGaugeSet()));
        registry.registerAll(new PrefixedMetricSet("thread", new ThreadStatesGaugeSet()));
        return registry;
    }

    private void run() {
        final DatabaseStub fakeDb = new DatabaseStub();
        final BackendMonitoring monitoring = new BackendMonitoring(registry);
        Map<String, Object> presetElements = IntStream.range(0, 100000)
                .mapToObj(Integer::new)
                .collect(Collectors.toMap(i -> UUID.randomUUID().toString(), i -> i));

        monitoring.runScenario(presetElements, new GuavaBackend(fakeDb), GuavaBackend.NAME);
        monitoring.runScenario(presetElements, new EhcacheBackend(fakeDb, ehcache), EhcacheBackend.NAME);
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

    public static void main(String[] args) {
        MetricRegistry registry = setupRegistry();
        CacheManager cacheManager = CacheManager.newInstance();
        Ehcache cache = cacheManager.addCacheIfAbsent("testCache");
        Benchmark benchmark = new Benchmark(registry, cache);
        benchmark.run();
        cacheManager.shutdown();
    }
}
