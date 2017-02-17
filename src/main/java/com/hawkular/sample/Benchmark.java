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

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.hawkular.metrics.client.HawkularClient;
import org.hawkular.metrics.client.HawkularFactory;
import org.hawkular.metrics.client.binder.HawkularDropwizardBinder;
import org.hawkular.metrics.client.grafana.GrafanaAnnotation;
import org.hawkular.metrics.client.grafana.GrafanaConnection;
import org.hawkular.metrics.client.grafana.GrafanaDashboard;
import org.hawkular.metrics.client.grafana.GrafanaDashboardMessage;
import org.hawkular.metrics.client.grafana.GrafanaDatasource;
import org.hawkular.metrics.client.grafana.GrafanaPanel;
import org.hawkular.metrics.client.grafana.GrafanaRow;
import org.hawkular.metrics.client.grafana.GrafanaTarget;
import org.hawkular.metrics.client.monitor.CPUMonitoring;
import org.hawkular.metrics.client.monitor.MemoryMonitoring;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.ehcache.InstrumentedEhcache;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.codahale.metrics.jvm.ThreadStatesGaugeSet;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;

/**
 * @author Joel Takvorian
 */
class Benchmark {

    private final HawkularClient hawkular;
    private final Ehcache ehcache;
    private final GrafanaDashboard dashboardOneByOne;
    private final GrafanaConnection grafanaConnection;
    static final String DATASOURCE_NAME = "testds";

    private Benchmark(HawkularClient hawkular, Ehcache cache) {
        this.hawkular = hawkular;
        ehcache = cache;

        GrafanaDashboard dashboardAllAtOnce = new GrafanaDashboard()
                .title("dashboard1")
                .addAnnotation(new GrafanaAnnotation("Errors", "BackendMonitoring.error.logs")
                        .color("red"))
                .addAnnotation(new GrafanaAnnotation("Warnings", "BackendMonitoring.warning.logs")
                        .color("orange"))
                .addAnnotation(new GrafanaAnnotation("Info", "BackendMonitoring.info.logs")
                        .color("blue"))
                .addRow(new GrafanaRow()
                        .addPanel(GrafanaPanel.graph()
                                .title("Storage size")
                                .addTarget(GrafanaTarget.gaugesTagged(BackendMonitoring.TAG_METRIC_SIZE)))
                        .addPanel(GrafanaPanel.graph()
                                .title("Read response time")
                                .addTarget(GrafanaTarget.gaugesTagged(BackendMonitoring.TAG_METRIC_RESPONSE_TIME))))
                .addRow(new GrafanaRow()
                        .addPanel(GrafanaPanel.graph()
                                .title("Read cache vs DB (mean rate)"))
                        .addPanel(GrafanaPanel.graph()
                                .title("Read cache vs DB (count)")
                                .addTarget(GrafanaTarget.countersTagged(BackendMonitoring.TAG_METRIC_READ))));
        grafanaConnection = new GrafanaConnection("eyJrIjoiN3dQcGRpVjVqZEFUMFBmOUwxa3Z3RTVFQ2ZaQ2g2ZFEiLCJuIjoidGVzdCIsImlkIjoxfQ==");
        try {
            grafanaConnection.createOrUpdateDatasource(GrafanaDatasource.fromHawkularConfig(DATASOURCE_NAME, hawkular.getInfo()));
            grafanaConnection.sendDashboard(new GrafanaDashboardMessage(dashboardAllAtOnce, DATASOURCE_NAME));
        } catch (IOException e) {
            e.printStackTrace();
        }

        dashboardOneByOne = new GrafanaDashboard().title("dashboard2");
    }

    private void run() {
        final DatabaseStub fakeDb = new DatabaseStub();
        final BackendMonitoring monitoring = new BackendMonitoring(hawkular, grafanaConnection, dashboardOneByOne);
        Map<String, Object> presetElements = IntStream.range(0, 100000)
                .mapToObj(Integer::new)
                .collect(toMap(i -> UUID.randomUUID().toString(), i -> i));

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

    private static void monitorJvm(HawkularClient hawkular) {
        MetricRegistry registry = new MetricRegistry();
        // Register some JVM metrics
        registry.registerAll(new PrefixedMetricSet("gc", new GarbageCollectorMetricSet()));
        registry.registerAll(new PrefixedMetricSet("memory", new MemoryUsageGaugeSet()));
        registry.registerAll(new PrefixedMetricSet("thread", new ThreadStatesGaugeSet()));
        HawkularDropwizardBinder
                .fromRegistry(registry)
                .withTag("family", "jvm")
                .bindWith(hawkular.getInfo(), 1, TimeUnit.SECONDS);
    }

    private static Ehcache monitorEhcache(HawkularClient hawkular) {
        MetricRegistry registry = new MetricRegistry();
        HawkularDropwizardBinder
                .fromRegistry(registry)
                .withTag("family", "ehcache")
                .bindWith(hawkular.getInfo(), 1, TimeUnit.SECONDS);
        CacheManager cacheManager = CacheManager.newInstance();
        return InstrumentedEhcache.instrument(registry,
                cacheManager.addCacheIfAbsent("testCache"));
    }

    public static void main(String[] args) {
        HawkularClient hawkular = HawkularFactory.load().builder()
                .addHeader("some-special-header", "value")
                .build();
        monitorJvm(hawkular);
        Ehcache cache = monitorEhcache(hawkular);
        Benchmark benchmark = new Benchmark(hawkular, cache);
        hawkular.prepareMonitoringSession(1, TimeUnit.SECONDS)
                .feeds(CPUMonitoring.create())
                .feeds(MemoryMonitoring.create())
                .run(benchmark::run);
    }
}
