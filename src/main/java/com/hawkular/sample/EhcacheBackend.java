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

import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;

/**
 * @author Joel Takvorian
 */
public class EhcacheBackend implements Backend {

    public static final String NAME = "ehcache";

    private final DatabaseStub db;
    private final Ehcache cache;
    private boolean isLastReadFromCache = false;

    public EhcacheBackend(DatabaseStub db, Ehcache cache) {
        this.db = db;
        this.cache = cache;
    }

    @Override public Object get(String key) {
        Object obj = cache.get(key);
        if (obj == null) {
            obj = db.get(key);
            cache.put(new Element(key, obj));
            isLastReadFromCache = false;
        } else {
            isLastReadFromCache = true;
        }
        return obj;
    }

    @Override public long count() {
        return cache.getSize();
    }

    @Override public void init(Map<String, Object> presetElements) {
        cache.removeAll();
        db.init(presetElements);
    }

    @Override public boolean isLastReadFromCache() {
        return isLastReadFromCache;
    }
}
