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

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Joel Takvorian
 */
class DatabaseStub {

    private final Map<String, Object> fakeDB = new HashMap<>();
    private AtomicBoolean simulateFailure = new AtomicBoolean(false);

    public DatabaseStub() {
        Random rnd = new Random();
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(() -> {
            if (simulateFailure.get()) {
                if (rnd.nextInt(100) < 20) {
                    simulateFailure.set(false);
                }
            } else {
                if (rnd.nextInt(100) == 0) {
                    simulateFailure.set(true);
                }
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void simulateLatency(long ms) {
        if (simulateFailure.get()) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            throw new RuntimeException("Warning: mysql_connect(): Lost connection to MySQL server during query");
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    Object get(String key) {
        simulateLatency(8);
        return fakeDB.get(key);
    }

    void init(Map<String, Object> presetElements) {
        fakeDB.clear();
        fakeDB.putAll(presetElements);
    }
}
