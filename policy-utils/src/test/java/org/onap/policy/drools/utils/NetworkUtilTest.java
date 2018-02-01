package org.onap.policy.drools.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;

import org.junit.Test;

public class NetworkUtilTest {

	@Test
	public void test() throws InterruptedException, IOException {
		assertNotNull(NetworkUtil.IPv4_WILDCARD_ADDRESS);
		assertFalse(NetworkUtil.isTcpPortOpen("localhost", 8080, 1, 5));
	}

}
