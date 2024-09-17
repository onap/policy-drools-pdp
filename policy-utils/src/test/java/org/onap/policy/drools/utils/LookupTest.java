/*-
 * ============LICENSE_START================================================
 * Copyright (C) 2024 Nordix Foundation.
 * =========================================================================
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
 * ============LICENSE_END==================================================
 */

package org.onap.policy.drools.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.onap.policy.common.utils.security.CryptoCoder;

class LookupTest {

    @Test
    void testCryptoLookup() {
        var cryptoCoder = new CryptoCoderValueLookup(new CryptoCoder() {
            @Override
            public String encrypt(String s) {
                return String.valueOf(s.hashCode());
            }

            @Override
            public String decrypt(String s) {
                return s;
            }
        });

        assertTrue(cryptoCoder.lookup("hello").startsWith("enc"));
        assertNull(cryptoCoder.lookup(null));
        assertNull(cryptoCoder.lookup(""));
    }

    @Test
    void testEnvDefaultLookup() {
        var envLookup = new EnvironmentVariableWithDefaultLookup();

        assertNull(envLookup.lookup(null));
        assertNull(envLookup.lookup(""));
        assertEquals("", envLookup.lookup(":"));
    }
}