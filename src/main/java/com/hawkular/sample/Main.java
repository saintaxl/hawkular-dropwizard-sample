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

import java.util.Scanner;

import com.codahale.metrics.Meter;
import com.codahale.metrics.ehcache.InstrumentedEhcache;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;

/**
 * @author Joel Takvorian
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("Init...");
        Scanner scanner = new Scanner(System.in);
        Metrics metrics = new Metrics();
        CacheManager cacheManager = CacheManager.newInstance();
        Ehcache cache = cacheManager.addCacheIfAbsent("testCache");
        Ehcache instrumentedCache = InstrumentedEhcache.instrument(metrics.registry(), cache);
        Benchmark benchmark = new Benchmark(metrics.registry(), instrumentedCache);
        Meter printMeter = metrics.registry().meter("global.print.meter");
        System.out.println("Benchmark started. Type 'p' to print stats, 'q' to quit.");
        String command = scanner.next();
        while (!"q".equals(command)) {
            if ("p".equals(command)) {
                printMeter.mark();
                System.out.println(metrics.info());
            } else {
                System.out.println("Unknown command");
            }
            System.out.println("Type 'p' to print stats, 'q' to quit.");
            command = scanner.next();
        }
        cacheManager.shutdown();
        benchmark.stop();
    }
}
