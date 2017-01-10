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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.base.Stopwatch;

import net.sf.ehcache.Ehcache;

/**
 * @author Joel Takvorian
 */
class Benchmark {

    private final MetricRegistry registry;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    Benchmark(MetricRegistry registry, Ehcache ehcache) {
        this.registry = registry;

        final Database fakeDb = new Database();
        final BackendMonitoring hashmapMonitoring = new BackendMonitoring(new HashmapBackend(fakeDb), HashmapBackend.NAME);
        final BackendMonitoring guavaMonitoring = new BackendMonitoring(new GuavaBackend(fakeDb), GuavaBackend.NAME);
        final BackendMonitoring ehcacheMonitoring = new BackendMonitoring(new EhcacheBackend(fakeDb, ehcache), EhcacheBackend.NAME);
        executor.scheduleAtFixedRate(() -> {
            Map<String, Object> presetElements = genData(5000);
            hashmapMonitoring.runScenario(presetElements);
            guavaMonitoring.runScenario(presetElements);
            ehcacheMonitoring.runScenario(presetElements);
        }, 0, 5, TimeUnit.MINUTES);
    }

    private static Map<String, Object> genData(int n) {
        return IntStream.range(0, n)
                .mapToObj(Integer::new)
                .collect(Collectors.toMap(i -> UUID.randomUUID().toString(), i -> i));
    }

    void stop() {
        executor.shutdown();
    }

    private class BackendMonitoring {
        private final Backend backend;
        private final String name;
        private final Timer readTimer;

        private BackendMonitoring(Backend backend, String name) {
            this.backend = backend;
            this.name = name;
            registry.register(name + ".size", (Gauge<Long>) backend::count);
            readTimer = registry.timer(name + ".read");
        }

        private void runScenario(Map<String, Object> presetElements) {
            System.out.println("Starting scenario for " + name);
            final Meter readCacheMeter = registry.meter(name + ".cache.read");
            final Meter readDbMeter = registry.meter(name + ".db.read");
            final Counter numberItemsRead = registry.counter(name + ".total.read.count");
            // Setup preset elements
            backend.init(presetElements);
            List<String> keys = new ArrayList<>(presetElements.keySet());
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            Stopwatch watch = Stopwatch.createStarted();
            while (watch.elapsed(TimeUnit.SECONDS) < 40) {
                int pos = rnd.nextInt(0, keys.size());
                runWithBenchmark(() -> {
                    backend.get(keys.get(pos));
                    if (backend.isLastReadFromCache()) {
                        readCacheMeter.mark();
                    } else {
                        readDbMeter.mark();
                    }
                    numberItemsRead.inc();
                }, readTimer);
            }
            // Reset metrics
            backend.init(new HashMap<>());
            registry.remove(name + ".cache.read");
            registry.remove(name + ".db.read");
            registry.remove(name + ".total.read.count");
            System.out.println("Ending scenario for " + name);
        }

        private void runWithBenchmark(Runnable r, Timer t) {
            final Timer.Context ctx = t.time();
            try {
                r.run();
            } finally {
                ctx.stop();
            }
        }
    }
}
