/*-
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2017-2021 AT&T Intellectual Property. All rights reserved.
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Properties;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.configuration2.ConfigurationConverter;
import org.apache.commons.configuration2.SystemConfiguration;
import org.onap.policy.common.utils.security.CryptoCoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides utilities to read properties from a properties
 * file, and optionally get notifications of future changes.
 */
public class PropertyUtil {
    public static final String ENV_WITH_DEFAULT_PROPERTY_PREFIX = "envd";
    public static final String CRYPTO_CODER_PROPERTY_PREFIX = "enc";

    // timer thread used for polling for property file changes
    private static Timer timer = null;

    // this table maps canonical file into a 'ListenerRegistration' instance
    private static final HashMap<File, ListenerRegistration> registrations = new HashMap<>();

    private static final Logger logger = LoggerFactory.getLogger(PropertyUtil.class.getName());

    private static final AtomicReference<CryptoCoder> cryptoCoder = new AtomicReference<>();

    /**
     * Sets a default Crypto Coder.
     */
    public static void setDefaultCryptoCoder(CryptoCoder cryptoCoder) {
        PropertyUtil.cryptoCoder.set(cryptoCoder);
    }

    /**
     * Read in a properties file and performs variable interpolation.
     *
     * @param file the properties file
     * @return a Properties object, containing the associated properties
     * @throws IOException - subclass 'FileNotFoundException' if the file
     *     does not exist or can't be opened, and 'IOException' if there is
     *     a problem loading the properties file.
     */
    public static Properties getProperties(File file) throws IOException {
        return getInterpolatedProperties(getPropertiesFile(file));
    }

    /**
     * Reads a property files and performs variable interpolation.  It uses a
     * crypto coder to interpolate encrypted sensitive data.
     *
     * @param file the properties file
     * @param cryptoCoder a custom CryptoCoder implementation
     * @return a Properties object, containing the associated properties
     * @throws IOException - subclass 'FileNotFoundException' if the file
     *     does not exist or can't be opened, and 'IOException' if there is
     *     a problem loading the properties file.
     */
    public static Properties getProperties(File file, CryptoCoder cryptoCoder) throws IOException {
        return getInterpolatedProperties(getPropertiesFile(file), cryptoCoder);
    }

    /**
     * Read in a properties file.
     *
     * @param fileName the properties file
     * @return a Properties object, containing the associated properties
     * @throws IOException - subclass 'FileNotFoundException' if the file
     *     does not exist or can't be opened, and 'IOException' if there is
     *     a problem loading the properties file
     */
    public static Properties getProperties(String fileName) throws IOException {
        return getProperties(new File(fileName));
    }

    /**
     * Reads a property files and performs variable interpolation.  It uses a
     * crypto coder to interpolate encrypted sensitive data.
     *
     * @param fileName the properties file name
     * @param cryptoCoder a custom CryptoCoder implementation
     * @return a Properties object, containing the associated properties
     * @throws IOException - subclass 'FileNotFoundException' if the file
     *     does not exist or can't be opened, and 'IOException' if there is
     *     a problem loading the properties file
     */
    public static Properties getProperties(String fileName, CryptoCoder cryptoCoder) throws IOException {
        return getProperties(new File(fileName), cryptoCoder);
    }

    /**
     * Read in a properties file, and register for update notifications.
     * NOTE: it is possible that the first callback will occur while this
     * method is still in progress. To avoid this problem, use 'synchronized'
     * blocks around this invocation and in the callback -- that will ensure
     * that the processing of the initial properties complete before any
     * updates are processed.
     *
     * @param file the properties file
     * @param listener notify if not null, this is a callback interface that is used for
     *     notifications of changes
     * @return a Properties object, containing the associated properties
     * @throws IOException - subclass 'FileNotFoundException' if the file
     *     does not exist or can't be opened, and 'IOException' if there is
     *     a problem loading the properties file.
     */
    public static Properties getProperties(File file, Listener listener) throws IOException {
        var propFile = file;
        if (listener == null) {
            // no listener specified -- just fetch the properties
            return getProperties(propFile);
        }

        // Convert the file to a canonical form in order to avoid the situation
        // where different names refer to the same file.
        propFile = propFile.getCanonicalFile();

        // See if there is an existing registration. The 'synchronized' block
        // is needed to handle the case where a new listener is added at about
        // the same time that another one is being removed.
        synchronized (registrations) {
            ListenerRegistration reg = registrations.get(propFile);
            if (reg == null) {
                // a new registration is needed
                reg = new ListenerRegistration(propFile);
            }
            return reg.addListener(listener);
        }
    }

