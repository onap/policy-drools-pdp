/*
 * ============LICENSE_START=======================================================
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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.kie.api.runtime.rule.FactHandle;
import org.onap.policy.drools.core.PolicySession;
import org.onap.policy.drools.event.comm.Topic.CommInfrastructure;
import org.onap.policy.drools.event.comm.TopicSink;
import org.onap.policy.drools.event.comm.TopicSource;
import org.onap.policy.drools.pooling.message.BucketAssignments;
import org.onap.policy.drools.pooling.message.Forward;
import org.onap.policy.drools.pooling.message.Message;
import org.onap.policy.drools.pooling.state.StartState;
import org.onap.policy.drools.pooling.state.State;
import org.onap.policy.drools.system.PolicyController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

// TODO re-comment
// TODO comment: messages read by the controller are held in the rule engine
// until a bucket assignment has been made

/*
 * TODO while locked, should still forward messages. However, stuff destined
 * for this rule engine should be discarded (same behavior as
 * PolicyController.offer()).
 */

public class PoolingManagerImpl implements PoolingManager {

	private static final Logger logger = LoggerFactory.getLogger(PoolingManagerImpl.class);

	// TODO remove constants

	/**
	 * ID of this host.
	 */
	private final String host;

	/**
	 * Associated controller.
	 */
	private PolicyController controller;

	// TODO populate the below

	/**
	 * Maps a session name to the session.
	 */
	private final Map<String, PolicySession> name2sess = new HashMap<>(107);

	/**
	 * Used to encode & decode request objects received from & sent to a rule
	 * engine.
	 */
	private final Serializer serializer;

	/**
	 * Internal DMaaP topic used by this controller.
	 */
	private final String topic;

	/**
	 * Topic source whose filter is to be manipulated. Initialized by
	 * {@link #afterStart()}.
	 */
	private TopicSource topicSource;

	/**
	 * Type used when delivering to the internal topic. Initialized by
	 * {@link #afterStart()}.
	 */
	private CommInfrastructure busType;

	// TODO if feature is disabled, then insert a RequestDistributor WME that
	// always returns false

	/**
	 * WME to be inserted into and retracted from the rule engine(s).
	 */
	private final ReqDist distributor = new ReqDist();

	/**
	 * Lock used while updating {@link #current}. Signaled, whenever the state
	 * object indicates that processing can begin, by invoking
	 * {@link #enableProcessing(boolean)}.
	 */
	private final Object curLocker = new Object();

	/**
	 * Current state, or {@code null} if not currently running.
	 * <p/>
	 * This uses a finite state machine, wherein the state object contains all
	 * of the data relevant to that state. Each state object has a process()
	 * method, specific to each type of {@link Message} subclass. The method
	 * returns the next state object, or {@code null} if the state is to remain
	 * the same.
	 */
	private State current = null;

	/**
	 * Current bucket assignments, or {@code null} if the host is not currently
	 * assigned any buckets.
	 */
	private BucketAssignments assignments = null;

	public PoolingManagerImpl(TypedProperties props) {
		this.host = UUID.randomUUID().toString();

		try {
			this.serializer = new Serializer();
			this.topic = props.getStrProperty(PoolingProperties.PROP_POOLING_TOPIC);

			logger.info("allocating host {} to controller {} for topic {}", host, controller.getName(), topic);

		} catch (PoolingFeatureException e) {
			logger.error("failed to attach internal DMaaP topic to controller {}", controller.getName());
			throw e.toRuntimeException();

		} catch (IOException e) {
			logger.error("failed to attach serializer to controller {}", controller.getName());
			throw new PoolingFeatureRtException(e);
		}
	}

	protected void setController(PolicyController controller) {
		this.controller = controller;
	}

	@Override
	public void publishAdmin(Message msg) {
		publish(Message.ADMIN, msg);
	}

	@Override
	public void publish(String channel, Message msg) {
		msg.setChannel(channel);

		try {
			String txt = serializer.encodeMsg(msg);
			controller.deliver(busType, topic, txt);

		} catch (JsonProcessingException e) {
			logger.error("failed to serialize message for topic {} channel {}", topic, channel, e);

		} catch (RuntimeException e) {
			logger.error("failed to publish message for topic {} channel {}", topic, channel, e);
		}
	}

	private void changeState(State newState) {
		if (newState != null) {
			current = newState;

			// TODO topicSource.setFilter(current.getFilter());
		}
	}

	public boolean beforeOffer(String topic2, String event) {
		if (!topic2.equals(topic)) {
			return false;
		}

		synchronized (curLocker) {
			if (current == null) {
				// not ready for it yet - just discard it
				return true;
			}

			Class<?> clazz = null;

			try {
				Message msg = serializer.decodeMsg(event);
				clazz = msg.getClass();

				msg.checkValidity();
				Method meth = current.getClass().getMethod("process", msg.getClass());
				changeState((State) meth.invoke(current, msg));

			} catch (IOException e) {
				logger.warn("failed to decode message for topic {}", topic, e);

			} catch (NoSuchMethodException | SecurityException e) {
				logger.error("no processor for {} for topic {}", topic, clazz, e);

			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				logger.error("failed to process message {} for topic {}", clazz, topic, e);

			} catch (PoolingFeatureException e) {
				logger.error("failed to process message {} for topic {}", clazz, topic, e);
			}
		}

		// we've handled the event
		return true;
	}

