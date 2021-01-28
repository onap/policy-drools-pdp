/*-
 * ============LICENSE_START=======================================================
 * ONAP
 * ================================================================================
 * Copyright (C) 2017-2018, 2021 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.utils.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.AbstractMatcherFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.Marker;

/**
 * Logger Marker Filters to be used in logback.xml configuration
 * to accept/deny metric or transaction (audit) events
 */
public abstract class LoggerMarkerFilter extends AbstractMatcherFilter<ILoggingEvent> {

    protected final Marker marker;

    protected LoggerMarkerFilter(Marker marker) {
        this.marker = marker;
    }

    @Override
    public FilterReply decide(ILoggingEvent event) {

        if (this.marker == null || !isStarted()) {
            return FilterReply.DENY;
        }

        if (event == null || event.getMarker() == null) {
            return FilterReply.DENY;
        }

        if (event.getMarker().equals(marker)) {
            return FilterReply.ACCEPT;
        } else {
            return FilterReply.DENY;
        }
    }

    /**
     * Metric Logger Marker Filter.
     */
    public static class MetricLoggerMarkerFilter extends LoggerMarkerFilter {

        public MetricLoggerMarkerFilter() {
            super(LoggerUtil.METRIC_LOG_MARKER);
        }

    }

    /**
     * Transaction Logger Marker Filter.
     */
    public static class TransactionLoggerMarkerFilter extends LoggerMarkerFilter {

        public TransactionLoggerMarkerFilter() {
            super(LoggerUtil.TRANSACTION_LOG_MARKER);
        }
    }
}