    /**
     * Read in a properties file, and register for update notifications.
     * NOTE: it is possible that the first callback will occur while this
     * method is still in progress. To avoid this problem, use 'synchronized'
     * blocks around this invocation and in the callback -- that will ensure
     * that the processing of the initial properties complete before any
     * updates are processed.
     *
     * @param fileName the properties file
     * @param listener notify if not null, this is a callback interface that is used for
     *     notifications of changes
     * @return a Properties object, containing the associated properties
     * @throws IOException - subclass 'FileNotFoundException' if the file
     *     does not exist or can't be opened, and 'IOException' if there is
     *     a problem loading the properties file.
     */
    public static Properties getProperties(String fileName, Listener listener) throws IOException {
        return getProperties(new File(fileName), listener);
    }

    /**
     * Get an interpolated properties object from a properties one.
     *
     * @param properties non-interpolated properties object
     * @return Properties - interpolated properties object
     */
    public static Properties getInterpolatedProperties(Properties properties) {
        return getInterpolatedProperties(properties, cryptoCoder.get());
    }

    /**
     * Get an interpolated properties object from a properties one.
     *
     * @param properties non-interpolated properties object
     * @param cryptoCoder crypto coder
     * @return Properties - interpolated properties object
     */
    public static Properties getInterpolatedProperties(Properties properties, CryptoCoder cryptoCoder) {
        var config = ConfigurationConverter.getConfiguration(properties);
        config.getInterpolator()
            .registerLookup(ENV_WITH_DEFAULT_PROPERTY_PREFIX, new EnvironmentVariableWithDefaultLookup());

        if (cryptoCoder == null) {
            return ConfigurationConverter.getProperties(config);
        }

        config.getInterpolator().registerLookup(CRYPTO_CODER_PROPERTY_PREFIX, new CryptoCoderValueLookup(cryptoCoder));
        var props = ConfigurationConverter.getProperties(config);
        props.stringPropertyNames().forEach(key -> props.setProperty(key, cryptoCoder.decrypt(props.getProperty(key))));
        return props;
    }

    /**
     * Read in a properties file from disk.
     *
     * @param file the properties file
     * @return a Properties object, containing the associated properties
     * @throws IOException - subclass 'FileNotFoundException' if the file
     *     does not exist or can't be opened, and 'IOException' if there is
     *     a problem loading the properties file.
     */
    protected static Properties getPropertiesFile(File file) throws IOException {
        // create an InputStream (may throw a FileNotFoundException)
        var rval = new Properties();
        try (var fis = new FileInputStream(file)) {
            // create the properties instance

            // load properties (may throw an IOException)
            rval.load(fis);
        }
        return rval;
    }

    /**
     * sets system properties from a properties file.
     *
     * @param properties properties file
     */
    public static void setSystemProperties(Properties properties) {
        SystemConfiguration
            .setSystemProperties(ConfigurationConverter
                .getConfiguration(getInterpolatedProperties(properties)));
    }

    /**
     * sets system properties from a properties file.
     *
     * @param properties properties file
     */
    public static void setSystemProperties(Properties properties, CryptoCoder cryptoCoder) {
        SystemConfiguration
            .setSystemProperties(ConfigurationConverter
                .getConfiguration(getInterpolatedProperties(properties, cryptoCoder)));
    }

    /* ============================================================ */

    /**
     * This is the callback interface, used for sending notifications of
     * changes in the properties file.
     */
    @FunctionalInterface
    public interface Listener {
        /**
         * Notification of a properties file change.
         *
         * @param properties the new properties
         * @param changedKeys the set of property names that have changed, including
         *      additions and removals
         */
        void propertiesChanged(Properties properties, Set<String> changedKeys);
    }

    /**
     * This is an internal class - one instance of this exists for each
     * property file that is being monitored. Note that multiple listeners
     * can be registered for the same file.
     */
    private static class ListenerRegistration {
        // the canonical path of the file being monitored
        File file;

        // the most recent value of 'file.lastModified()'
        long lastModified;

        // the most recent set of properties
        Properties properties;

        // the set of listeners monitoring this file
        LinkedList<Listener> listeners;

        // the 'TimerTask' instance, used for periodic polling
        TimerTask timerTask;

