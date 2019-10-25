/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2017-2019 AT&T Intellectual Property. All rights reserved.
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

import org.apache.commons.configuration2.interpol.Lookup;
import org.apache.commons.lang3.StringUtils;
import org.onap.policy.common.utils.security.CryptoCoder;

/**
 * Crypto Coder value look up.  Syntax:  ${enc:encoded-value}.
 */
public class CryptoCoderValueLookup implements Lookup {

    protected final CryptoCoder cryptoCoder;

    /**
     * Crypto Coder Lookup.
     *
     * @param crypto crypto coder
     */
    public CryptoCoderValueLookup(CryptoCoder crypto) {
        this.cryptoCoder = crypto;
    }

    @Override
    public String lookup(String key) {
        if (StringUtils.isBlank(key)) {
            return null;
        }

        return cryptoCoder.decrypt(PropertyUtil.CRYPTO_CODER_PROPERTY_PREFIX + ":" + key);
    }
}
