/*
 * ============LICENSE_START=======================================================
 * policy-endpoints
 * ================================================================================
 * Copyright (C) 2017-2018 AT&T Intellectual Property. All rights reserved.
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

package org.onap.policy.drools.event.comm.bus.internal;

import java.io.IOException;
import java.net.MalformedURLException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.onap.policy.drools.event.comm.bus.DmaapTopicSinkFactory;
import org.onap.policy.drools.properties.PolicyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.att.nsa.cambria.client.CambriaClientBuilders;
import com.att.nsa.cambria.client.CambriaClientBuilders.ConsumerBuilder;
import com.att.nsa.cambria.client.CambriaConsumer;
import com.att.nsa.mr.client.MRClientFactory;
import com.att.nsa.mr.client.impl.MRConsumerImpl;
import com.att.nsa.mr.client.response.MRConsumerResponse;
import com.att.nsa.mr.test.clients.ProtocolTypeConstants;

/**
 * Wrapper around libraries to consume from message bus
 *
 */
public interface BusConsumer {

  /**
   * fetch messages
   *
   * @return list of messages
   * @throws Exception when error encountered by underlying libraries
   */
  public Iterable<String> fetch() throws InterruptedException, IOException;

  /**
   * close underlying library consumer
   */
  public void close();

  /**
   * BusConsumer that supports server-side filtering.
   */
  public interface FilterableBusConsumer extends BusConsumer {

        /**
         * Sets the server-side filter.
         * 
         * @param filter new filter value, or {@code null}
         * @throws IllegalArgumentException if the consumer cannot be built with
         *         the new filter
         */
        public void setFilter(String filter);
  }

  /**
   * Cambria based consumer
   */
  public static class CambriaConsumerWrapper implements FilterableBusConsumer {

    /**
     * logger
     */
    private static Logger logger = LoggerFactory.getLogger(CambriaConsumerWrapper.class);
    
    /**
     * Used to build the consumer.
     */
    private final ConsumerBuilder builder;
    
    /**
     * Locked while updating {@link #consumer} and {@link #newConsumer}.
     */
    private final Object consLocker = new Object();

    /**
     * Cambria client
     */
    private CambriaConsumer consumer;

    /**
     * Cambria client to use for next fetch
     */
    private CambriaConsumer newConsumer = null;

    /**
     * fetch timeout
     */
    protected int fetchTimeout;

    /**
     * close condition
     */
    protected Object closeCondition = new Object();

    /**
     * Cambria Consumer Wrapper
     *
     * @param servers messaging bus hosts
     * @param topic topic
     * @param apiKey API Key
     * @param apiSecret API Secret
     * @param consumerGroup Consumer Group
     * @param consumerInstance Consumer Instance
     * @param fetchTimeout Fetch Timeout
     * @param fetchLimit Fetch Limit
     * @throws GeneralSecurityException
     * @throws MalformedURLException
     */
    public CambriaConsumerWrapper(List<String> servers, String topic, String apiKey,
        String apiSecret, String consumerGroup, String consumerInstance, int fetchTimeout,
        int fetchLimit, boolean useHttps, boolean useSelfSignedCerts) {
      this(servers, topic, apiKey, apiSecret, null, null,
           consumerGroup, consumerInstance, fetchTimeout, fetchLimit,
           useHttps, useSelfSignedCerts);
    }

    public CambriaConsumerWrapper(List<String> servers, String topic, String apiKey,
        String apiSecret, String username, String password,
        String consumerGroup, String consumerInstance, int fetchTimeout,
        int fetchLimit, boolean useHttps, boolean useSelfSignedCerts) {

      this.fetchTimeout = fetchTimeout;

      this.builder = new CambriaClientBuilders.ConsumerBuilder();

      builder.knownAs(consumerGroup, consumerInstance).usingHosts(servers).onTopic(topic)
          .waitAtServer(fetchTimeout).receivingAtMost(fetchLimit);
      
      // Set read timeout to fetch timeout + 30 seconds (TBD: this should be configurable)
      builder.withSocketTimeout(fetchTimeout + 30000);

      if (useHttps) {
          builder.usingHttps();

        if (useSelfSignedCerts) {
          builder.allowSelfSignedCertificates();
        }
      }

      if (apiKey != null && !apiKey.isEmpty() && apiSecret != null && !apiSecret.isEmpty()) {
		  builder.authenticatedBy(apiKey, apiSecret);
      }

      if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
		  builder.authenticatedByHttp(username, password);
      }