        /**
         * Constructor - create a 'ListenerRegistration' instance for this
         * file, but with no listeners.
         */
        ListenerRegistration(File file) throws IOException {
            this.file = file;

            // The initial value of 'lastModified' is set to 0 to ensure that we
            // correctly handle the case where the file is modified within the
            // same second that polling begins.
            lastModified = 0;

            // fetch current properties
            properties = getProperties(file);

            // no listeners yet
            listeners = new LinkedList<>();

            // add to static table, so this instance can be shared
            registrations.put(file, this);

            // still need to create a timer thread
            synchronized (PropertyUtil.class) {
                // an additional check is added inside the 'synchronized' block,
                // just in case someone beat us to it
                if (timer == null) {
                    timer = new Timer("PropertyUtil-Timer", true);
                }
            }

            // create and schedule the timer task, so this is periodically polled
            timerTask = new TimerTask() {
                @Override
                public void run() {
                    try {
                        poll();
                    } catch (Exception e) {
                        logger.warn("Polling for property changes", e);
                    }
                }
            };
            timer.schedule(timerTask, 10000L, 10000L);
        }

        /**
         * Add a listener to the notification list.
         *
         * @param listener this is the listener to add to the list
         * @return the properties at the moment the listener was added to the list
         */
        synchronized Properties addListener(Listener listener) {
            listeners.add(listener);
            return (Properties) properties.clone();
        }

        /**
         * Remove a listener from the notification list.
         *
         * @param listener this is the listener to remove
         */
        synchronized void removeListener(Listener listener) {
            listeners.remove(listener);

            // See if we need to remove this 'ListenerRegistration' instance
            // from the table. The 'synchronized' block is needed in case
            // another listener is being added at about the same time that this
            // one is being removed.
            synchronized (registrations) {
                if (listeners.isEmpty()) {
                    timerTask.cancel();
                    registrations.remove(file);
                }
            }
        }

        /**
         * This method is periodically called to check for property list updates.
         *
         * @throws IOException if there is an error in reading the properties file
         */
        synchronized void poll() throws IOException {
            long timestamp = file.lastModified();
            if (timestamp != lastModified) {
                // update the record, and send out the notifications
                lastModified = timestamp;

                // Save old set, and initial set of changed properties.
                var oldProperties = properties;
                HashSet<String> changedProperties =
                        new HashSet<>(oldProperties.stringPropertyNames());

                // Fetch the list of listeners that we will potentially notify,
                // and the new properties. Note that this is in a 'synchronized'
                // block to ensure that all listeners receiving notifications
                // actually have a newer list of properties than the one
                // returned on the initial 'getProperties' call.
                properties = getProperties(file);

                Set<String> newPropertyNames = properties.stringPropertyNames();
                changedProperties.addAll(newPropertyNames);

                // At this point, 'changedProperties' is the union of all properties
                // in both the old and new properties files. Iterate through all
                // of the entries in the new properties file - if the entry
                // matches the one in the old file, remove it from
                // 'changedProperties'.
                for (String name : newPropertyNames) {
                    if (properties.getProperty(name).equals(oldProperties.getProperty(name))) {
                        // Apparently, any property that exists must be of type
                        // 'String', and can't be null. For this reason, we don't
                        // need to worry about the case where
                        // 'properties.getProperty(name)' returns 'null'. Note that
                        // 'oldProperties.getProperty(name)' may be 'null' if the
                        // old property does not exist.
                        changedProperties.remove(name);
                    }
                }

                // 'changedProperties' should be correct at this point
                if (!changedProperties.isEmpty()) {
                    // there were changes - notify everyone in 'listeners'
                    for (final Listener notify : listeners) {
                        // Copy 'properties' and 'changedProperties', so it doesn't
                        // cause problems if the recipient makes changes.
                        final var tmpProperties = (Properties) properties.clone();
                        final HashSet<String> tmpChangedProperties = new HashSet<>(changedProperties);

                        // Do the notification in a separate thread, so blocking
                        // won't cause any problems.
                        new Thread(() -> notify.propertiesChanged(tmpProperties, tmpChangedProperties)).start();
                    }
                }
            }
        }
    }

    /**
     * Stop listenening for updates.
     *
     * @param file the properties file
     * @param listener notify if not null, this is a callback interface that was used for
     *     notifications of changes
     */
    public static void stopListening(File file, Listener listener) {
        if (listener != null) {
            ListenerRegistration reg = registrations.get(file);
            if (reg != null) {
                reg.removeListener(listener);
            }
        }
    }

    /**
     * Stop listenening for updates.
     *
     * @param fileName the properties file
     * @param listener notify if not null, this is a callback interface that was used for
     *     notifications of changes
     */
    public static void stopListening(String fileName, Listener listener) {
        stopListening(new File(fileName), listener);
    }

}
