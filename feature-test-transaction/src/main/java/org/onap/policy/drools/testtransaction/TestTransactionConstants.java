/*-
 * ============LICENSE_START=======================================================
 * feature-test-transaction
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

package org.onap.policy.drools.testtransaction;

import lombok.Getter;

public class TestTransactionConstants {

    public static final String TT_FPC = "TT.FPC";
    public static final String TT_COUNTER = "$ttc";
    public static final String TT_UUID = "43868e59-d1f3-43c2-bd6f-86f89a61eea5";
    public static final long DEFAULT_TT_TASK_SLEEP = 20000;

    @Getter
    private static final TestTransaction manager = new TtImpl();

    private TestTransactionConstants() {
        // do nothing
    }
}
