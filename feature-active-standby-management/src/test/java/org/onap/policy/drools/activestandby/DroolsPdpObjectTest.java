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

package org.onap.policy.drools.activestandby;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.Date;
import lombok.Getter;
import lombok.Setter;
import org.junit.Before;
import org.junit.Test;

public class DroolsPdpObjectTest {
    private static final String PDP_ID = "my-id";
    private static final String PDP_ID2 = "my-id2";
    private static final String SITE = "my-site";
    private static final String SITE2 = "my-site2";
    private static final int PRIORITY = 11;
    private static final int PRIORITY2 = 12;

    private MyPdp pdp;

    @Before
    public void setUp() {
        pdp = makePdp(PDP_ID, SITE, PRIORITY);
    }

    @Test
    public void testEqualsObject() {
        // self
        assertEquals(pdp, pdp);

        // same id
        MyPdp pdp2 = new MyPdp();
        pdp2.setPdpId(PDP_ID);
        assertEquals(pdp, pdp2);

        // different id
        pdp2.setPdpId(PDP_ID2);
        assertNotEquals(pdp, pdp2);

        // different type of object
        assertNotEquals(pdp, "");
    }

    @Test
    public void testHashCode() {
        int hc = pdp.hashCode();

        // same data should yield same hash code
        assertEquals(hc, pdp.hashCode());
        assertEquals(hc, makePdp(PDP_ID, SITE, PRIORITY).hashCode());

        // different data should yield different hash code
        assertNotEquals(hc, makePdp(PDP_ID2, SITE, PRIORITY).hashCode());

        // these fields have no impact on hash code
        assertEquals(hc, makePdp(PDP_ID, SITE, PRIORITY2).hashCode());
        assertEquals(hc, makePdp(PDP_ID, SITE2, PRIORITY).hashCode());

        // should not throw an exception
        new MyPdp().hashCode();
    }

    @Test
    public void testNullSafeCompare() {
        // self, when null
        pdp.setSite(null);
        assertEquals(0, pdp.comparePriority(pdp));

        // both null
        MyPdp pdp2 = makePdp(PDP_ID, null, PRIORITY);
        assertEquals(0, pdp.comparePriority(pdp2));

        // left null
        pdp2 = makePdp(PDP_ID, SITE, PRIORITY);
        assertEquals(-1, pdp.comparePriority(pdp2));

        // right null - note: args are reversed
        pdp2 = makePdp(PDP_ID, SITE, PRIORITY);
        assertEquals(1, pdp2.comparePriority(pdp));
    }

    @Test
    public void testComparePriorityDroolsPdp() {
        // self
        assertEquals(0, pdp.comparePriority(pdp));

        // same
        MyPdp pdp2 = makePdp(PDP_ID, SITE, PRIORITY);
        assertEquals(0, pdp.comparePriority(pdp2));

        // different site
        pdp2 = makePdp(PDP_ID, SITE2, PRIORITY);
        assertEquals(SITE.compareTo(SITE2), pdp.comparePriority(pdp2));

        // different priority
        pdp2 = makePdp(PDP_ID, SITE, PRIORITY2);
        assertEquals(PRIORITY - PRIORITY2, pdp.comparePriority(pdp2));

        // different id
        pdp2 = makePdp(PDP_ID2, SITE, PRIORITY);
        assertEquals(PDP_ID.compareTo(PDP_ID2), pdp.comparePriority(pdp2));
    }

    @Test
    public void testComparePriorityDroolsPdpString() {
        final int result = 1000;

        // override other comparison method so we know if it's called
        MyPdp pdp2 = new MyPdp() {
            @Override
            public int comparePriority(DroolsPdp other) {
                return result;
            }
        };

        pdp2.setPdpId(PDP_ID);
        pdp2.setSite(SITE2);
        pdp2.setPriority(PRIORITY);

        // should use overridden comparison method
        assertEquals(result, pdp2.comparePriority(pdp, null));
        assertEquals(result, pdp2.comparePriority(pdp, ""));

        // should use normal comparison method
        assertEquals(SITE2.compareTo(SITE), pdp2.comparePriority(pdp, SITE));
    }

    private MyPdp makePdp(String id, String site, int priority) {
        MyPdp pdp2 = new MyPdp();

        pdp2.setSite(site);
        pdp2.setPdpId(id);
        pdp2.setPriority(priority);

        return pdp2;
    }

    @Getter
    @Setter
    private class MyPdp extends DroolsPdpObject {
        private String pdpId;
        private boolean designated;
        private int priority;
        private Date updatedDate;
        private String site;
        private Date designatedDate;
    }
}