	private void insertDistributor() {
		for (PolicySession sess : name2sess.values()) {
			try {
				sess.getKieSession().insert(distributor);

			} catch (RuntimeException e) {
				logger.error("failed to insert distributor into session {}", sess.getName(), e);
			}
		}
	}

	private void retractDistributor() {
		if (assignments != null) {
			for (PolicySession sess : name2sess.values()) {
				retractDistributor(sess);
			}

			assignments = null;
		}
	}

	@Override
	public void stopDistributing() {
		synchronized (curLocker) {
			retractDistributor();
		}
	}

	private void retractDistributor(PolicySession sess) {
		FactHandle fh = sess.getKieSession().getFactHandle(distributor);

		if (fh != null) {
			try {
				sess.getKieSession().delete(fh);

			} catch (RuntimeException e) {
				logger.error("failed to retract distributor from session {}", sess.getName(), e);
			}
		}
	}

	@Override
	public void startDistributing(BucketAssignments assignments) {
		synchronized (curLocker) {
			boolean wasInserted = (this.assignments != null);
			this.assignments = assignments;

			if (!wasInserted) {
				insertDistributor();
			}
		}
	}

	public void afterStart() throws PoolingFeatureException {
		synchronized (curLocker) {
			if (current == null) {
				initTopicSource();
				initTopicSink();
				current = new StartState(this);
			}
		}
	}

	private void initTopicSource() throws PoolingFeatureException {
		for (TopicSource src : controller.getTopicSources()) {
			if (topic.equals(src.getTopic())) {
				topicSource = src;
				return;
			}
		}

		throw new PoolingFeatureException("controller " + controller.getName() + " is missing topic source " + topic);
	}

	private void initTopicSink() throws PoolingFeatureException {
		for (TopicSink sink : controller.getTopicSinks()) {
			if (topic.equals(sink.getTopic())) {
				busType = sink.getTopicCommInfrastructure();
				return;
			}
		}

		throw new PoolingFeatureException("controller " + controller.getName() + " is missing topic sink " + topic);
	}

	public void beforeStop() {
		synchronized (curLocker) {
			if (current != null) {
				current.stop();
				current = null;

				retractDistributor();
			}
		}
	}

	public String getHost() {
		return host;
	}

	@Override
	public void inject(String sessName, String encodedReq) {
		if (sessName == null || encodedReq == null) {
			logger.error("ignored null request for session {} topic {}", sessName, topic);
			return;
		}

		synchronized (curLocker) {
			if (controller.isLocked()) {
				return;
			}

			Object req = serializer.decodeReq(encodedReq);
			if (req == null) {
				logger.error("failed to deserialize request for session {} topic {}", sessName, topic);
				return;
			}

			PolicySession sess = name2sess.get(sessName);
			if (sess == null) {
				logger.error("no session {} for topic {}", sessName, topic);
				return;
			}

			try {
				sess.getKieSession().insert(req);

			} catch (RuntimeException e) {
				logger.error("failed to inject request into session {} for topic {}", sessName, topic, e);
			}
		}
	}

	private class ReqDist implements RequestDistributor {

		@Override
		public boolean handle(String sessName, String reqid, Object req) throws IOException {
			if (!validArgs(sessName, reqid, req)) {
				return false;
			}

			synchronized (curLocker) {
				if (assignments == null) {
					logger.error("no buckets assigned for session {} topic {}", sessName, topic);

					// consume the message so it doesn't get inserted into the
					// engine
					return true;
				}

				int hc = sessName.hashCode() * 37 + reqid.hashCode();
				int bucket = Math.abs(hc) % assignments.size();

				String me = getHost();
				String target = assignments.getAssignedHost(bucket);

				if (target.equals(me)) {
					// message belongs to this host - allow it to flow through
					return false;
				}

				// forward to a different host
				String data = serializer.encodeReq(req);
				publish(target, new Forward(me, bucket, sessName, data));

				// we've consumed the message
				return true;
			}
		}

		private boolean validArgs(String sessName, String reqid, Object req) {
			if (sessName == null || sessName.isEmpty()) {
				throw new IllegalArgumentException("missing sessName");
			}

			if (req == null) {
				throw new IllegalArgumentException("missing request object");
			}

			if (reqid == null) {
				throw new IllegalArgumentException("missing reqid");
			}

			if (reqid.isEmpty()) {
				logger.warn("session {} missing request id for topic {}", sessName, topic);
				return false;
			}

			return true;
		}
	}
}
