/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.core.lock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.onap.policy.drools.core.lock.TestUtils.expectException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.Test;
import org.onap.policy.drools.core.lock.PolicyResourceLockFeatureAPI.Callback;

public class LockRequestFutureTest {

    private static final int WAIT_SEC = 1;
    private static final String RESOURCE = "my.resource";
    private static final String OWNER = "my.owner";
    private static final String EXPECTED_EXCEPTION = "expected exception";

    private Callback callback;
    private LockRequestFuture fut;

    @Before
    public void setUp() {
        callback = mock(Callback.class);
        fut = new LockRequestFuture(RESOURCE, OWNER, callback);
    }

    @Test
    public void testLockRequestFutureStringStringBoolean_False() throws Exception {
        fut = new LockRequestFuture(RESOURCE, OWNER, false);

        assertTrue(fut.isDone());
        assertEquals(RESOURCE, fut.getResourceId());
        assertEquals(OWNER, fut.getOwner());

        assertFalse(fut.isLocked());
        assertFalse(fut.get(0, TimeUnit.SECONDS));
    }

    @Test
    public void testLockRequestFutureStringStringBoolean_True() throws Exception {
        fut = new LockRequestFuture(RESOURCE, OWNER, true);

        assertTrue(fut.isDone());
        assertEquals(RESOURCE, fut.getResourceId());
        assertEquals(OWNER, fut.getOwner());

        assertTrue(fut.isLocked());
        assertTrue(fut.get(0, TimeUnit.SECONDS));
    }

    @Test
    public void testLockRequestFutureStringStringBoolean_ArgEx() throws Exception {

        // null resource id
        IllegalArgumentException ex = expectException(IllegalArgumentException.class,
                        xxx -> new LockRequestFuture(null, OWNER, true));
        assertEquals("null resourceId", ex.getMessage());


        // null owner
        ex = expectException(IllegalArgumentException.class, xxx -> new LockRequestFuture(RESOURCE, null, true));
        assertEquals("null owner", ex.getMessage());
    }

    @Test
    public void testLockRequestFutureStringStringCallback() throws Exception {
        assertFalse(fut.isDone());
        assertEquals(RESOURCE, fut.getResourceId());
        assertEquals(OWNER, fut.getOwner());

        fut.setLocked(true);
        fut.invokeCallback();

        // ensure it invoked the callback
        verify(callback).set(true);
    }

    @Test
    public void testLockRequestFutureStringStringCallback_ArgEx() throws Exception {

        // null resource id
        IllegalArgumentException ex = expectException(IllegalArgumentException.class,
                        xxx -> new LockRequestFuture(null, OWNER, callback));
        assertEquals("null resourceId", ex.getMessage());


        // null owner
        ex = expectException(IllegalArgumentException.class, xxx -> new LockRequestFuture(RESOURCE, null, callback));
        assertEquals("null owner", ex.getMessage());


        // null callback is OK
        new LockRequestFuture(RESOURCE, OWNER, null);
    }

    @Test
    public void testGetResourceId() {
        assertEquals(RESOURCE, fut.getResourceId());
    }

    @Test
    public void testGetOwner() {
        assertEquals(OWNER, fut.getOwner());
    }

    @Test
    public void testCancel() throws Exception {
        // not cancelled yet
        assertFalse(fut.isDone());

        // cancel it
        assertTrue(fut.cancel(false));
        assertTrue(fut.isDone());

        // should not block now
        expectException(CancellationException.class, xxx -> fut.get(0, TimeUnit.SECONDS));

    }

    @Test
    public void testCancel_AlreadyCancelled() throws Exception {

        fut.cancel(true);

        assertFalse(fut.cancel(true));
        assertTrue(fut.isDone());

        expectException(CancellationException.class, xxx -> fut.get(0, TimeUnit.SECONDS));
    }

    @Test
    public void testCancel_AlreadyAcquired() throws Exception {

        fut.setLocked(true);

        assertFalse(fut.cancel(true));
        assertTrue(fut.isDone());
        assertTrue(fut.get(0, TimeUnit.SECONDS));
    }

    @Test
    public void testCancel_AlreadyDenied() throws Exception {

        fut.setLocked(false);

        assertFalse(fut.cancel(true));
        assertTrue(fut.isDone());
        assertFalse(fut.get(0, TimeUnit.SECONDS));
    }

    @Test
    public void testSetLocked_True() throws Exception {
        assertTrue(fut.setLocked(true));

        assertTrue(fut.isDone());
        assertTrue(fut.get(0, TimeUnit.SECONDS));
    }

    @Test
    public void testSetLocked_False() throws Exception {
        assertTrue(fut.setLocked(false));

        assertTrue(fut.isDone());
        assertFalse(fut.get(0, TimeUnit.SECONDS));
    }

    @Test
    public void testSetLocked_AlreadyCancelled() {

        fut.cancel(true);

        assertFalse(fut.setLocked(true));
        assertFalse(fut.setLocked(false));

        assertTrue(fut.isDone());
        expectException(CancellationException.class, xxx -> fut.get(0, TimeUnit.SECONDS));
    }

