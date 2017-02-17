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

import static com.hawkular.sample.Benchmark.DATASOURCE_NAME;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.hawkular.metrics.client.HawkularClient;
import org.hawkular.metrics.client.HawkularFactory;
import org.hawkular.metrics.client.HawkularLogger;
import org.hawkular.metrics.client.grafana.GrafanaConnection;
import org.hawkular.metrics.client.grafana.GrafanaDashboard;
import org.hawkular.metrics.client.grafana.GrafanaDashboardMessage;
import org.hawkular.metrics.client.grafana.GrafanaPanel;
import org.hawkular.metrics.client.grafana.GrafanaRow;
import org.hawkular.metrics.client.grafana.GrafanaTarget;
import org.hawkular.metrics.client.model.Counter;
import org.hawkular.metrics.client.model.Gauge;
import org.hawkular.metrics.client.model.Tag;
import org.hawkular.metrics.client.model.Tags;
import org.hawkular.metrics.client.model.Watch;

import com.google.common.base.Stopwatch;

/**
 * @author Joel Takvorian
 */
class BackendMonitoring {

    private static final HawkularLogger HWK = HawkularFactory.load().logger(BackendMonitoring.class);
    private static final Tag TAG_KEY_IMPL = Tag.key("impl");
    private static final Tag TAG_KEY_SOURCE = Tag.key("source");
    private static final Tag TAG_KEY_METRIC = Tag.key("metric");
    static final Tag TAG_METRIC_SIZE = TAG_KEY_METRIC.valued("size");
    static final Tag TAG_METRIC_RESPONSE_TIME = TAG_KEY_METRIC.valued("response-time");
    static final Tag TAG_METRIC_READ = TAG_KEY_METRIC.valued("read");

    private final HawkularClient hawkular;
    private final GrafanaConnection grafanaConnection;
    private final GrafanaDashboard dashboard;

    BackendMonitoring(HawkularClient hawkular, GrafanaConnection grafanaConnection, GrafanaDashboard dashboard) {
        this.hawkular = hawkular;
        this.grafanaConnection = grafanaConnection;
        this.dashboard = dashboard;
        dashboard.addLoggerAnnotations(HWK);
    }

    void runScenario(Map<String, Object> presetElements, Backend backend, String name) {
        Tags impl = Tags.from(TAG_KEY_IMPL.valued(name));
        HWK.info("Starting scenario for " + name, impl);
        Gauge size = hawkular.metricBuilder()
                .addSegments(impl)
                .addSegment(TAG_METRIC_SIZE)
                .toGauge();
        Watch responseTime = hawkular.metricBuilder()
                .addSegments(impl)
                .addSegment(TAG_METRIC_RESPONSE_TIME)
                .toWatch();
        Counter readCache = hawkular.metricBuilder()
                .addSegment("source", "cache")
                .addSegment("metric", "read")
                .addSegment("{impl=" + name + "}")
                .toCounter();
        Counter readDb = hawkular.metricBuilder()
                .addSegments(impl)
                .addSegment(TAG_KEY_SOURCE.valued("db"))
                .addSegment(TAG_METRIC_READ)
                .toCounter();
        dashboard.addRow(new GrafanaRow()
                .addPanel(GrafanaPanel.graph().title(name + " storage size").addTarget(GrafanaTarget.fromMetric(size)))
                .addPanel(GrafanaPanel.graph().title(name + " response time").addTarget(GrafanaTarget.fromMetric(responseTime)))
                .addPanel(GrafanaPanel.graph().title(name + " cache vs db read")
                        .addTarget(GrafanaTarget.fromMetric(readCache))
                        .addTarget(GrafanaTarget.fromMetric(readDb))));
        try {
            grafanaConnection.sendDashboard(new GrafanaDashboardMessage(dashboard, DATASOURCE_NAME));
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Setup preset elements
        backend.init(presetElements);
        List<String> keys = new ArrayList<>(presetElements.keySet());
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Stopwatch watch = Stopwatch.createStarted();
        while (watch.elapsed(TimeUnit.MINUTES) < 5) {
            try {
                int pos = rnd.nextInt(0, keys.size());
                backend.get(keys.get(pos));
                if (backend.isLastReadFromCache()) {
                    readCache.inc();
                } else {
                    readDb.inc();
                }
            } catch (Exception e) {
                HWK.error(e, impl);
            } finally {
                responseTime.tick();
                size.set(backend.count());
            }
        }
        // Reset size gauge to 0
        backend.init(new HashMap<>());
        HWK.info("Ending scenario for " + name, impl);
    }
}
