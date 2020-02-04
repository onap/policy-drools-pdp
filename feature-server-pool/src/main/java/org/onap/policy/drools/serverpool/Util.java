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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Comparator;
import java.util.Timer;
import java.util.UUID;

public class Util {
    // create a shared 'Timer' instance
    public static final Timer timer = new Timer("Server Pool Timer", true);

    /**
     * Internally, UUID objects use two 'long' variables, and the default
     * comparison is signed, which means the order for the first and 16th digit
     * is: '89abcdef01234567', while the order for the rest is
     * '0123456789abcdef'.
     * The following comparator uses the ordering '0123456789abcdef' for all
     * digits.
     */
    public static final Comparator<UUID> uuidComparator =
        new Comparator<UUID>() {
            public int compare(UUID u1, UUID u2) {
                // compare most significant portion
                int rval = Long.compareUnsigned(u1.getMostSignificantBits(),
                                                u2.getMostSignificantBits());
                if (rval == 0) {
                    // most significant portion matches --
                    // compare least significant portion
                    rval = Long.compareUnsigned(u1.getLeastSignificantBits(),
                                                u2.getLeastSignificantBits());
                }
                return rval;
            }
        };

    /* ============================================================ */

    /**
     * write a UUID to an output stream.
     *
     * @param ds the output stream
     * @param uuid the uuid to write
     */
    public static void writeUuid(DataOutputStream ds, UUID uuid) throws IOException {
        // write out 16 byte UUID
        ds.writeLong(uuid.getMostSignificantBits());
        ds.writeLong(uuid.getLeastSignificantBits());
    }

    /**
     * read a UUID from an input stream.
     *
     * @param ds the input stream
     */
    public static UUID readUuid(DataInputStream ds) throws IOException {
        long mostSigBits = ds.readLong();
        long leastSigBits = ds.readLong();
        return new UUID(mostSigBits, leastSigBits);
    }

    /* ============================================================ */

    /**
     * Read from an 'InputStream' until EOF or until it is closed.  This method
     * may block, depending on the type of 'InputStream'.
     *
     * @param input This is the input stream
     * @return A 'String' containing the contents of the input stream
     */
    public static String inputStreamToString(InputStream input) {
        StringBuilder sb = new StringBuilder();
        byte[] buffer = new byte[8192];
        int length;

        try {
            while ((length = input.read(buffer)) > 0) {
                sb.append(new String(buffer, 0, length));
            }
        } catch (IOException e) {
            // return what we have so far
        }
        return sb.toString();
    }

    /* ============================================================ */

    /**
     * Serialize an object into a byte array.
     *
     * @param object the object to serialize
     * @return a byte array containing the serialized object
     * @throws IOException this may be an exception thrown by the output stream,
     *     a NotSerializableException if an object can't be serialized, or an
     *     InvalidClassException
     */
    public static byte[] serialize(Object object) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(object);
            oos.flush();
            return bos.toByteArray();
        }
    }

    /**
     * Deserialize a byte array into an object.
     *
     * @param data a byte array containing the serialized object
     * @return the deserialized object
     * @throws IOException this may be an exception thrown by the input stream,
     *     a StreamCorrupted Exception if the information in the stream is not
     *     consistent, an OptionalDataException if the input data primitive data,
     *     rather than an object, or InvalidClassException
     * @throws ClassNotFoundException if the class of a serialized object can't
     *     be found
     */
    public static Object deserialize(byte[] data) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
                    ObjectInputStream ois = new ObjectInputStream(bis)) {
            return ois.readObject();
        }
    }

    /**
     * Deserialize a byte array into an object.
     *
     * @param data a byte array containing the serialized object
     * @param classLoader the class loader to use when locating classes
     * @return the deserialized object
     * @throws IOException this may be an exception thrown by the input stream,
     *     a StreamCorrupted Exception if the information in the stream is not
     *     consistent, an OptionalDataException if the input data primitive data,
     *     rather than an object, or InvalidClassException
     * @throws ClassNotFoundException if the class of a serialized object can't
     *     be found
     */
    public static Object deserialize(byte[] data, ClassLoader classLoader)
        throws IOException, ClassNotFoundException {

        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
                    ExtendedObjectInputStream ois =
                        new ExtendedObjectInputStream(bis, classLoader)) {
            return ois.readObject();
        }
    }

    /**
     * Shutdown the timer thread.
     */
    public static void shutdown() {
        timer.cancel();
    }
}