      try {
        this.consumer = builder.build();
      } catch (MalformedURLException | GeneralSecurityException e) {
        throw new IllegalArgumentException(e);
      }
    }

    @Override
    public Iterable<String> fetch() throws IOException, InterruptedException {
      try {
        return getCurrentConsumer().fetch();
      } catch (final IOException e) {
        logger.error("{}: cannot fetch because of {} - backoff for {} ms.", this, e.getMessage(),
            this.fetchTimeout);
        synchronized (this.closeCondition) {
          this.closeCondition.wait(this.fetchTimeout);
        }

        throw e;
      }
    }

    @Override
    public void close() {
      synchronized (closeCondition) {
        closeCondition.notifyAll();
      }

      getCurrentConsumer().close();
    }

    private CambriaConsumer getCurrentConsumer() {
        CambriaConsumer old = null;
        CambriaConsumer ret;
        
        synchronized(consLocker) {
            if(this.newConsumer != null) {
                // replace old consumer with new consumer
                old = this.consumer;
                this.consumer = this.newConsumer;
                this.newConsumer = null;
            }
            
            ret = this.consumer;
        }
        
        if(old != null) {
            old.close();
        }
        
        return ret;
    }

    @Override
    public void setFilter(String filter) {
        logger.info("{}: setting DMAAP server-side filter: {}", this, filter);
        builder.withServerSideFilter(filter);

        try {
            CambriaConsumer previous;
            synchronized(consLocker) {
                previous = this.newConsumer;
                this.newConsumer = builder.build();
            }
            
            if(previous != null) {
                // there was already a new consumer - close it
                previous.close();
            }
            
        } catch (MalformedURLException | GeneralSecurityException e) {
            /*
             * Since an exception occurred, "consumer" still has its old value,
             * thus it should not be closed at this point.
             */
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public String toString() {
      return "CambriaConsumerWrapper [fetchTimeout=" + fetchTimeout + "]";
    }
  }

  /**
   * MR based consumer
   */
  public abstract class DmaapConsumerWrapper implements BusConsumer {

    /**
     * logger
     */
    private static Logger logger = LoggerFactory.getLogger(DmaapConsumerWrapper.class);

    /**
     * Name of the "protocol" property.
     */
    protected static final String PROTOCOL_PROP = "Protocol";

    /**
     * fetch timeout
     */
    protected int fetchTimeout;

    /**
     * close condition
     */
    protected Object closeCondition = new Object();

    /**
     * MR Consumer
     */
    protected MRConsumerImpl consumer;

    /**
     * MR Consumer Wrapper
     *
     * @param servers messaging bus hosts
     * @param topic topic
     * @param apiKey API Key
     * @param apiSecret API Secret
     * @param username AAF Login
     * @param password AAF Password
     * @param consumerGroup Consumer Group
     * @param consumerInstance Consumer Instance
     * @param fetchTimeout Fetch Timeout
     * @param fetchLimit Fetch Limit
     * @throws MalformedURLException
     */
    public DmaapConsumerWrapper(List<String> servers, String topic, String apiKey, String apiSecret,
        String username, String password, String consumerGroup, String consumerInstance,
        int fetchTimeout, int fetchLimit) throws MalformedURLException {

      this.fetchTimeout = fetchTimeout;

      if (topic == null || topic.isEmpty()) {
        throw new IllegalArgumentException("No topic for DMaaP");
      }

      this.consumer = new MRConsumerImpl(servers, topic, consumerGroup, consumerInstance,
          fetchTimeout, fetchLimit, null, apiKey, apiSecret);

      this.consumer.setUsername(username);
      this.consumer.setPassword(password);
    }

    @Override
    public Iterable<String> fetch() throws InterruptedException, IOException {
      final MRConsumerResponse response = this.consumer.fetchWithReturnConsumerResponse();
      if (response == null) {
        logger.warn("{}: DMaaP NULL response received", this);

        synchronized (closeCondition) {
          closeCondition.wait(fetchTimeout);
        }
        return new ArrayList<>();
      } else {
        logger.debug("DMaaP consumer received {} : {}" + response.getResponseCode(),
            response.getResponseMessage());

        if (response.getResponseCode() == null || !"200".equals(response.getResponseCode())) {

          logger.error("DMaaP consumer received: {} : {}", response.getResponseCode(),
              response.getResponseMessage());

          synchronized (closeCondition) {
            closeCondition.wait(fetchTimeout);
          }

          /* fall through */
        }
      }

      if (response.getActualMessages() == null)
        return new ArrayList<>();
      else
        return response.getActualMessages();
    }

    @Override
    public void close() {
      synchronized (closeCondition) {
        closeCondition.notifyAll();
      }

      this.consumer.close();
    }

    @Override
    public String toString() {
            return "DmaapConsumerWrapper [" + "consumer.getAuthDate()=" + consumer.getAuthDate()
                            + ", consumer.getAuthKey()=" + consumer.getAuthKey() + ", consumer.getHost()="
                            + consumer.getHost() + ", consumer.getProtocolFlag()=" + consumer.getProtocolFlag()
                            + ", consumer.getUsername()=" + consumer.getUsername() + "]";
    }
  }

  /**
   * MR based consumer
   */
  public static class DmaapAafConsumerWrapper extends DmaapConsumerWrapper {

    private static Logger logger = LoggerFactory.getLogger(DmaapAafConsumerWrapper.class);

    private final Properties props;

    /**
     * MR Consumer Wrapper
     *
     * @param servers messaging bus hosts
     * @param topic topic
     * @param apiKey API Key
     * @param apiSecret API Secret
     * @param aafLogin AAF Login
     * @param aafPassword AAF Password
     * @param consumerGroup Consumer Group
     * @param consumerInstance Consumer Instance
     * @param fetchTimeout Fetch Timeout
     * @param fetchLimit Fetch Limit
     * @throws MalformedURLException
     */
    public DmaapAafConsumerWrapper(List<String> servers, String topic, String apiKey,
        String apiSecret, String aafLogin, String aafPassword, String consumerGroup,
        String consumerInstance, int fetchTimeout, int fetchLimit, boolean useHttps)
        throws MalformedURLException {

      super(servers, topic, apiKey, apiSecret, aafLogin, aafPassword, consumerGroup,
          consumerInstance, fetchTimeout, fetchLimit);

      // super constructor sets servers = {""} if empty to avoid errors when using DME2
      if ((servers.size() == 1 && ("".equals(servers.get(0)))) || (servers == null)
          || (servers.isEmpty())) {
        throw new IllegalArgumentException("Must provide at least one host for HTTP AAF");
      }

      this.consumer.setProtocolFlag(ProtocolTypeConstants.AAF_AUTH.getValue());

      props = new Properties();

      if (useHttps) {
        props.setProperty(PROTOCOL_PROP, "https");
        this.consumer.setHost(servers.get(0) + ":3905");

      } else {
        props.setProperty(PROTOCOL_PROP, "http");
        this.consumer.setHost(servers.get(0) + ":3904");
      }

      this.consumer.setProps(props);
      logger.info("{}: CREATION", this);
    }

    @Override
    public String toString() {
      final MRConsumerImpl consumer = this.consumer;

            return "DmaapConsumerWrapper [" + "consumer.getAuthDate()=" + consumer.getAuthDate()
                            + ", consumer.getAuthKey()=" + consumer.getAuthKey() + ", consumer.getHost()="
                            + consumer.getHost() + ", consumer.getProtocolFlag()=" + consumer.getProtocolFlag()
                            + ", consumer.getUsername()=" + consumer.getUsername() + "]";
    }
  }

  public static class DmaapDmeConsumerWrapper extends DmaapConsumerWrapper {

    private static Logger logger = LoggerFactory.getLogger(DmaapDmeConsumerWrapper.class);

    private final Properties props;

    public DmaapDmeConsumerWrapper(List<String> servers, String topic, String apiKey,
        String apiSecret, String dme2Login, String dme2Password, String consumerGroup,
        String consumerInstance, int fetchTimeout, int fetchLimit, String environment,
        String aftEnvironment, String dme2Partner, String latitude, String longitude,
        Map<String, String> additionalProps, boolean useHttps) throws MalformedURLException {



      super(servers, topic, apiKey, apiSecret, dme2Login, dme2Password, consumerGroup,
          consumerInstance, fetchTimeout, fetchLimit);


      final String dme2RouteOffer =
          additionalProps.get(DmaapTopicSinkFactory.DME2_ROUTE_OFFER_PROPERTY);

      if (environment == null || environment.isEmpty()) {
          throw parmException(topic, PolicyProperties.PROPERTY_DMAAP_DME2_ENVIRONMENT_SUFFIX);
      }
      if (aftEnvironment == null || aftEnvironment.isEmpty()) {
          throw parmException(topic, PolicyProperties.PROPERTY_DMAAP_DME2_AFT_ENVIRONMENT_SUFFIX);
      }
      if (latitude == null || latitude.isEmpty()) {
        throw parmException(topic, PolicyProperties.PROPERTY_DMAAP_DME2_LATITUDE_SUFFIX);
      }
      if (longitude == null || longitude.isEmpty()) {
    	throw parmException(topic, PolicyProperties.PROPERTY_DMAAP_DME2_LONGITUDE_SUFFIX);
      }

      if ((dme2Partner == null || dme2Partner.isEmpty())
          && (dme2RouteOffer == null || dme2RouteOffer.isEmpty())) {
        throw new IllegalArgumentException(
            "Must provide at least " + PolicyProperties.PROPERTY_DMAAP_SOURCE_TOPICS + "." + topic
                + PolicyProperties.PROPERTY_DMAAP_DME2_PARTNER_SUFFIX + " or "
                + PolicyProperties.PROPERTY_DMAAP_SOURCE_TOPICS + "." + topic
                + PolicyProperties.PROPERTY_DMAAP_DME2_ROUTE_OFFER_SUFFIX + " for DME2");
      }

      final String serviceName = servers.get(0);

      this.consumer.setProtocolFlag(ProtocolTypeConstants.DME2.getValue());

      this.consumer.setUsername(dme2Login);
      this.consumer.setPassword(dme2Password);

      props = new Properties();

      props.setProperty(DmaapTopicSinkFactory.DME2_SERVICE_NAME_PROPERTY, serviceName);

      props.setProperty("username", dme2Login);
      props.setProperty("password", dme2Password);

      /* These are required, no defaults */
      props.setProperty("topic", topic);

      props.setProperty("Environment", environment);
      props.setProperty("AFT_ENVIRONMENT", aftEnvironment);

      if (dme2Partner != null)
        props.setProperty("Partner", dme2Partner);
      if (dme2RouteOffer != null)
        props.setProperty(DmaapTopicSinkFactory.DME2_ROUTE_OFFER_PROPERTY, dme2RouteOffer);

      props.setProperty("Latitude", latitude);
      props.setProperty("Longitude", longitude);

      /* These are optional, will default to these values if not set in additionalProps */
      props.setProperty("AFT_DME2_EP_READ_TIMEOUT_MS", "50000");
      props.setProperty("AFT_DME2_ROUNDTRIP_TIMEOUT_MS", "240000");
      props.setProperty("AFT_DME2_EP_CONN_TIMEOUT", "15000");
      props.setProperty("Version", "1.0");
      props.setProperty("SubContextPath", "/");
      props.setProperty("sessionstickinessrequired", "no");

      /* These should not change */
      props.setProperty("TransportType", "DME2");
      props.setProperty("MethodType", "GET");

      if (useHttps) {
        props.setProperty(PROTOCOL_PROP, "https");

      } else {
        props.setProperty(PROTOCOL_PROP, "http");
      }

      props.setProperty("contenttype", "application/json");

      if (additionalProps != null) {
        for (Map.Entry<String, String> entry : additionalProps.entrySet())
              props.put(entry.getKey(), entry.getValue());
      }

      MRClientFactory.prop = props;
      this.consumer.setProps(props);

      logger.info("{}: CREATION", this);
    }
    
    private IllegalArgumentException parmException(String topic, String propnm) {
        return new IllegalArgumentException(
                "Missing " + PolicyProperties.PROPERTY_DMAAP_SOURCE_TOPICS + "." + topic
                    + propnm + " property for DME2 in DMaaP");
    	
    }
  }
}


