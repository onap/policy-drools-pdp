/*-
 * ============LICENSE_START=======================================================
 * feature-session-persistence
 * ================================================================================
 * Copyright (C) 2017-2018, 2020 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.persistence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.Date;
import org.junit.Test;

public class DroolsSessionEntityTest {

    @Test
    public void testHashCode() {
        DroolsSessionEntity entity = makeEnt("mynameA", 1);

        DroolsSessionEntity e2 = makeEnt("mynameA", 2);

        // session id is not part of hash code
        assertEquals(entity.hashCode(), e2.hashCode());

        // diff sess name
        e2 = makeEnt("mynameB", 1);
        assertNotEquals(entity.hashCode(), e2.hashCode());
    }

    /** Ensures that hashCode() functions as expected when the getXxx methods are overridden. */
    @Test
    public void testHashCode_Subclass() {
        DroolsSessionEntity entity = makeEnt2("mynameA", 1);

        DroolsSessionEntity e2 = makeEnt("mynameA", 2);

        // session id is not part of hash code
        assertEquals(entity.hashCode(), e2.hashCode());

        // diff sess name
        e2 = makeEnt("mynameB", 1);
        assertNotEquals(entity.hashCode(), e2.hashCode());
    }

    @Test
    public void testGetSessionName_testSetSessionName() {
        DroolsSessionEntity entity = makeEnt("mynameZ", 1);

        assertEquals("mynameZ", entity.getSessionName());

        entity.setSessionName("another");
        assertEquals("another", entity.getSessionName());

        // others unchanged
        assertEquals(1, entity.getSessionId());
    }

    @Test
    public void testGetSessionId_testSetSessionId() {
        DroolsSessionEntity entity = makeEnt("mynameA", 1);

        assertEquals(1, entity.getSessionId());

        entity.setSessionId(20);
        assertEquals(20, entity.getSessionId());

        // others unchanged
        assertEquals("mynameA", entity.getSessionName());
    }

    @Test
    public void testGetCreatedDate_testSetCreatedDate_testGetUpdatedDate_testSetUpdatedDate() {
        DroolsSessionEntity entity = new DroolsSessionEntity();

        Date crtdt = new Date(System.currentTimeMillis() - 100);
        entity.setCreatedDate(crtdt);

        Date updt = new Date(System.currentTimeMillis() - 200);
        entity.setUpdatedDate(updt);

        assertEquals(crtdt, entity.getCreatedDate());
        assertEquals(updt, entity.getUpdatedDate());
    }

    @Test
    public void testEqualsObject() {
        DroolsSessionEntity entity = makeEnt("mynameA", 1);

        // diff object type
        assertNotEquals(entity, "hello");

        // reflexive
        assertEquals(entity, entity);

        DroolsSessionEntity e2 = makeEnt("mynameA", 2);

        // session id is not part of hash code
        assertEquals(entity, e2);
        assertEquals(entity, e2);

        // diff sess name
        e2 = makeEnt("mynameB", 1);
        assertNotEquals(entity, e2);
        assertNotEquals(entity, e2);
    }

    /** Ensures that equals() functions as expected when the getXxx methods are overridden. */
    @Test
    public void testEqualsObject_Subclass() {
        DroolsSessionEntity entity = makeEnt2("mynameA", 1);

        // reflexive
        assertEquals(entity, entity);

        DroolsSessionEntity e2 = makeEnt("mynameA", 2);

        // session id is not part of hash code
        assertEquals(entity, e2);
        assertEquals(entity, e2);

        // diff sess name
        e2 = makeEnt("mynameB", 1);
        assertNotEquals(entity, e2);
        assertNotEquals(entity, e2);
    }

    @Test
    public void testToString() {
        DroolsSessionEntity entity = makeEnt("mynameA", 23);

        assertEquals("{name=mynameA, id=23}", entity.toString());
    }

    /**
     * Makes a session Entity. The parameters are stored into the Entity object via the setXxx
     * methods.
     *
     * @param sessnm session name
     * @param sessid session id
     * @return a new session Entity
     */
    private DroolsSessionEntity makeEnt(String sessnm, long sessid) {

        DroolsSessionEntity entity = new DroolsSessionEntity();

        entity.setSessionName(sessnm);
        entity.setSessionId(sessid);

        return entity;
    }

    /**
     * Makes a session Entity that overrides the getXxx methods. The parameters that are provided are
     * returned by the overridden methods, but they are <i>not</i> stored into the Entity object via
     * the setXxx methods.
     *
     * @param sessnm session name
     * @param sessid session id
     * @return a new session Entity
     */
    @SuppressWarnings("serial")
    private DroolsSessionEntity makeEnt2(String sessnm, long sessid) {

        return new DroolsSessionEntity() {

            @Override
            public String getSessionName() {
                return sessnm;
            }

            @Override
            public long getSessionId() {
                return sessid;
            }
        };
    }
}
