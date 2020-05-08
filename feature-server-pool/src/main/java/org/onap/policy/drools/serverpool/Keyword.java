/*
 * ============LICENSE_START=======================================================
 * feature-server-pool
 * ================================================================================
 * Copyright (C) 2020 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.serverpool;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import lombok.AllArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class supports the lookup of keywords from objects within Drools
 * sessions. It maps the class of the object into an object implementing
 * the 'Keyword.Lookup' interface. At present, this requires writing
 * special code for each class that can exist in a Drools session that is
 * assignable and relocatable via a bucket. In theory, it would be possible
 * to populate this table through properties, which would use the reflective
 * interface, and  indicate the methods and fields to use to do this lookup.
 */
public class Keyword {
    private static Logger logger = LoggerFactory.getLogger(Keyword.class);

    // this table can be used to map an object class into the method
    // to invoke to do the lookup
    private static ConcurrentHashMap<Class<?>, Lookup> classToLookup =
        new ConcurrentHashMap<>();

    // this is a pre-defined 'Lookup' instance that always returns 'null'
    private static Lookup nullLookup = (Object obj) -> (String) null;

    /**
     * This method takes the object's class, looks it up in the 'classToLookup'
     * table, and then performs the lookup to get the keyword. When a direct
     * lookup on a class fails, it will attempt to find a match using inheritance
     * rules -- if an appropriate match is found, the 'classToLookup' table is
     * updated, so it will be easier next time. If no match is found, the table
     * is also updated, but the 'value' will be 'nullLookup'.
     *
     * @param obj object to to the lookup on
     * @return a String keyword, if found; 'null' if not
     */
    public static String lookupKeyword(Object obj) {
        Lookup lu = classToLookup.get(obj.getClass());
        if (lu != null) {
            return lu.getKeyword(obj);
        }
        // no entry for this class yet --
        // try to locate a matching entry using 'inheritance' rules
        Class<?> thisClass = obj.getClass();
        Class<?> matchingClass = null;
        for (Map.Entry<Class<?>, Lookup> entry : classToLookup.entrySet()) {
            if (entry.getKey().isAssignableFrom(thisClass)
                    && (matchingClass == null
                       || matchingClass.isAssignableFrom(entry.getKey()))) {
                // we have a first match, or a more specific match
                matchingClass = entry.getKey();
                lu = entry.getValue();
            }
        }

        /*
         * whether we found a match or not, update the table accordingly
         * no match found -- see if the 'keyword.<CLASS-NAME>.lookup'
         * properties can provide a solution.
         */
        if (lu == null && (lu = buildReflectiveLookup(thisClass)) == null) {
            lu = nullLookup;
        }

        // update table
        classToLookup.put(thisClass, lu);
        return lu.getKeyword(obj);
    }

    /**
     * explicitly place an entry in the table.
     *
     * @param clazz the class to do the lookup on
     * @param handler an instance implementing the 'Lookup' interface,
     *     can handle instances of type 'clazz'
     */
    public static void setLookupHandler(Class<?> clazz, Lookup handler) {
        classToLookup.put(clazz, handler);
    }

    /* ============================================================ */

    /**
     * These are the interface that must be implemented by objects in the
     * 'classToLookup' table.
     */
    public interface Lookup {
        /**
         * Map the object into a keyword string.
         *
         * @param obj the object to lookup, which should be an instance of the
         *     associated class in the 'classToLookup' table
         * @return the keyword, if found; 'null' if not
         */
        public String getKeyword(Object obj);
    }

    /* ============================================================ */

    // this table maps class name to a sequence of method calls and field
    // references, based upon 'keyword.<CLASS-NAME>.lookup' entries found
    // in the property list
    private static Map<String,String> classNameToSequence = null;

    static final String KEYWORD_PROPERTY_START = "keyword.";
    static final String KEYWORD_PROPERTY_END = ".lookup";

    /**
     * Attempt to build a 'Lookup' instance for a particular class based upon
     * properties.
     *
     * @param clazz the class to build an entry for
     * @return a 'Lookup' instance to do the lookup, or 'null' if one can't
     *     be generated from the available properties
     */
    private static synchronized Lookup buildReflectiveLookup(Class<?> clazz) {
        if (classNameToSequence == null) {
            classNameToSequence = new HashMap<>();
            Properties prop = ServerPoolProperties.getProperties();

            /*
             * iterate over all of the properties, picking out those
             * that match the name 'keyword.<CLASS-NAME>.lookup'
             */
            for (String name : prop.stringPropertyNames()) {
                if (name.startsWith(KEYWORD_PROPERTY_START)
                        && name.endsWith(KEYWORD_PROPERTY_END)) {
                    // this property matches -- locate the '<CLASS-NAME>' part
                    int beginIndex = KEYWORD_PROPERTY_START.length();
                    int endIndex = name.length()
                                   - KEYWORD_PROPERTY_END.length();
                    if (beginIndex < endIndex) {
                        // add it to the table
                        classNameToSequence.put(name.substring(beginIndex, endIndex),
                            prop.getProperty(name));
                    }
                }
            }
        }

        Class<?> keyClass = buildReflectiveLookup_findKeyClass(clazz);

        if (keyClass == null) {
            // no matching class name found
            return null;
        }

        return buildReflectiveLookup_build(clazz, keyClass);
    }

