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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.base.Stopwatch;

/**
 * @author Joel Takvorian
 */
class BackendMonitoring {
    private final MetricRegistry registry;

    BackendMonitoring(MetricRegistry registry) {
        this.registry = registry;
    }

    void runScenario(Map<String, Object> presetElements, Backend backend, String name) {
        System.out.println("Starting scenario for " + name);
        registry.register(name + ".size", (Gauge<Long>) backend::count);
        Timer readTimer = registry.timer(name + ".read");
        final Meter readCacheMeter = registry.meter(name + ".cache.read");
        final Meter readDbMeter = registry.meter(name + ".db.read");
        final Counter numberItemsRead = registry.counter(name + ".total.read.count");
        // Setup preset elements
        backend.init(presetElements);
        List<String> keys = new ArrayList<>(presetElements.keySet());
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Stopwatch watch = Stopwatch.createStarted();
        while (watch.elapsed(TimeUnit.MINUTES) < 5) {
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
        // Reset size gauge to 0
        backend.init(new HashMap<>());
        System.out.println("Ending scenario for " + name);
    }

    private void runWithBenchmark(Runnable r, Timer readTimer) {
        final Timer.Context ctx = readTimer.time();
        try {
            r.run();
        } finally {
            ctx.stop();
        }
    }
}
