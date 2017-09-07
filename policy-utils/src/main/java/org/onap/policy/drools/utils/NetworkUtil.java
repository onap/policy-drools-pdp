/*-
 * ============LICENSE_START=======================================================
 * ONAP
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

package org.onap.policy.drools.utils;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Network Utilities
 */
public class NetworkUtil {

  public static final Logger logger = LoggerFactory.getLogger(NetworkUtil.class.getName());

  /**
   * IPv4 Wildcard IP address
   */
  public static final String IPv4_WILDCARD_ADDRESS = "0.0.0.0";


  /**
   * try to connect to $host:$port $retries times while we are getting connection failures.
   *
   * @param host host
   * @param port port
   * @param retries number of attempts
   * @return true is port is open, false otherwise
   * @throws InterruptedException if execution has been interrupted
   */
  public static boolean isTcpPortOpen(String host, int port, int retries, long interval)
      throws InterruptedException, IOException {
    int retry = 0;
    while (retry < retries) {
      try (Socket s = new Socket(host, port)) {
        logger.debug("{}:{} connected - retries={} interval={}", host, port, retries, interval);
        return true;
      } catch (final ConnectException e) {
        retry++;
        logger.trace("{}:{} connected - retries={} interval={}", host, port, retries, interval, e);
        Thread.sleep(interval);
      }
    }

    logger.warn("{}:{} closed = retries={} interval={}", host, port, retries, interval);
    return false;
  }

}
