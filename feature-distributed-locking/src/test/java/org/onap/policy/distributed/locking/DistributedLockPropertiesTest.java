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

package org.onap.policy.distributed.locking;

import static org.junit.Assert.assertEquals;

import java.util.Properties;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.common.utils.properties.exception.PropertyException;

public class DistributedLockPropertiesTest {

    private Properties props;

    /**
     * Populates {@link #props}.
     */
    @Before
    public void setUp() {
        props = new Properties();

        props.setProperty(DistributedLockProperties.DB_DRIVER, "my driver");
        props.setProperty(DistributedLockProperties.DB_URL, "my url");
        props.setProperty(DistributedLockProperties.DB_USER, "my user");
        props.setProperty(DistributedLockProperties.DB_PASS, "my pass");
        props.setProperty(DistributedLockProperties.EXPIRE_CHECK_SEC, "100");
        props.setProperty(DistributedLockProperties.RETRY_SEC, "200");
        props.setProperty(DistributedLockProperties.MAX_RETRIES, "300");
    }

    @Test
    public void test() throws PropertyException {
        DistributedLockProperties dlp = new DistributedLockProperties(props);

        assertEquals("my driver", dlp.getDbDriver());
        assertEquals("my url", dlp.getDbUrl());
        assertEquals("my user", dlp.getDbUser());
        assertEquals("my pass", dlp.getDbPwd());
        assertEquals(100, dlp.getExpireCheckSec());
        assertEquals(200, dlp.getRetrySec());
        assertEquals(300, dlp.getMaxRetries());
    }
}
