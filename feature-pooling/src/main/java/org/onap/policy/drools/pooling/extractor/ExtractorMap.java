/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.pooling.extractor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.lang.StringUtils;
import org.onap.policy.drools.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extractors for each object class. Properties define how the data is to be
 * extracted for a given class, where the properties are similar to the
 * following:
 * 
 * <pre>
 * <code>extractor.requestId.&lt;class-name> = ${event.reqid}</code>
 * </pre>
 * 
 * If it doesn't find a property for the class, then it looks for a property for
 * that class' super class or interfaces. Extractors are compiled and cached.
 */
public class ExtractorMap {

    private static final Logger logger = LoggerFactory.getLogger(ExtractorMap.class);

    /**
     * Property prefix.
     */
    public static final String PROP_PREFIX = "extractor.";

    /**
     * Properties that specify how the data is to be extracted from a given
     * class.
     */
    private final Properties properties;

    /**
     * Property prefix, including the {@link #type} and a trailing ".".
     */
    private final String prefix;

    /**
     * Type of item to be extracted.
     */
    private final String type;

    /**
     * Maps the class to its extractor. Because this map is concurrent, and it
     * may be built recursively, we insert a {@link DelayedExtractor} into it
     * rather than a plain {@link Extractor}.
     */
    private final ConcurrentHashMap<Class<?>, DelayedExtractor> class2extractor = new ConcurrentHashMap<>();

    /**
     * 
     * @param props properties that specify how the data is to be extracted from
     *        a given class
     * @param type type of item to be extracted
     */
    public ExtractorMap(Properties props, String type) {
        this.properties = props;
        this.prefix = PROP_PREFIX + type + ".";
        this.type = type;
    }

    /**
     * Gets the number of extractors in the map.
     * 
     * @return gets the number of extractors in the map
     */
    protected int size() {
        return class2extractor.size();
    }

    /**
     * Extracts the desired data item from an object.
     * 
     * @param object object from which to extract the data item
     * @return the extracted item, or {@code null} if it could not be extracted
     */
    public Object extract(Object object) {
        if (object == null) {
            return null;
        }

        Extractor ext = getExtractor(object);

        return ext.extract(object);
    }

    /**
     * Gets the extractor for the given type of object, creating one if it
     * doesn't exist yet.
     * 
     * @param object object whose extracted is desired
     * @return an extractor for the object
     */
    private Extractor getExtractor(Object object) {
        Class<?> clazz = object.getClass();
        Extractor ext = class2extractor.get(clazz);

        if (ext == null) {
            // allocate a new extractor, if another thread doesn't beat us to it
            ext = class2extractor.computeIfAbsent(clazz, xxx -> new DelayedExtractor(clazz));
        }

        return ext;
    }

    /**
     * Extractor that always returns {@code null}. Used when no extractor could
     * be built for a given object type.
     */
    private class NullExtractor implements Extractor {

        @Override
        public Object extract(Object object) {
            logger.warn("cannot extract " + type + " from " + object.getClass());
            return null;
        }
    }

    /**
     * Extractor that delays configuration of its extractor until it's actually
     * needed.
     */
    private class DelayedExtractor implements Extractor {
        /**
         * Identifies the class for which an extractor will be built.
         */
        private final Class<?> clazz;

        /**
         * The extractor that was built for this class.
         */
        private AtomicReference<Extractor> extractor = new AtomicReference<>(null);

        /**
         * 
         * @param clazz the class of objects on which this extractor works
         */
        public DelayedExtractor(Class<?> clazz) {
            this.clazz = clazz;
        }

        @Override
        public Object extract(Object object) {
            Extractor ext = extractor.get();
            if (ext == null) {
                // no extractor yet - create one
                ext = buildExtractor();

                // set it, if no one else beats us to it
                extractor.compareAndSet(null, ext);

                // make sure we use whatever is current
                ext = extractor.get();
            }

            return ext.extract(object);
        }

