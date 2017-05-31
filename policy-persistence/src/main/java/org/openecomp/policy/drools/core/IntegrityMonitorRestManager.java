/*-
 * ============LICENSE_START=======================================================
 * policy-persistence
 * ================================================================================
 * Copyright (C) 2017 AT&T Intellectual Property. All rights reserved.
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

package org.openecomp.policy.drools.core;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import org.openecomp.policy.common.logging.eelf.MessageCodes;
import org.openecomp.policy.common.logging.flexlogger.FlexLogger;
import org.openecomp.policy.common.logging.flexlogger.Logger;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

@Api(value = "test")
	@Path("/")
public class IntegrityMonitorRestManager {
		private static Logger logger = FlexLogger.getLogger(IntegrityMonitorRestManager.class);	
		private DroolsPDPIntegrityMonitor im;
		
		/**
		 * Test interface for Integrity Monitor
		 * 
		 * @return Exception message if exception, otherwise empty
		 */
		@ApiOperation(
			value = "Test endpoint for integrity monitor",
			notes = "The TEST command is used to request data from a subcomponent "
					+ "instance that can be used to determine its operational state. "
					+ "A 200/success response status code should be returned if the "
					+ "subcomponent instance is functioning properly and able to respond to requests.",
			response = String.class)
		@ApiResponses(value = {
			@ApiResponse(
				code = 200,
				message = "Integrity monitor sanity check passed"),
			@ApiResponse(
				code = 500,
				message = "Integrity monitor sanity check encountered an exception. This can indicate operational state disabled or administrative state locked")
		})
		@GET
		@Path("test")
		public Response test() {
			logger.error("integrity monitor /test accessed");
			// The responses are stored within the audit objects, so we need to
			// invoke the audits and get responses before we handle another
			// request.
			synchronized (IntegrityMonitorRestManager.class) {
				// will include messages associated with subsystem failures
				StringBuilder body = new StringBuilder();

				// 200=SUCCESS, 500=failure
				int responseValue = 200;

				if (im == null) {
					try {
						im = DroolsPDPIntegrityMonitor.getInstance();
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						
						body.append("\nException: " + e + "\n");
						responseValue = 500;
					}
				}

				if (im != null) {
					try {
						// call 'IntegrityMonitor.evaluateSanity()'
						im.evaluateSanity();
					} catch (Exception e) {
						// this exception isn't coming from one of the audits,
						// because those are caught in 'subsystemTest()'
						logger.error(MessageCodes.EXCEPTION_ERROR, e, "DroolsPDPIntegrityMonitor.evaluateSanity()");

						// include exception in HTTP response
						body.append("\nException: " + e + "\n");
						responseValue = 500;
					}
				}

				// send response, including the contents of 'body'
				// (which is empty if everything is successful)
				if (responseValue == 200)
					return Response.status(Response.Status.OK).build();
				else
					return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(body.toString()).build();
			}
		}
}
