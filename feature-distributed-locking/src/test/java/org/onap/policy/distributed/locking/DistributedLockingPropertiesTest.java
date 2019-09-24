package org.onap.policy.distributed.locking;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Properties;
import java.util.TreeSet;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.common.utils.properties.exception.PropertyException;

public class DistributedLockingPropertiesTest {

    private Properties props;

    /**
     * Populates {@link #props}.
     */
    @Before
    public void setUp() {
        props = new Properties();

        props.setProperty(DistributedLockingProperties.DB_DRIVER, "my driver");
        props.setProperty(DistributedLockingProperties.DB_URL, "my url");
        props.setProperty(DistributedLockingProperties.DB_USER, "my user");
        props.setProperty(DistributedLockingProperties.DB_PASS, "my pass");
        props.setProperty(DistributedLockingProperties.TRANSIENT_ERROR_CODES, "10,-20,,,30");
        props.setProperty(DistributedLockingProperties.EXPIRE_CHECK_SEC, "100");
        props.setProperty(DistributedLockingProperties.RETRY_SEC, "200");
        props.setProperty(DistributedLockingProperties.MAX_RETRIES, "300");
        props.setProperty(DistributedLockingProperties.MAX_THREADS, "4");
    }

    @Test
    public void test() throws PropertyException {
        DistributedLockingProperties dlp = new DistributedLockingProperties(props);

        assertEquals("my driver", dlp.getDbDriver());
        assertEquals("my url", dlp.getDbUrl());
        assertEquals("my user", dlp.getDbUser());
        assertEquals("my pass", dlp.getDbPwd());
        assertEquals("10,-20,,,30", dlp.getErrorCodeStrings());
        assertEquals("[-20, 10, 30]", new TreeSet<>(dlp.getTransientErrorCodes()).toString());
        assertEquals(100, dlp.getExpireCheckSec());
        assertEquals(200, dlp.getRetrySec());
        assertEquals(300, dlp.getMaxRetries());
        assertEquals(4, dlp.getMaxThreads());

        assertTrue(dlp.isTransient(10));
        assertTrue(dlp.isTransient(-20));
        assertTrue(dlp.isTransient(30));

        assertFalse(dlp.isTransient(-10));

        // invalid value
        props.setProperty(DistributedLockingProperties.TRANSIENT_ERROR_CODES, "10,abc,30");

        assertThatThrownBy(() -> new DistributedLockingProperties(props)).isInstanceOf(PropertyException.class)
                        .hasMessageContaining(DistributedLockingProperties.TRANSIENT_ERROR_CODES);
    }
}
