/*-
 * ============LICENSE_START=======================================================
 * policy-utils
 * ================================================================================
 * Copyright (C) 2019-2021 AT&T Intellectual Property. All rights reserved.
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
 */

package org.onap.policy.drools.utils.logging;

public class MdcTransactionConstants {
    /*
     * The fields must match the naming given at
     * https://wiki.onap.org/pages/viewpage.action?pageId=20087036
     */

    /**
     * End to end transaction ID. Subtransactions will inherit this value from the transaction.
     */
    public static final String REQUEST_ID = "RequestID";

    /**
     * Invocation ID, ie. SubTransaction ID.
     */
    public static final String INVOCATION_ID = "InvocationID";

    /**
     * Service Name. Both transactions and subtransactions will have its own copy.
     */
    public static final String SERVICE_NAME = "ServiceName";

    /**
     * Partner Name Subtransactions will inherit this value from the transaction.
     */
    public static final String PARTNER_NAME = "PartnerName";

    /**
     * Start Timestamp. Both transactions and subtransactions will have its own copy.
     */
    public static final String BEGIN_TIMESTAMP = "BeginTimestamp";

    /**
     * End Timestamp. Both transactions and subtransactions will have its own copy.
     */
    public static final String END_TIMESTAMP = "EndTimestamp";

    /**
     * Elapsed Time. Both transactions and subtransactions will have its own copy.
     */
    public static final String ELAPSED_TIME = "ElapsedTime";

    /**
     * Elapsed Time. Both transactions and subtransactions will have its own copy.
     */
    public static final String SERVICE_INSTANCE_ID = "ServiceInstanceID";

    /**
     * Virtual Server Name. Subtransactions will inherit this value from the transaction.
     */
    public static final String VIRTUAL_SERVER_NAME = "VirtualServerName";

    /**
     * Status Code Both transactions and subtransactions will have its own copy.
     */
    public static final String STATUS_CODE = "StatusCode";

    /**
     * Response Code Both transactions and subtransactions will have its own copy.
     */
    public static final String RESPONSE_CODE = "ResponseCode";

    /**
     * Response Description Both transactions and subtransactions will have its own copy.
     */
    public static final String RESPONSE_DESCRIPTION = "ResponseDescription";

    /**
     * Instance UUID Both transactions and subtransactions will have its own copy.
     */
    public static final String INSTANCE_UUID = "InstanceUUID";

    /**
     * Severity Both transactions and subtransactions will have its own copy.
     */
    public static final String SEVERITY = "Severity";

    /**
     * Target Entity Both transactions and subtransactions will have its own copy.
     */
    public static final String TARGET_ENTITY = "TargetEntity";

    /**
     * Target Service Name Both transactions and subtransactions will have its own copy.
     */
    public static final String TARGET_SERVICE_NAME = "TargetServiceName";

    /**
     * Server Subtransactions inherit this value. if (this.getSources().size() == 1)
     * this.getSources().get(0).getTopic();
     */
    public static final String SERVER = "Server";

    /**
     * Server IP Address Subtransactions inherit this value.
     */
    public static final String SERVER_IP_ADDRESS = "ServerIpAddress";

    /**
     * Server FQDN Subtransactions inherit this value.
     */
    public static final String SERVER_FQDN = "ServerFQDN";

    /**
     * Client IP Address Both transactions and subtransactions will have its own copy.
     */
    public static final String CLIENT_IP_ADDRESS = "ClientIPAddress";

    /**
     * Process Key Both transactions and subtransactions will have its own copy.
     */
    public static final String PROCESS_KEY = "ProcessKey";

    /**
     * Remote Host Both transactions and subtransactions will have its own copy.
     */
    public static final String REMOTE_HOST = "RemoteHost";

    /**
     * Target Virtual Entity Both transactions and subtransactions will have its own copy.
     */
    public static final String TARGET_VIRTUAL_ENTITY = "TargetVirtualEntity";

    /**
     * Custom Field1.
     */
    public static final String CUSTOM_FIELD1 = "CustomField1";

    /**
     * Custom Field2.
     */
    public static final String CUSTOM_FIELD2 = "CustomField2";

    /**
     * Custom Field3.
     */
    public static final String CUSTOM_FIELD3 = "CustomField3";

    /**
     * Custom Field4.
     */
    public static final String CUSTOM_FIELD4 = "CustomField4";

    /**
     * Status Code Complete.
     */
    public static final String STATUS_CODE_COMPLETE = "COMPLETE";

    /**
     * Status Code Error.
     */
    public static final String STATUS_CODE_FAILURE = "ERROR";

    private MdcTransactionConstants() {
        // do nothing
    }
}