        /**
         * Builds an extractor for the class.
         * 
         * @return a new extractor
         */
        private Extractor buildExtractor() {
            String value = properties.getProperty(prefix + clazz.getName(), null);
            if (value != null) {
                // property has config info for this class - build the extractor
                return buildExtractor(value);
            }

            /*
             * Get the extractor, if any, for the super class or interfaces, but
             * don't add one if it doesn't exist
             */
            Extractor ext = getClassExtractor(clazz, false);
            if (ext != null) {
                return ext;
            }

            /*
             * No extractor defined for for this class or its super class - we
             * cannot extract data items from objects of this type, so just
             * allocated a null extractor.
             */
            logger.warn("missing property " + prefix + clazz.getName());
            return new NullExtractor();
        }

        /**
         * Builds an extractor for the class, based on the config value
         * extracted from the corresponding property.
         * 
         * @param value config value (e.g., "${event.request.id}"
         * @return a new extractor
         */
        private Extractor buildExtractor(String value) {
            if (!value.startsWith("${")) {
                logger.warn("property value for " + prefix + clazz.getName() + " does not start with '${'");
                return new NullExtractor();
            }

            if (!value.endsWith("}")) {
                logger.warn("property value for " + prefix + clazz.getName() + " does not end with '}'");
                return new NullExtractor();
            }

            // get the part in the middle
            String val = value.substring(2, value.length() - 1);
            if (val.startsWith(".")) {
                logger.warn("property value for " + prefix + clazz.getName() + " begins with '.'");
                return new NullExtractor();
            }

            if (val.endsWith(".")) {
                logger.warn("property value for " + prefix + clazz.getName() + " ends with '.'");
                return new NullExtractor();
            }

            // everything's valid - create the extractor
            try {
                ComponetizedExtractor ext = new ComponetizedExtractor(clazz, val.split("[.]"));

                /*
                 * If there's only one extractor, then just return it, otherwise
                 * return the whole extractor.
                 */
                return (ext.extractors.length == 1 ? ext.extractors[0] : ext);

            } catch (ExtractorException e) {
                logger.warn("cannot build extractor for " + clazz.getName());
                return new NullExtractor();
            }
        }

        /**
         * Gets the extractor for a class, examining all super classes and
         * interfaces.
         * 
         * @param clazz class whose extractor is desired
         * @param addOk {@code true} if the extractor may be added, provided the
         *        property is defined, {@code false} otherwise
         * @return the extractor to be used for the class, or {@code null} if no
         *         extractor has been defined yet
         */
        private Extractor getClassExtractor(Class<?> clazz, boolean addOk) {
            if (clazz == null) {
                return null;
            }

            Extractor ext = null;

            if (addOk) {
                String val = properties.getProperty(prefix + clazz.getName(), null);

                if (val != null) {
                    /*
                     * A property is defined for this class, so create the
                     * extractor for it.
                     */
                    return class2extractor.computeIfAbsent(clazz, xxx -> new DelayedExtractor(clazz));
                }
            }

            // see if the superclass has an extractor
            if ((ext = getClassExtractor(clazz.getSuperclass(), true)) != null) {
                return ext;
            }

            // check the interfaces, too
            for (Class<?> clz : clazz.getInterfaces()) {
                if ((ext = getClassExtractor(clz, true)) != null) {
                    break;
                }
            }

            return ext;
        }
    }

    /**
     * Component-ized extractor. Extracts an object that is referenced
     * hierarchically, where each name identifies a particular component within
     * the hierarchy. Supports retrieval from {@link Map} objects, as well as
     * via getXxx() methods, or by direct field retrieval.
     * <p>
     * Note: this will <i>not</i> work if POJOs are contained within a Map.
     */
    private class ComponetizedExtractor implements Extractor {

        /**
         * Extractor for each component.
         */
        private final Extractor[] extractors;

