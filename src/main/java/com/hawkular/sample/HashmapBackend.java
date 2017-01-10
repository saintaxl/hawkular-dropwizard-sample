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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Joel Takvorian
 */
public class HashmapBackend implements Backend {

    public static final String NAME = "hashmap";

    private final Map<String, Object> map = new HashMap<>();
    private final Database db;
    private final AtomicBoolean isLastReadFromCache = new AtomicBoolean(false);

    public HashmapBackend(Database db) {
        this.db = db;
    }

    @Override public Object get(String key) {
        if (map.containsKey(key)) {
            isLastReadFromCache.set(true);
            return map.get(key);
        } else {
            isLastReadFromCache.set(false);
            Object obj = db.get(key);
            map.put(key, obj);
            return obj;
        }
    }

    @Override public long count() {
        return map.size();
    }

    @Override public void init(Map<String, Object> presetElements) {
        map.clear();
        db.init(presetElements);
    }

    @Override public boolean isLastReadFromCache() {
        return isLastReadFromCache.get();
    }
}
