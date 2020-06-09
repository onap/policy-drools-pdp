/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018, 2020 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.pooling.message;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.SortedSet;
import java.util.TreeSet;
import org.junit.Test;
import org.onap.policy.drools.pooling.PoolingFeatureException;

public class BucketAssignmentsTest {

    @Test
    public void testBucketAssignments() {
        assertThatCode(() -> new BucketAssignments()).doesNotThrowAnyException();
    }

    @Test
    public void testBucketAssignmentsStringArray() {
        String[] arr = {"abc", "def"};
        BucketAssignments asgn = new BucketAssignments(arr);

        assertNotNull(asgn.getHostArray());
        assertEquals(arr.toString(), asgn.getHostArray().toString());
    }

    @Test
    public void testGetHostArray_testSetHostArray() {

        String[] arr = {"abc", "def"};
        BucketAssignments asgn = new BucketAssignments(arr);

        assertNotNull(asgn.getHostArray());
        assertEquals(arr.toString(), asgn.getHostArray().toString());

        String[] arr2 = {"xyz"};
        asgn.setHostArray(arr2);

        assertNotNull(asgn.getHostArray());
        assertEquals(arr2.toString(), asgn.getHostArray().toString());
    }

    @Test
    public void testGetLeader() {
        // host array is null
        BucketAssignments asgn = new BucketAssignments();
        assertNull(asgn.getLeader());

        // array is non-null, but empty
        asgn.setHostArray(new String[0]);
        assertNull(asgn.getLeader());

        // all entries are null
        asgn.setHostArray(new String[5]);
        assertNull(asgn.getLeader());

        // some entries are null
        asgn.setHostArray(new String[] {null, "abc", null});
        assertEquals("abc", asgn.getLeader());

        // only one entry
        asgn.setHostArray(new String[] {"abc"});
        assertEquals("abc", asgn.getLeader());

        // first is least
        asgn.setHostArray(new String[] {"Ahost", "Bhost", "Chost"});
        assertEquals("Ahost", asgn.getLeader());

        // middle is least
        asgn.setHostArray(new String[] {"Xhost", "Bhost", "Chost"});
        assertEquals("Bhost", asgn.getLeader());

        // last is least
        asgn.setHostArray(new String[] {"Xhost", "Yhost", "Chost"});
        assertEquals("Chost", asgn.getLeader());

        // multiple entries
        asgn.setHostArray(new String[] {"Xhost", "Bhost", "Chost", "Bhost", "Xhost", "Chost"});
        assertEquals("Bhost", asgn.getLeader());
    }

    @Test
    public void testHasAssignment() {
        // host array is null
        BucketAssignments asgn = new BucketAssignments();
        assertFalse(asgn.hasAssignment("abc"));

        // array is non-null, but empty
        asgn.setHostArray(new String[0]);
        assertFalse(asgn.hasAssignment("abc"));

        // all entries are null
        asgn.setHostArray(new String[5]);
        assertFalse(asgn.hasAssignment("abc"));

        // some entries are null
        asgn.setHostArray(new String[] {null, "abc", null});
        assertTrue(asgn.hasAssignment("abc"));

        // only one entry
        asgn.setHostArray(new String[] {"abc"});
        assertTrue(asgn.hasAssignment("abc"));

        // appears as first entry
        asgn.setHostArray(new String[] {"abc", "Bhost", "Chost"});
        assertTrue(asgn.hasAssignment("abc"));

        // appears in middle
        asgn.setHostArray(new String[] {"Xhost", "abc", "Chost"});
        assertTrue(asgn.hasAssignment("abc"));

        // appears last
        asgn.setHostArray(new String[] {"Xhost", "Yhost", "abc"});
        assertTrue(asgn.hasAssignment("abc"));

        // appears repeatedly
        asgn.setHostArray(new String[] {"Xhost", "Bhost", "Chost", "Bhost", "Xhost", "Chost"});
        assertTrue(asgn.hasAssignment("Bhost"));
    }

    @Test
    public void testGetAllHosts() {
        // host array is null
        BucketAssignments asgn = new BucketAssignments();
        assertEquals("[]", getSortedHosts(asgn).toString());

        // array is non-null, but empty
        asgn.setHostArray(new String[0]);
        assertEquals("[]", getSortedHosts(asgn).toString());

        // all entries are null
        asgn.setHostArray(new String[5]);
        assertEquals("[]", getSortedHosts(asgn).toString());

        // some entries are null
        asgn.setHostArray(new String[] {null, "abc", null});
        assertEquals("[abc]", getSortedHosts(asgn).toString());

        // only one entry
        asgn.setHostArray(new String[] {"abc"});
        assertEquals("[abc]", getSortedHosts(asgn).toString());

        // multiple, repeated entries
        asgn.setHostArray(new String[] {"def", "abc", "def", "ghi", "def", "def", "xyz"});
        assertEquals("[abc, def, ghi, xyz]", getSortedHosts(asgn).toString());
    }

    /**
     * Gets the hosts, sorted, so that the order is predictable.
     *
     * @param asgn assignment whose hosts are to be retrieved
     * @return a new, sorted set of hosts
     */
    private SortedSet<String> getSortedHosts(BucketAssignments asgn) {
        return new TreeSet<>(asgn.getAllHosts());
    }