        /**
         * 
         * @param clazz the class associated with the object at the root of the
         *        hierarchy
         * @param names name associated with each component
         * @throws ExtractorException
         */
        public ComponetizedExtractor(Class<?> clazz, String[] names) throws ExtractorException {
            this.extractors = new Extractor[names.length];

            Class<?> clz = clazz;

            for (int x = 0; x < names.length; ++x) {
                String comp = names[x];

                Pair<Extractor, Class<?>> pair = buildExtractor(clz, comp);

                extractors[x] = pair.first();
                clz = pair.second();
            }
        }

        /**
         * Builds an extractor for the given component of an object.
         * 
         * @param clazz type of object from which the component will be
         *        extracted
         * @param comp name of the component to extract
         * @return a pair containing the extractor and the extracted object's
         *         type
         * @throws ExtractorException
         */
        private Pair<Extractor, Class<?>> buildExtractor(Class<?> clazz, String comp) throws ExtractorException {
            Pair<Extractor, Class<?>> pair = null;

            if (pair == null) {
                pair = getMethodExtractor(clazz, comp);
            }

            if (pair == null) {
                pair = getFieldExtractor(clazz, comp);
            }

            if (pair == null) {
                pair = getMapExtractor(clazz, comp);
            }


            // didn't find an extractor
            if (pair == null) {
                throw new ExtractorException("class " + clazz + " contains no element " + comp);
            }

            return pair;
        }

        @Override
        public Object extract(Object object) {
            Object obj = object;

            for (Extractor ext : extractors) {
                if (obj == null) {
                    break;
                }

                obj = ext.extract(obj);
            }

            return obj;
        }

        /**
         * Gets an extractor that invokes a getXxx() method to retrieve the
         * object.
         * 
         * @param clazz container's class
         * @param name name of the property to be retrieved
         * @return a new extractor, or {@code null} if the class does not
         *         contain the corresponding getXxx() method
         * @throws ExtractorException if the getXxx() method is inaccessible
         */
        private Pair<Extractor, Class<?>> getMethodExtractor(Class<?> clazz, String name) throws ExtractorException {
            Method meth;

            String nm = "get" + StringUtils.capitalize(name);

            try {
                meth = clazz.getMethod(nm);

                Class<?> retType = meth.getReturnType();
                if (retType == void.class) {
                    // it's a void method, thus it won't return an object
                    return null;
                }

                return new Pair<>(new MethodExtractor(meth), retType);

            } catch (NoSuchMethodException expected) {
                // no getXxx() method, maybe there's a field by this name
                return null;

            } catch (SecurityException e) {
                throw new ExtractorException("inaccessible method " + clazz + "." + nm, e);
            }
        }

        /**
         * Gets an extractor for a field within the object.
         * 
         * @param clazz container's class
         * @param name name of the field whose value is to be extracted
         * @return a new extractor, or {@code null} if the class does not
         *         contain the given field
         * @throws ExtractorException if the field is inaccessible
         */
        private Pair<Extractor, Class<?>> getFieldExtractor(Class<?> clazz, String name) throws ExtractorException {
            try {
                Field field = clazz.getDeclaredField(name);

                return new Pair<>(new FieldExtractor(field), field.getType());

            } catch (NoSuchFieldException expected) {
                // no field by this name, maybe it's in a map
                return null;

            } catch (SecurityException e) {
                throw new ExtractorException("inaccessible field " + clazz + "." + name, e);
            }
        }

        /**
         * Gets an extractor for an item within a Map object.
         * 
         * @param clazz container's class
         * @param key item key within the map
         * @return a new extractor, or {@code null} if the class is not a Map
         *         subclass
         * @throws ExtractorException
         */
        private Pair<Extractor, Class<?>> getMapExtractor(Class<?> clazz, String key) throws ExtractorException {

            if (!Map.class.isAssignableFrom(clazz)) {
                return null;
            }

            /*
             * Don't know the value's actual type, so we'll assume it's a Map
             * for now. Things should still work OK, as this is only used to
             * direct the constructor on what type of extractor to create next.
             * If the object turns out not to be a map, then the MapExtractor
             * for the next component will just return null.
             */
            return new Pair<>(new MapExtractor(key), Map.class);
        }
    }
}
