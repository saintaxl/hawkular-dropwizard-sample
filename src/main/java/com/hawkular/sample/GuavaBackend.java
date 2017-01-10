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

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

/**
 * @author Joel Takvorian
 */
public class GuavaBackend implements Backend {

    public static final String NAME = "guava";

    private final LoadingCache<String, Object> cache;
    private final Database db;
    private final AtomicBoolean isLastReadFromCache = new AtomicBoolean(false);

    public GuavaBackend(Database db) {
        this.db = db;
        cache = CacheBuilder.newBuilder()
                .build(new CacheLoader<String, Object>() {
                    @Override public Object load(String s) throws Exception {
                        isLastReadFromCache.set(false);
                        return db.get(s);
                    }
                });
    }

    @Override public Object get(String key) {
        isLastReadFromCache.set(true);
        return cache.getUnchecked(key);
    }

    @Override public long count() {
        return cache.size();
    }

    @Override public void init(Map<String, Object> presetElements) {
        cache.invalidateAll();
        db.init(presetElements);
    }

    @Override public boolean isLastReadFromCache() {
        return isLastReadFromCache.get();
    }
}