    /**
     * Look for the "best match" for class 'clazz' in the hash table.
     * First, look for the name of 'clazz' itself, followed by all of
     * interfaces. If no match is found, repeat with the superclass,
     * and all the way up the superclass chain.
     */
    private static Class<?> buildReflectiveLookup_findKeyClass(Class<?> clazz) {
        Class<?> keyClass = null;
        for (Class<?> cl = clazz ; cl != null ; cl = cl.getSuperclass()) {
            if (classNameToSequence.containsKey(cl.getName())) {
                // matches the class
                keyClass = cl;
                break;
            }
            for (Class<?> intf : cl.getInterfaces()) {
                if (classNameToSequence.containsKey(intf.getName())) {
                    // matches one of the interfaces
                    keyClass = intf;
                    break;
                }
                // interface can have superclass
                for (Class<?> cla = clazz; cla != null; cla = intf.getSuperclass()) {
                    if (classNameToSequence.containsKey(cla.getName())) {
                        // matches the class
                        keyClass = cla;
                        break;
                    }
                }
            }
            if (keyClass != null) {
                break;
            }
        }
        return keyClass;
    }

    private static Lookup buildReflectiveLookup_build(Class<?> clazz, Class<?> keyClass) {
        // we found a matching key in the table -- now, process the values
        Class<?> currentClass = keyClass;

        /**
         * there may potentially be a chain of entries if multiple
         * field and/or method calls are in the sequence -- this is the first
         */
        ReflectiveLookup first = null;

        // this is the last entry in the list
        ReflectiveLookup last = null;

        /**
         * split the value into segments, where each segment has the form
         * 'FIELD-NAME' or 'METHOD-NAME()', with an optional ':CONVERSION'
         * at the end
         */
        String sequence = classNameToSequence.get(keyClass.getName());
        ConversionFunctionLookup conversionFunctionLookup = null;
        int index = sequence.indexOf(':');
        if (index >= 0) {
            // conversion function specified
            conversionFunctionLookup =
                new ConversionFunctionLookup(sequence.substring(index + 1));
            sequence = sequence.substring(0, index);
        }
        for (String segment : sequence.split("\\.")) {
            ReflectiveLookup current = null;
            ReflectiveOperationException error = null;
            try {
                if (segment.endsWith("()")) {
                    // this segment is a method lookup
                    current = new MethodLookup(currentClass,
                        segment.substring(0, segment.length() - 2));
                } else {
                    // this segment is a field lookup
                    current = new FieldLookup(currentClass, segment);
                }
            } catch (ReflectiveOperationException e) {
                // presumably the field or method does not exist in this class
                error = e;
            }
            if (current == null) {
                logger.error("Keyword.buildReflectiveLookup: build error "
                             + "(class={},value={},segment={})",
                             clazz.getName(),
                             classNameToSequence.get(keyClass.getName()),
                             segment,
                             error);
                return null;
            }

            // if we reach this point, we processed this segment successfully
            currentClass = current.nextClass();
            if (first == null) {
                // the initial segment
                first = current;
            } else {
                // link to the most recently created segment
                last.next = current;
            }
            // update most recently created segment
            last = current;
        }

        // add optional conversion function ('null' if it doesn't exist)
        last.next = conversionFunctionLookup;

        // successful - return the first 'Lookup' instance in the chain
        return first;
    }

    /* ============================================================ */

    /**
     * Abstract superclass of 'FieldLookup' and 'MethodLookup'.
     */
    private abstract static class ReflectiveLookup implements Lookup {
        // link to the next 'Lookup' instance in the chain
        Lookup next = null;

        /**
         * Return the next 'class' instance.
         *
         * @return the class associated with the return value of the
         *     field or method lookup
         */
        abstract Class<?> nextClass();
    }

    /* ============================================================ */

    /**
     * This class is used to do a field lookup.
     */
    private static class FieldLookup extends ReflectiveLookup {
        // the reflective 'Field' instance associated with this lookup
        Field field;

