/*-
 * ============LICENSE_START=======================================================
 * feature-simulators
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

package org.onap.policy.drools.simulators;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.BlockingQueue;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/events")
public class DMaaPSimulatorJaxRs {
	
	private static final Map<String, BlockingQueue<String>> queues = new ConcurrentHashMap<>();
	private static final Logger logger = LoggerFactory.getLogger(DMaaPSimulatorJaxRs.class);
	private static int responseCode = 200;
	
	@GET
	@Path("/{topicName}/{consumeGroup}/{consumerId}")
	public String subscribe(@DefaultValue("0") @QueryParam("timeout") int timeout, @PathParam("topicName") String topicName,
	        @Context final HttpServletResponse httpResponse) {
	    int currentRespCode = responseCode;
        httpResponse.setStatus(currentRespCode);
        try {
            httpResponse.flushBuffer();
        } catch (IOException e) {
            final Logger logger = LoggerFactory.getLogger(DMaaPSimulatorJaxRs.class);
            logger.error("flushBuffer threw: ", e);
            return "Got an error";
        }
        
	    if (currentRespCode < 200 || currentRespCode >= 300)
	    {
	        return "You got response code: " + currentRespCode;
	    }
		if (queues.containsKey(topicName)) {
			BlockingQueue<String> queue = queues.get(topicName);
			String response = "No Data";
			try {
				response = queue.poll(timeout, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				logger.debug("error in DMaaP simulator", e);
			}
			if (response == null) {
			    response = "No Data";
			}
			return response;
		}
		else if (timeout > 0) {
			try {
				Thread.sleep(timeout);
				if (queues.containsKey(topicName)) {
					BlockingQueue<String> queue = queues.get(topicName);
					String response = queue.poll();
					if (response == null) {
					    response = "No Data";
					}
					return response;
				}
			} catch (InterruptedException e) {
				logger.debug("error in DMaaP simulator", e);
			}
		}
		return "No topic";
	}
	
	@POST
	@Path("/{topicName}")
	@Consumes(MediaType.TEXT_PLAIN)
	public String publish(@PathParam("topicName") String topicName, String body) { 
		if (queues.containsKey(topicName)) {
			BlockingQueue<String> queue = queues.get(topicName);
			queue.offer(body);
		}
		else {
			BlockingQueue<String> queue = new LinkedBlockingQueue<>();
			queue.offer(body);
			queues.put(topicName, queue);
		}
		
		return "";
	}
	
	@POST
	@Path("/setStatus")
	public String setStatus(@QueryParam("statusCode") int statusCode) {
	    responseCode = statusCode;
	    return "Status code set";
	}
}