    @Test
    public void testSetLocked_AlreadyAcquired() throws Exception {
        fut.setLocked(true);

        assertTrue(fut.isDone());
        assertTrue(fut.get(0, TimeUnit.SECONDS));

        assertFalse(fut.cancel(true));
        assertFalse(fut.setLocked(true));
        assertFalse(fut.setLocked(false));
    }

    @Test
    public void testSetLocked_AlreadyDenied() throws Exception {
        fut.setLocked(false);

        assertTrue(fut.isDone());
        assertFalse(fut.get(0, TimeUnit.SECONDS));

        assertFalse(fut.cancel(true));
        assertFalse(fut.setLocked(true));
        assertFalse(fut.setLocked(false));
    }

    @Test
    public void testIsCancelled() {
        assertFalse(fut.isCancelled());

        fut.cancel(false);
        assertTrue(fut.isCancelled());
    }

    @Test
    public void testIsCancelled_Acquired() {
        fut.setLocked(true);
        assertFalse(fut.isCancelled());
    }

    @Test
    public void testIsCancelled_Denied() {
        fut.setLocked(false);
        assertFalse(fut.isCancelled());
    }

    @Test
    public void testIsDone_Cancelled() {
        fut.cancel(false);
        assertTrue(fut.isDone());
    }

    @Test
    public void testIsDone_Acquired() {
        fut.setLocked(true);
        assertTrue(fut.isDone());
    }

    @Test
    public void testIsDone_Denied() {
        fut.setLocked(false);
        assertTrue(fut.isDone());
    }

    @Test
    public void testIsDone_Waiting() {
        assertFalse(fut.isDone());
    }

    @Test
    public void testIsLocked_Cancelled() {
        fut.cancel(false);
        assertFalse(fut.isLocked());
    }

    @Test
    public void testIsLocked_Acquired() {
        fut.setLocked(true);
        assertTrue(fut.isLocked());
    }

    @Test
    public void testIsLocked_Denied() {
        fut.setLocked(false);
        assertFalse(fut.isLocked());
    }

    @Test
    public void testIsLocked_Waiting() {
        assertFalse(fut.isLocked());
    }

    @Test
    public void testGet_Cancelled() throws Exception {
        new Thread() {
            @Override
            public void run() {
                fut.cancel(false);
            }
        }.start();

        expectException(CancellationException.class, xxx -> fut.get());
    }

    @Test
    public void testGet_Acquired() throws Exception {
        new Thread() {
            @Override
            public void run() {
                fut.setLocked(true);
            }
        }.start();

        assertTrue(fut.get());
    }

    @Test
    public void testGet_Denied() throws Exception {
        new Thread() {
            @Override
            public void run() {
                fut.setLocked(false);
            }
        }.start();

        assertFalse(fut.get());
    }

    @Test
    public void testGetLongTimeUnit() throws Exception {
        expectException(TimeoutException.class, xxx -> fut.get(0, TimeUnit.SECONDS));

        fut.setLocked(true);
        assertTrue(fut.get(0, TimeUnit.SECONDS));
    }

    @Test
    public void testGetLongTimeUnit_Timeout() throws Exception {
        expectException(TimeoutException.class, xxx -> fut.get(0, TimeUnit.SECONDS));
        expectException(TimeoutException.class, xxx -> fut.get(2, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testGetLongTimeUnit_Cancelled() throws Exception {
        new Thread() {
            @Override
            public void run() {
                fut.cancel(false);
            }
        }.start();

        expectException(CancellationException.class, xxx -> fut.get(WAIT_SEC, TimeUnit.SECONDS));
    }

    @Test
    public void testGetLongTimeUnit_Acquired() throws Exception {
        new Thread() {
            @Override
            public void run() {
                fut.setLocked(true);
            }
        }.start();

        assertTrue(fut.get(WAIT_SEC, TimeUnit.SECONDS));
    }

    @Test
    public void testGetLongTimeUnit_Denied() throws Exception {
        new Thread() {
            @Override
            public void run() {
                fut.setLocked(false);
            }
        }.start();

        assertFalse(fut.get(WAIT_SEC, TimeUnit.SECONDS));
    }

    @Test(expected = IllegalStateException.class)
    public void testInvokeCallback() {
        fut.setLocked(true);
        fut.invokeCallback();

        // re-invoke - should throw an exception
        fut.invokeCallback();
    }

    @Test(expected = IllegalStateException.class)
    public void testInvokeCallback_Cancelled() {
        fut.cancel(false);

        // invoke after cancel - should throw an exception
        fut.invokeCallback();
    }

    @Test
    public void testInvokeCallback_Acquired() {
        fut.setLocked(true);
        fut.invokeCallback();

        verify(callback).set(true);
        verify(callback, never()).set(false);
    }

    @Test
    public void testInvokeCallback_Denied() {
        fut.setLocked(false);
        fut.invokeCallback();

        verify(callback).set(false);
        verify(callback, never()).set(true);
    }

    @Test
    public void testInvokeCallback_Ex() {
        doThrow(new RuntimeException(EXPECTED_EXCEPTION)).when(callback).set(anyBoolean());

        fut.setLocked(false);
        fut.invokeCallback();
    }

    @Test
    public void testMakeNullArgException() {
        IllegalArgumentException ex = LockRequestFuture.makeNullArgException(EXPECTED_EXCEPTION);
        assertEquals(EXPECTED_EXCEPTION, ex.getMessage());
    }
}