        /**
         * Constructor.
         *
         * @param clazz the 'class' we are doing the field lookup on
         * @param segment a segment from the property value, which is just the
         *     field name
         */
        FieldLookup(Class<?> clazz, String segment) throws NoSuchFieldException {
            field = clazz.getField(segment);
        }

        /********************************/
        /* 'ReflectiveLookup' interface */
        /********************************/

        /**
         * {@inheritDoc}
         */
        @Override
        Class<?> nextClass() {
            return field.getType();
        }

        /**********************/
        /* 'Lookup' interface */
        /**********************/

        /**
         * {@inheritDoc}
         */
        @Override
        public String getKeyword(Object obj) {
            try {
                // do the field lookup
                Object rval = field.get(obj);
                if (rval == null) {
                    return null;
                }

                // If there is no 'next' entry specified, this value is the
                // keyword. Otherwise, move on to the next 'Lookup' entry in
                // the chain.
                return next == null ? rval.toString() : next.getKeyword(rval);
            } catch (Exception e) {
                logger.error("Keyword.FieldLookup error: field={}",
                             field, e);
                return null;
            }
        }
    }

    /* ============================================================ */

    /**
     * This class is used to do a method call on the target object.
     */
    private static class MethodLookup extends ReflectiveLookup {
        // the reflective 'Method' instance associated with this lookup
        Method method;

        /**
         * Constructor.
         *
         * @param clazz the 'class' we are doing the method lookup on
         * @param name a method name extracted from a segment from the
         *     property value, which is the
         */
        MethodLookup(Class<?> clazz, String name) throws NoSuchMethodException {
            method = clazz.getMethod(name);
        }

        /*==============================*/
        /* 'ReflectiveLookup' interface */
        /*==============================*/

        /**
         * {@inheritDoc}
         */
        @Override
        Class<?> nextClass() {
            return method.getReturnType();
        }

        /*====================*/
        /* 'Lookup' interface */
        /*====================*/

        /**
         * {@inheritDoc}
         */
        @Override
        public String getKeyword(Object obj) {
            try {
                // do the method call
                Object rval = method.invoke(obj);
                if (rval == null) {
                    return null;
                }

                // If there is no 'next' entry specified, this value is the
                // keyword. Otherwise, move on to the next 'Lookup' entry in
                // the chain.
                return next == null ? rval.toString() : next.getKeyword(rval);
            } catch (Exception e) {
                logger.error("Keyword.MethodLookup error: method={}",
                             method, e);
                return null;
            }
        }
    }

    /* ============================================================ */

    /*
     * Support for named "conversion functions", which take an input keyword,
     * and return a possibly different keyword derived from it. The initial
     * need is to take a string which consists of a UUID and a suffix, and
     * return the base UUID.
     */

    // used to lookup optional conversion functions
    private static Map<String, Function<String, String>> conversionFunction =
        new ConcurrentHashMap<>();

    // conversion function 'uuid':
    // truncate strings to 36 characters(uuid length)
    static final int UUID_LENGTH = 36;

    static {
        conversionFunction.put("uuid", value -> {
            // truncate strings to 36 characters
            return value != null && value.length() > UUID_LENGTH
                ? value.substring(0, UUID_LENGTH) : value;
        });
    }

    /**
     * Add a conversion function.
     *
     * @param name the conversion function name
     * @param function the object that does the transformation
     */
    public static void addConversionFunction(String name, Function<String, String> function) {
        conversionFunction.put(name, function);
    }

    /**
     * Apply a named conversion function to a keyword.
     *
     * @param inputKeyword this is the keyword extracted from a message or object
     * @param functionName this is the name of the conversion function to apply
     *     (if 'null', no conversion is done)
     * @return the converted keyword
     */
    public static String convertKeyword(String inputKeyword, String functionName) {
        if (functionName == null || inputKeyword == null) {
            // don't do any conversion -- just return the input keyword
            return inputKeyword;
        }

        // look up the function
        Function<String, String> function = conversionFunction.get(functionName);
        if (function == null) {
            logger.error("{}: conversion function not found", functionName);
            return null;
        }

        // call the conversion function, and return the value
        return function.apply(inputKeyword);
    }

    /**
     * This class is used to invoke a conversion function.
     */
    @AllArgsConstructor
    private static class ConversionFunctionLookup implements Lookup {
        // the conversion function name
        private final String functionName;

        /**
         * {@inheritDoc}
         */
        @Override
        public String getKeyword(Object obj) {
            return obj == null ? null : convertKeyword(obj.toString(), functionName);
        }
    }
}
