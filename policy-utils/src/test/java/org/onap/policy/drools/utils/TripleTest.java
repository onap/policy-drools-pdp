/*
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2018 AT&T Intellectual Property. All rights reserved.
 * Modifications Copyright (C) 2020 Nordix Foundation
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

import org.junit.Assert;
import org.junit.Test;

public class TripleTest {

    @Test
    public void test() {
        Triple<String, String, String> triple  =
                new Triple<>("one", "two", "three");

        Assert.assertTrue("one".equals(triple.first()));
        Assert.assertTrue("one".equals(triple.getFirst()));

        Assert.assertTrue("two".equals(triple.second()));
        Assert.assertTrue("two".equals(triple.getSecond()));

        Assert.assertTrue("three".equals(triple.third()));
        Assert.assertTrue("three".equals(triple.getThird()));

        triple.first("I");
        Assert.assertTrue("I".equals(triple.first()));

        triple.setFirst("1");
        Assert.assertTrue("1".equals(triple.first()));

        triple.second("2");
        Assert.assertTrue("2".equals(triple.second()));

        triple.setSecond("II");
        Assert.assertTrue("II".equals(triple.second()));

        triple.third("3");
        Assert.assertTrue("3".equals(triple.third()));

        triple.setThird("III");
        Assert.assertTrue("III".equals(triple.third()));

    }
}