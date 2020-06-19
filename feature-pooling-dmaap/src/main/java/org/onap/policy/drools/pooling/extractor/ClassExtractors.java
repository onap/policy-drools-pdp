/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018-2019 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2019-2020 Nordix Foundation
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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extractors for each object class. Properties define how the data is to be
 * extracted for a given class, where the properties are similar to the
 * following:
 *
 * <pre>
 * <code>&lt;a.prefix>.&lt;class.name> = ${event.reqid}</code>
 * </pre>
 *
 * <p>For any given field name (e.g., "reqid"), it first looks for a public "getXxx()"
 * method to extract the specified field. If that fails, then it looks for a public field
 * by the given name. If that also fails, and the object is a <i>Map</i> subclass, then it
 * simply uses the "get(field-name)" method to extract the data from the map.
 */
public class ClassExtractors {

    private static final Logger logger = LoggerFactory.getLogger(ClassExtractors.class);

    /**
     * Properties that specify how the data is to be extracted from a given
     * class.
     */
    private final Properties properties;

    /**
     * Property prefix, including a trailing ".".
     */
    private final String prefix;

    /**
     * Type of item to be extracted.
     */
    private final String type;

    /**
     * Maps the class name to its extractor.
     */
    private final ConcurrentHashMap<String, Extractor> class2extractor = new ConcurrentHashMap<>();

    /**
     * Constructor.
     *
     * @param props properties that specify how the data is to be extracted from
     *        a given class
     * @param prefix property name prefix, prepended before the class name
     * @param type type of item to be extracted
     */
    public ClassExtractors(Properties props, String prefix, String type) {
        this.properties = props;
        this.prefix = (prefix.endsWith(".") ? prefix : prefix + ".");
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
        Extractor ext = class2extractor.get(clazz.getName());

        if (ext == null) {
            // allocate a new extractor, if another thread doesn't beat us to it
            ext = class2extractor.computeIfAbsent(clazz.getName(), xxx -> buildExtractor(clazz));
        }

        return ext;
    }

    /**
     * Builds an extractor for the class.
     *
     * @param clazz class for which the extractor should be built
     *
     * @return a new extractor
     */
    private Extractor buildExtractor(Class<?> clazz) {
        String value = properties.getProperty(prefix + clazz.getName(), null);
        if (value != null) {
            // property has config info for this class - build the extractor
            return buildExtractor(clazz, value);
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
        logger.warn("missing property {}{}", prefix, clazz.getName());
        return new NullExtractor();
    }

    /**
     * Builds an extractor for the class, based on the config value extracted
     * from the corresponding property.
     *
     * @param clazz class for which the extractor should be built
     * @param value config value (e.g., "${event.request.id}"
     * @return a new extractor
     */
    private Extractor buildExtractor(Class<?> clazz, String value) {
        if (!value.startsWith("${")) {
            logger.warn("property value for {}{} does not start with {}", prefix, clazz.getName(), "'${'");
            return new NullExtractor();
        }

        if (!value.endsWith("}")) {
            logger.warn("property value for {}{} does not end with '}'", prefix, clazz.getName());
            return new NullExtractor();
        }

        // get the part in the middle
        String val = value.substring(2, value.length() - 1);
        if (val.startsWith(".")) {
            logger.warn("property value for {}{} begins with '.'", prefix, clazz.getName());
            return new NullExtractor();
        }

        if (val.endsWith(".")) {
            logger.warn("property value for {}{} ends with '.'", prefix, clazz.getName());
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
            logger.warn("cannot build extractor for {}", clazz.getName(), e);
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
                 * A property is defined for this class, so create the extractor
                 * for it.
                 */
                return class2extractor.computeIfAbsent(clazz.getName(), xxx -> buildExtractor(clazz));
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

    /**
     * Extractor that always returns {@code null}. Used when no extractor could
     * be built for a given object type.
     */
    private class NullExtractor implements Extractor {

        @Override
        public Object extract(Object object) {
            logger.info("cannot extract {} from {}", type, object.getClass());
            return null;
        }
    }

    /**
     * Component-ized extractor. Extracts an object that is referenced
     * hierarchically, where each name identifies a particular component within
     * the hierarchy. Supports retrieval from {@link Map} objects, as well as
     * via getXxx() methods, or by direct field retrieval.
     *
     * <p>Note: this will <i>not</i> work if POJOs are contained within a Map.
     */
    private class ComponetizedExtractor implements Extractor {

        /**
         * Extractor for each component.
         */
        private final Extractor[] extractors;

        /**
         * Constructor.
         *
         * @param clazz the class associated with the object at the root of the
         *        hierarchy
         * @param names name associated with each component
         * @throws ExtractorException extractor exception
         */
        public ComponetizedExtractor(Class<?> clazz, String[] names) throws ExtractorException {
            this.extractors = new Extractor[names.length];

            Class<?> clz = clazz;

            for (int x = 0; x < names.length; ++x) {
                String comp = names[x];

                Pair<Extractor, Class<?>> pair = buildExtractor(clz, comp);

                extractors[x] = pair.getLeft();
                clz = pair.getRight();
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
         * @throws ExtractorException extrator exception
         */
        private Pair<Extractor, Class<?>> buildExtractor(Class<?> clazz, String comp) throws ExtractorException {

            Pair<Extractor, Class<?>> pair = getMethodExtractor(clazz, comp);

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

                return Pair.of(new MethodExtractor(meth), retType);

            } catch (NoSuchMethodException expected) {
                // no getXxx() method, maybe there's a field by this name
                logger.debug("no method {} in {}", nm, clazz.getName(), expected);
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

            Field field = getClassField(clazz, name);
            if (field == null) {
                return null;
            }

            return Pair.of(new FieldExtractor(field), field.getType());
        }

        /**
         * Gets an extractor for an item within a Map object.
         *
         * @param clazz container's class
         * @param key item key within the map
         * @return a new extractor, or {@code null} if the class is not a Map
         *         subclass
         */
        private Pair<Extractor, Class<?>> getMapExtractor(Class<?> clazz, String key) {

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
            return Pair.of(new MapExtractor(key), Map.class);
        }

        /**
         * Gets field within a class, examining all super classes and
         * interfaces.
         *
         * @param clazz class whose field is desired
         * @param name name of the desired field
         * @return the field within the class, or {@code null} if the field does
         *         not exist
         * @throws ExtractorException if the field is inaccessible
         */
        private Field getClassField(Class<?> clazz, String name) throws ExtractorException {
            if (clazz == null) {
                return null;
            }

            try {
                return clazz.getField(name);

            } catch (NoSuchFieldException expected) {
                // no field by this name - try super class & interfaces
                logger.debug("no field {} in {}", name, clazz.getName(), expected);
                return null;

            } catch (SecurityException e) {
                throw new ExtractorException("inaccessible field " + clazz + "." + name, e);
            }
        }
    }
}
