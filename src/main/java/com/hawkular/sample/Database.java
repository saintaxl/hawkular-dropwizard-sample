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

/**
 * @author Joel Takvorian
 */
class Database {

    private final Map<String, Object> fakeDB = new HashMap<>();

    private void simulateDelay(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    Object get(String key) {
        simulateDelay(10);
        return fakeDB.get(key);
    }

    void init(Map<String, Object> presetElements) {
        fakeDB.clear();
        fakeDB.putAll(presetElements);
    }
}
