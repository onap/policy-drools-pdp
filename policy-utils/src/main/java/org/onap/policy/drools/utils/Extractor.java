/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2019 AT&T Intellectual Property. All rights reserved.
 * ================================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ============LICENSE_END=========================================================
 */

package org.onap.policy.drools.utils;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Field extractor that extracts a particular field from within arbitrary objects.
 * JavaScript scripts are registered for each object class name of interest and then the
 * extractor executes the scripts to extract data from a given object. Multiple scripts
 * may be registered for the same class name.
 *
 * <p/>
 * Scripts should prefix all references with "v.", as "v" is the name of the variable
 * within the script engine identifying the object whose field is to be extracted.
 */
public class Extractor {
    private static final Logger logger = LoggerFactory.getLogger(Extractor.class);

    /**
     * Maps a class name to its set of scripts.
     */
    private Map<String, Set<String>> class2scripts = new ConcurrentHashMap<>();


    /**
     * Constructs the object.
     */
    public Extractor() {
        super();
    }

    /**
     * Registers all class/scripts found within a set of properties. Property names of
     * interest are expected to be of the form, "{prefix}{class-name}", where the
     * corresponding property value is the script to be used to extract the field from
     * objects of the given class. Any properties not of that form are ignored.
     *
     * <p/>
     * Scripts are split on commas and each component is registered as an alternative
     * script for the given class.
     *
     * @param props properties containing extraction scripts
     * @param prefix prefix for relevant properties, typically ending with "."
     */
    public void register(Properties props, String prefix) {
        int len = prefix.length();

        for (Entry<Object, Object> ent : props.entrySet()) {
            String name = ent.getKey().toString().trim();
            if (!name.startsWith(prefix)) {
                continue;
            }

            String classnm = name.substring(len);
            if (!classnm.isEmpty()) {
                String scripts = ent.getValue().toString();
                for (String script : scripts.split(",")) {
                    register(classnm, script);
                }
            }
        }
    }

    /**
     * Registers a script for a given class name.
     *
     * @param className name of the class for which the script should be registered
     * @param script script to be registered
     */
    public void register(String className, String script) {
        logger.info("add extractor for {}: {}", className, script);

        class2scripts.compute(className, (key, scripts) -> {
            if (scripts == null) {
                scripts = new LinkedHashSet<>();

            } else {
                /*
                 * The existing set should be left unchanged, because extract() may
                 * already be using it. Thus we make a copy and replace the old set with
                 * the new set within the map.
                 */
                scripts = new LinkedHashSet<>(scripts);
            }

            scripts.add(script.trim());
            return scripts;
        });
    }

    /**
     * Extracts the field from the object, trying each extractor in turn, until a non-null
     * value is extracted.
     *
     * @param object object whose field is to be extracted
     * @return the extracted field, or {@code null} if the field could not be extracted by
     *         any of the relevant scripts
     */
    public String extract(Object object) {
        if (object == null) {
            return null;
        }

        ScriptEngine eng = getEngine();
        Set<String> scripts = getScripts(object.getClass());

        for (String script : scripts) {
            try {
                Bindings bindings = eng.createBindings();
                bindings.put("v", object);

                Object result = eng.eval(script, bindings);
                if (result != null) {
                    return result.toString();
                }

            } catch (ScriptException | RuntimeException e) {
                logger.warn("extraction script failure {}", script, e);
            }
        }

        return null;
    }

    /**
     * Gets the scripts that can be used to extract the field from objects of the given
     * class. If the class has no scripts defined yet, then it recursively walks the class
     * hierarchy to identify scripts for its superclass and interfaces.
     *
     * @param clazz class of interest
     * @return the scripts for the given class
     */
    protected Set<String> getScripts(Class<?> clazz) {
        Set<String> existingScripts = class2scripts.get(clazz.getName());
        if (existingScripts != null) {
            return existingScripts;
        }

        // nothing registered for this class yet - build its list of scripts

        // use a LinkedHashSet so items from the superclass take precedence
        Set<String> newScripts = new LinkedHashSet<>();

        Class<?> sup = clazz.getSuperclass();
        if (sup != null) {
            newScripts.addAll(getScripts(sup));
        }

        for (Class<?> intfc : clazz.getInterfaces()) {
            newScripts.addAll(getScripts(intfc));
        }

        return class2scripts.computeIfAbsent(clazz.getName(), key -> newScripts);
    }

    /**
     * Initialization-on-demand holder idiom.
     */
    private static class Singleton {

        private static final ScriptEngine engine = new ScriptEngineManager().getEngineByName("javascript");

        /**
         * Not invoked.
         */
        private Singleton() {
            super();
        }
    }

    // these may be overridden by junit tests

    protected ScriptEngine getEngine() {
        return Singleton.engine;
    }
}
