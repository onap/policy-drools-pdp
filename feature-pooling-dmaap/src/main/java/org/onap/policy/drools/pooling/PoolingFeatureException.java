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

package org.onap.policy.drools.pooling;

/**
 * Exception thrown by the pooling feature.
 */
public class PoolingFeatureException extends Exception {
    private static final long serialVersionUID = 1L;

    public PoolingFeatureException() {
        super();
    }

    public PoolingFeatureException(String message) {
        super(message);
    }

    public PoolingFeatureException(Throwable cause) {
        super(cause);
    }

    public PoolingFeatureException(String message, Throwable cause) {
        super(message, cause);
    }

    public PoolingFeatureException(String message, Throwable cause, boolean enableSuppression,
                    boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    /**
     * Converts the exception to a runtime exception.
     * 
     * @return a new runtime exception, wrapping this exception
     */
    public PoolingFeatureRtException toRuntimeException() {
        return new PoolingFeatureRtException(this);
    }

}
