package org.onap.policy.drools.utils;

import static org.junit.Assert.*;

import org.junit.Test;

public class LoggerUtilTest {

	@Test
	public void test() {
		assertNotNull(LoggerUtil.setLevel("foo", "warn"));
	}

}
