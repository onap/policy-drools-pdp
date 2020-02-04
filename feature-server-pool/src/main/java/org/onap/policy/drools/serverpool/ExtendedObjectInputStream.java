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

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;

/**
 * This class provides an 'ObjectInputStream' variant that uses the
 * specified 'ClassLoader' instance.
 */
public class ExtendedObjectInputStream extends ObjectInputStream {
    // the 'ClassLoader' to use when doing class lookups
    private ClassLoader classLoader;

    /**
     * Constructor -- invoke the superclass, and save the 'ClassLoader'.
     *
     * @param in input stream to read from
     * @param classLoader 'ClassLoader' to use when doing class lookups
     */
    public ExtendedObjectInputStream(InputStream in, ClassLoader classLoader) throws IOException {
        super(in);
        this.classLoader = classLoader;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {

        // Standard ClassLoader implementations first attempt to load classes
        // via the parent class loader, and then attempt to load it using the
        // current class loader if that fails. For some reason, Drools container
        // class loaders define a different order -- in theory, this is only a
        // problem if different versions of the same class are accessible through
        // different class loaders, which is exactly what happens in some Junit
        // tests.
        //
        // This change restores the order, at least when deserializing objects
        // into a Drools container.
        try {
            // try the parent class loader first
            return classLoader.getParent().loadClass(desc.getName());
        } catch (ClassNotFoundException e) {
            return classLoader.loadClass(desc.getName());
        }
    }
}
