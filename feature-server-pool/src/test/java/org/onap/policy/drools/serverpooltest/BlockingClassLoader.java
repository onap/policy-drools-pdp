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

package org.onap.policy.drools.serverpooltest;

import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.NoSuchElementException;

/**
 * Ordinarily, a 'ClassLoader' first attempts to load a class via the
 * parent 'ClassLoader'. If that fails, it attempts to load it "locally"
 * by whatever mechanism the class loader supports.
 * This 'ClassLoader' instance blocks attempts to load specific classes,
 * throwing a 'ClassNotFoundException'. This doesn't seem useful on the
 * surface, but it forces all child 'ClassLoader' instances to do the lookup
 * themselves. In addition, each child 'ClassLoader' will have their own
 * copy of the classes they load, providing a way to have multiple copies of
 * the same class running within the same JVM. Each child 'ClassLoader' can
 * be viewed as having a separate name space.
 */
public class BlockingClassLoader extends ClassLoader {
    // these are the set of packages to block
    private HashSet<String> packages;

    // these are the prefixes of class names to block
    private ArrayList<String> prefixes;

    // these specific classes will not be blocked, even if they are in one
    // of the packages indicated by 'packages'
    private HashSet<String> excludes = new HashSet<String>();

    // these are the prefixes of class names to exclude
    private ArrayList<String> excludePrefixes = new ArrayList<>();

    /**
     * Constructor -- initialize the 'ClassLoader' and 'packages' variable.
     *
     * @param parent the parent ClassLoader
     * @param packages variable number of packages to block
     */
    public BlockingClassLoader(ClassLoader parent, String... packages) {
        super(parent);
        this.packages = new HashSet<>();
        this.prefixes = new ArrayList<>();
        for (String pkg : packages) {
            if (pkg.endsWith("*")) {
                prefixes.add(pkg.substring(0, pkg.length() - 1));
            } else {
                this.packages.add(pkg);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        // throws a 'ClassNotFoundException' if we are blocking this one
        testClass(name);

        // not blocking this one -- pass it on to the superclass
        return super.findClass(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Enumeration<URL> getResources(String name) {
        // in order to avoid replicated resources, we return an empty set
        return new Enumeration<URL>() {
            public boolean hasMoreElements() {
                return false;
            }

            public URL nextElement() {
                throw new NoSuchElementException("'BlockingClassLoader' blocks duplicate resources");
            }
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        // throws a 'ClassNotFoundException' if we are blocking this one
        testClass(name);

        // not blocking this one -- pass it on to the superclass
        return super.loadClass(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // throws a 'ClassNotFoundException' if we are blocking this one
        testClass(name);

        // not blocking this one -- pass it on to the superclass
        return super.loadClass(name, resolve);
    }

    /**
     * Add an entry to the list of classes that should NOT be blocked.
     *
     * @param name the full name of a class that shouldn't be blocked
     */
    public void addExclude(String name) {
        if (name.endsWith("*")) {
            excludePrefixes.add(name.substring(0, name.length() - 1));
        } else {
            excludes.add(name);
        }
    }

    /**
     * This method looks at a class name -- if it should be blocked, a
     * 'ClassNotFoundException' is thrown. Otherwise, it does nothing.
     *
     * @param name the name of the class to be tested
     * @throws ClassNotFoundException if this class should be blocked
     */
    private void testClass(String name) throws ClassNotFoundException {
        if (excludes.contains(name)) {
            // allow this one
            return;
        }

        for (String prefix : excludePrefixes) {
            if (name.startsWith(prefix)) {
                // allow this one
                return;
            }
        }

        // extract the package from the class name -- throw a
        // 'ClassNotFoundException' if the package is in the list
        // being blocked
        int index = name.lastIndexOf('.');
        if (index >= 0) {
            if (packages.contains(name.substring(0, index))) {
                throw(new ClassNotFoundException(name));
            }

            for (String prefix : prefixes) {
                if (name.startsWith(prefix)) {
                    throw(new ClassNotFoundException(name));
                }
            }
        }
    }
}