    @Test
    public void testGetAssignedHost() {
        // host array is null
        BucketAssignments asgn = new BucketAssignments();
        assertNull(asgn.getAssignedHost(3));

        // array is non-null, but empty
        asgn.setHostArray(new String[0]);
        assertNull(asgn.getAssignedHost(3));

        // all entries are null
        asgn.setHostArray(new String[5]);
        assertNull(asgn.getAssignedHost(3));

        // multiple, repeated entries
        String[] arr = {"def", "abc", "def", "ghi", "def", "def", "xyz"};
        asgn.setHostArray(arr);

        /*
         * get assignments for consecutive integers, including negative numbers and
         * numbers extending past the length of the array.
         *
         */
        TreeSet<String> seen = new TreeSet<>();
        for (int x = -1; x < arr.length + 2; ++x) {
            seen.add(asgn.getAssignedHost(x));
        }

        TreeSet<String> expected = new TreeSet<>(Arrays.asList(arr));
        assertEquals(expected, seen);

        // try a much bigger number
        assertNotNull(asgn.getAssignedHost(arr.length * 1000));
    }

    @Test
    public void testSize() {
        // host array is null
        BucketAssignments asgn = new BucketAssignments();
        assertEquals(0, asgn.size());

        // array is non-null, but empty
        asgn.setHostArray(new String[0]);
        assertEquals(0, asgn.size());

        // all entries are null
        asgn.setHostArray(new String[5]);
        assertEquals(5, asgn.size());

        // multiple, repeated entries
        String[] arr = {"def", "abc", "def", "ghi", "def", "def", "xyz"};
        asgn.setHostArray(arr);
        assertEquals(arr.length, asgn.size());
    }

    @Test
    public void testCheckValidity() throws Exception {
        // host array is null
        BucketAssignments asgn = new BucketAssignments();
        expectException(asgn);

        // array is non-null, but empty
        asgn.setHostArray(new String[0]);
        expectException(asgn);

        // array is too big
        asgn.setHostArray(new String[BucketAssignments.MAX_BUCKETS + 1]);
        expectException(asgn);

        // all entries are null
        asgn.setHostArray(new String[5]);
        expectException(asgn);

        // null at the beginning
        asgn.setHostArray(new String[] {null, "Bhost", "Chost"});
        expectException(asgn);

        // null in the middle
        asgn.setHostArray(new String[] {"Ahost", null, "Chost"});
        expectException(asgn);

        // null at the end
        asgn.setHostArray(new String[] {"Ahost", "Bhost", null});
        expectException(asgn);

        // only one entry
        asgn.setHostArray(new String[] {"abc"});
        asgn.checkValidity();

        // multiple entries
        asgn.setHostArray(new String[] {"Ahost", "Bhost", "Chost"});
        asgn.checkValidity();
    }

    @Test
    public void testHashCode() {
        // with null assignments
        BucketAssignments asgn = new BucketAssignments();
        asgn.hashCode();

        // with empty array
        asgn = new BucketAssignments(new String[0]);
        asgn.hashCode();

        // with null items
        asgn = new BucketAssignments(new String[] {"abc", null, "def"});
        asgn.hashCode();

        // same assignments
        asgn = new BucketAssignments(new String[] {"abc", null, "def"});
        int code = asgn.hashCode();

        asgn = new BucketAssignments(new String[] {"abc", null, "def"});
        assertEquals(code, asgn.hashCode());

        // slightly different values (i.e., changed "def" to "eef")
        asgn = new BucketAssignments(new String[] {"abc", null, "eef"});
        assertNotEquals(code, asgn.hashCode());
    }

    @Test
    public void testEquals() {
        // null object
        BucketAssignments asgn = new BucketAssignments();
        assertNotEquals(asgn, null);

        // same object
        asgn = new BucketAssignments();
        assertEquals(asgn, asgn);

        // different class of object
        asgn = new BucketAssignments();
        assertNotEquals(asgn, "not an assignment object");

        assertNotEquals(asgn, new BucketAssignments(new String[] {"abc"}));

        // with null assignments
        asgn = new BucketAssignments();
        assertEquals(asgn, new BucketAssignments());

        // with empty array
        asgn = new BucketAssignments(new String[0]);
        assertEquals(asgn, asgn);

        assertNotEquals(asgn, new BucketAssignments());
        assertNotEquals(asgn, new BucketAssignments(new String[] {"abc"}));

        // with null items
        String[] arr = {"abc", null, "def"};
        asgn = new BucketAssignments(arr);
        assertEquals(asgn, asgn);
        assertEquals(asgn, new BucketAssignments(arr));
        assertEquals(asgn, new BucketAssignments(new String[] {"abc", null, "def"}));

        assertNotEquals(asgn, new BucketAssignments());
        assertNotEquals(asgn, new BucketAssignments(new String[] {"abc", null, "XYZ"}));

        assertNotEquals(asgn, new BucketAssignments());
    }

    /**
     * Expects an exception when checkValidity() is called.
     *
     * @param asgn assignments to be checked
     */
    private void expectException(BucketAssignments asgn) {
        try {
            asgn.checkValidity();
            fail("missing exception");

        } catch (PoolingFeatureException expected) {
            // success
        }
    }

}
